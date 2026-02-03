# Research: App Prefix Notifications

## 1. Explicit Broadcasts with setPackage()

### Decision: Use explicit broadcasts for cross-app notification

**Rationale**:
- `setPackage()` targets a specific app - secure and efficient
- Required for Android 8+ (implicit broadcasts to manifest receivers are blocked)
- Broker doesn't need to know if client is running - just sends broadcast

**Implementation Notes**:
```kotlin
// In broker service after persisting message
fun broadcastNewMessage(context: Context, prefix: String, packageName: String) {
    val intent = Intent(ACTION_NEW_MESSAGE).apply {
        setPackage(packageName)  // Explicit - only this app receives
        putExtra(EXTRA_PREFIX, prefix)
    }
    context.sendBroadcast(intent)
}

// For testing (broadcast to self)
broadcastNewMessage(context, "broker", context.packageName)
```

**Broadcast Action**: `com.example.udpservice.NEW_MESSAGE`
**Extras**: `prefix` (String) - which prefix has new messages

**Alternatives Considered**:
- Implicit broadcast: Rejected - blocked on Android 8+ for manifest receivers
- Ordered broadcast: Not needed - just a ping, no response required
- LocalBroadcastManager: Deprecated, and doesn't work cross-app

## 2. BroadcastReceiver Lifecycle

### Decision: Manifest-declared receiver with WorkManager for background work

**Rationale**:
- Manifest receiver works even when app is not running
- BroadcastReceiver.onReceive() has ~10 second limit
- WorkManager handles longer work and survives process death

**Implementation Notes**:

AndroidManifest.xml:
```xml
<receiver
    android:name=".receiver.MessageReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.example.udpservice.NEW_MESSAGE" />
    </intent-filter>
</receiver>
```

Receiver:
```kotlin
class MessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefix = intent.getStringExtra(EXTRA_PREFIX) ?: return

        // Quick check: is UI actively showing this prefix?
        if (isUiActiveForPrefix(prefix)) {
            // UI will handle it via database observation
            return
        }

        // Background: enqueue work
        val workRequest = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf("prefix" to prefix))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
```

**Alternatives Considered**:
- Context-registered receiver: Wouldn't work when app is killed
- JobIntentService: Deprecated in favor of WorkManager
- Foreground service for each notification: Overkill

## 3. WorkManager for Background Notifications

### Decision: Use expedited OneTimeWorkRequest for notification processing

**Rationale**:
- Expedited work runs as soon as possible (within system constraints)
- Handles Android's background restrictions gracefully
- Survives process death; will retry if killed

**Implementation Notes**:
```kotlin
class MessageNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefix = inputData.getString("prefix") ?: return Result.failure()

        // Pull unread count from database
        val unreadCount = packetDao.getUnreadCount(prefix)
        if (unreadCount == 0) {
            notificationHelper.dismiss(prefix)
            return Result.success()
        }

        // Get registration for deep link URI
        val registration = registrationRepository.getRegistration(prefix)
            ?: return Result.failure()

        // Show/update notification
        notificationHelper.show(
            prefix = prefix,
            count = unreadCount,
            deepLinkUri = registration.deepLinkUri
        )

        return Result.success()
    }
}
```

**Expedited Work**:
- `setExpedited()` with `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`
- Falls back to normal priority if quota exceeded (rare)

## 4. Android Notification Best Practices

### Decision: Per-prefix notification channels with NotificationCompat

**Rationale**:
- Channels required on API 26+ (Android 8)
- Per-prefix allows user to control each prefix independently
- NotificationCompat for backward compatibility

**Implementation Notes**:
```kotlin
object NotificationChannels {
    fun createChannelForPrefix(context: Context, prefix: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "messages_$prefix",
                "Messages: $prefix",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for $prefix messages"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

fun showNotification(context: Context, prefix: String, count: Int, deepLinkUri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri))
    val pendingIntent = PendingIntent.getActivity(
        context, prefix.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "messages_$prefix")
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(prefix)
        .setContentText("$count new message${if (count > 1) "s" else ""}")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)  // Don't re-alert on update
        .build()

    NotificationManagerCompat.from(context)
        .notify(prefix.hashCode(), notification)
}
```

## 5. Deep Link Handling in Jetpack Compose Navigation

### Decision: NavDeepLink with custom URI scheme

**Rationale**:
- Compose Navigation has built-in deep link support
- Custom scheme avoids conflicts
- URI pattern: `udptest://messages/{prefix}`

**Implementation Notes**:

AndroidManifest.xml:
```xml
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="udptest" android:host="messages" />
    </intent-filter>
</activity>
```

Navigation:
```kotlin
composable(
    route = "messages/{prefix}",
    deepLinks = listOf(navDeepLink { uriPattern = "udptest://messages/{prefix}" }),
    arguments = listOf(navArgument("prefix") { type = NavType.StringType })
) { backStackEntry ->
    val prefix = backStackEntry.arguments?.getString("prefix") ?: ""
    MessagesScreen(prefix = prefix, onMessagesViewed = { markAsRead(prefix) })
}
```

## 6. Room Database Migration

### Decision: Migration v1 → v2 with isRead field and registration table

**Schema Changes**:
1. Add `isRead` column to packets table
2. Create `app_registrations` table

**Migration SQL**:
```sql
-- Add isRead column
ALTER TABLE packets ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS index_packets_appId_isRead ON packets(appId, isRead);

-- Create registrations table
CREATE TABLE IF NOT EXISTS app_registrations (
    prefix TEXT NOT NULL PRIMARY KEY,
    packageName TEXT NOT NULL,
    notificationsEnabled INTEGER NOT NULL,
    deepLinkUri TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
```

## 7. Foreground Detection (Simplified)

### Decision: Check if MessagesScreen is active for immediate UI update

**Rationale**:
- BroadcastReceiver needs to decide: immediate UI update or WorkManager
- Simple approach: check if relevant Activity/Screen is showing
- If yes: skip WorkManager, database Flow will update UI
- If no: enqueue WorkManager for notification

**Implementation Notes**:
```kotlin
// In Application or singleton
object AppState {
    var activePrefix: String? = null  // Set by MessagesScreen
}

// In BroadcastReceiver
if (AppState.activePrefix == prefix) {
    // UI is showing this prefix, database Flow will handle update
    return
}
// Otherwise, enqueue WorkManager
```

**Why not ProcessLifecycleOwner**:
- More complex than needed
- We only care if the specific messages screen is visible
- Simple singleton approach is sufficient for same-app testing
