# Quickstart: App Prefix Notifications

## Prerequisites

- Android Studio (Hedgehog or later)
- Android device or emulator (API 29+)
- Python 3.10+ with `uv` (for UDP sender tool)

## Development Setup

### 1. Clone and Build

```bash
# Ensure you're on the feature branch
git checkout 006-app-notifications

# Build the project
./gradlew assembleDebug

# Run unit tests
./gradlew :udp-service:test :app:test
```

### 2. Run on Device/Emulator

```bash
# Install and launch
./gradlew :app:installDebug
adb shell am start -n com.example.udpbroker/.MainActivity
```

### 3. Test Notifications

```bash
# Get device IP (shown in app UI)
DEVICE_IP="<your-device-ip>"

# Send message to "broker" prefix
cd tools/udp-sender
uv run udp-sender send -h $DEVICE_IP -p 5000 -a "broker" -m "Hello broker!"

# Send message to "alerts" prefix
uv run udp-sender send -h $DEVICE_IP -p 5000 -a "alerts" -m "Alert message!"
```

## Architecture Overview

```
UDP Message → Broker Service → Persist → Broadcast → Client Receiver → WorkManager → Notification
                                              ↓
                                     (or if foreground)
                                              ↓
                                     Direct UI Update
```

## Testing Strategy

### Stage 1: Unit Tests (JVM)

```bash
./gradlew :udp-service:test :app:test
```

**What to test:**
- `MessageBroadcaster` - sends correct intent with setPackage
- `RegistrationRepository` - CRUD operations
- `NotificationHelper` - builds correct notification
- `MessageNotificationWorker` - correct flow logic

### Stage 2: Emulator Tests

```bash
./gradlew :app:connectedAndroidTest :udp-service:connectedAndroidTest
```

**What to test:**
- Database migration (v1 → v2)
- BroadcastReceiver receives intent
- Deep link navigation
- Notification channel creation

### Stage 3: Device Tests (Manual)

1. **Broadcast Flow**:
   - Set breakpoint in `MessageReceiver.onReceive()`
   - Send UDP message
   - Verify intent has correct action and prefix extra

2. **Background Notification**:
   - Start service, press Home to background app
   - Send UDP message
   - Verify notification appears with correct count
   - Tap notification, verify deep link navigation

3. **Foreground Update**:
   - Open app to "broker" messages screen
   - Send UDP message to "broker"
   - Verify UI updates immediately (no notification)

4. **Dual Prefix**:
   - Background app
   - Send messages to both "broker" and "alerts"
   - Verify separate notifications for each

## Key Commands Reference

```bash
# Build
./gradlew assembleDebug

# Test
./gradlew test                        # All unit tests
./gradlew connectedAndroidTest        # All instrumented tests

# Install
./gradlew :app:installDebug

# Logs
adb logcat -s UdpService MessageReceiver MessageNotificationWorker

# Test broadcast manually
adb shell am broadcast \
  -a com.example.udpservice.NEW_MESSAGE \
  -p com.example.udpbroker \
  --es prefix "broker"

# Test deep link manually
adb shell am start -a android.intent.action.VIEW \
  -d "udptest://messages/broker" \
  com.example.udpbroker

# Send test messages
cd tools/udp-sender
uv run udp-sender send -h <IP> -p 5000 -a "broker" -m "message"
uv run udp-sender send -h <IP> -p 5000 -a "alerts" -m "alert!"
```

## Debugging Tips

### Broadcast Not Received

1. Check manifest declares receiver with correct action
2. Verify `setPackage()` uses correct package name
3. Check logcat for broadcast errors: `adb logcat -s BroadcastQueue`
4. Test manually with adb shell am broadcast

### Notification Not Appearing

1. Check notification permission granted
2. Check notification channel exists and not disabled
3. Verify WorkManager is running: `adb shell dumpsys jobscheduler | grep udpbroker`
4. Check logcat for NotificationHelper errors

### Deep Link Not Working

1. Verify manifest intent-filter for `udptest://` scheme
2. Test manually: `adb shell am start -d "udptest://messages/broker"`
3. Check NavController handles the deep link route

### WorkManager Not Running

1. Check expedited work quota: `adb shell dumpsys jobscheduler`
2. Verify WorkManager initialized in Application
3. Check for exceptions in Worker: `adb logcat -s WM-WorkerWrapper`

## File Locations

| Component | Path |
|-----------|------|
| MessageBroadcaster | `udp-service/src/.../broadcast/` |
| Registration | `udp-service/src/.../registration/` |
| PacketEntity (modified) | `udp-service/src/.../persistence/PacketEntity.kt` |
| BroadcastReceiver | `app/src/.../receiver/MessageReceiver.kt` |
| WorkManager Worker | `app/src/.../worker/MessageNotificationWorker.kt` |
| NotificationHelper | `app/src/.../notification/` |
| Deep Link Navigation | `app/src/.../ui/navigation/` |
| MessagesScreen | `app/src/.../ui/MessagesScreen.kt` |
| AndroidManifest | `app/src/main/AndroidManifest.xml` |
