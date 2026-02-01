# Android UDP Service Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a local-first Android UDP communication library and standalone broker that enables apps to send/receive messages over local network without cloud dependency.

**Architecture:** Multi-module Gradle project with a reusable library (`:udp-service`) and standalone app (`:app`). The library provides a foreground service with coroutine-based UDP socket handling, Room database for message buffering, and ContentProvider for cross-app IPC. Messages use topic-based routing with a simple header format.

**Tech Stack:** Kotlin, Coroutines/Flow, Android Foreground Service, Room, ContentProvider, Jetpack Compose (UI), Python/uv (test tooling)

---

## Phase 1: Project Scaffolding

### Task 1: Initialize Gradle Multi-Module Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `udp-service/build.gradle.kts`
- Create: `app/build.gradle.kts`

**Step 1: Create root settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-udp-service"
include(":udp-service")
include(":app")
```

**Step 2: Create root build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
```

**Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 4: Create udp-service/build.gradle.kts**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.udpservice"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

**Step 5: Create app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.udpbroker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.udpbroker"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":udp-service"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

**Step 6: Verify build compiles**

Run: `./gradlew projects`
Expected: Shows `:udp-service` and `:app` modules

**Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties udp-service/build.gradle.kts app/build.gradle.kts
git commit -m "chore: initialize gradle multi-module project structure"
```

---

### Task 2: Create Library Module Skeleton

**Files:**
- Create: `udp-service/src/main/AndroidManifest.xml`
- Create: `udp-service/src/main/kotlin/com/example/udpservice/UdpBrokerService.kt`
- Create: `udp-service/consumer-rules.pro`
- Create: `udp-service/proguard-rules.pro`

**Step 1: Create library AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name=".UdpBrokerService"
            android:exported="true"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Local network UDP communication for local-first apps" />
        </service>
    </application>

</manifest>
```

**Step 2: Create minimal UdpBrokerService.kt**

```kotlin
package com.example.udpservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class UdpBrokerService : Service() {

    companion object {
        const val CHANNEL_ID = "udp_broker_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Will implement binder later
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "UDP Broker Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when UDP broker service is running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("UDP Broker")
            .setContentText("Service running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
```

**Step 3: Create empty proguard files**

Create `udp-service/consumer-rules.pro`:
```
# Consumer proguard rules for udp-service library
```

Create `udp-service/proguard-rules.pro`:
```
# Proguard rules for udp-service library
```

**Step 4: Verify library builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add udp-service/
git commit -m "feat: add library module skeleton with foreground service"
```

---

### Task 3: Create App Module Skeleton

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/example/udpbroker/MainActivity.kt`
- Create: `app/src/main/kotlin/com/example/udpbroker/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/com/example/udpbroker/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/example/udpbroker/ui/theme/Type.kt`
- Create: `app/src/main/res/values/strings.xml`

**Step 1: Create app AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.UdpBroker">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.UdpBroker">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

**Step 2: Create MainActivity.kt**

```kotlin
package com.example.udpbroker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.udpbroker.ui.theme.UdpBrokerTheme
import com.example.udpservice.UdpBrokerService

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            UdpBrokerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrokerStatusScreen(
                        onStartService = { startBrokerService() },
                        onStopService = { stopBrokerService() }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startBrokerService() {
        val intent = Intent(this, UdpBrokerService::class.java)
        startForegroundService(intent)
    }

    private fun stopBrokerService() {
        val intent = Intent(this, UdpBrokerService::class.java)
        stopService(intent)
    }
}

@Composable
fun BrokerStatusScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "UDP Broker",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRunning) "Service Running" else "Service Stopped",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (isRunning) {
                    onStopService()
                } else {
                    onStartService()
                }
                isRunning = !isRunning
            }
        ) {
            Text(if (isRunning) "Stop Service" else "Start Service")
        }
    }
}
```

**Step 3: Create theme files**

Create `app/src/main/kotlin/com/example/udpbroker/ui/theme/Color.kt`:
```kotlin
package com.example.udpbroker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

Create `app/src/main/kotlin/com/example/udpbroker/ui/theme/Type.kt`:
```kotlin
package com.example.udpbroker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

Create `app/src/main/kotlin/com/example/udpbroker/ui/theme/Theme.kt`:
```kotlin
package com.example.udpbroker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun UdpBrokerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

**Step 4: Create strings.xml**

Create `app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">UDP Broker</string>
</resources>
```

**Step 5: Create themes.xml**

Create `app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.UdpBroker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

**Step 6: Verify app builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/
git commit -m "feat: add app module with basic Compose UI"
```

---

## Phase 2: Core UDP Socket Implementation

### Task 4: Create UDP Datagram Data Classes

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/api/UdpDatagram.kt`
- Create: `udp-service/src/test/kotlin/com/example/udpservice/api/UdpDatagramTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.example.udpservice.api

import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class UdpDatagramTest {

    @Test
    fun `parse datagram extracts topic and payload`() {
        // Format: [2-byte topic length][topic][payload]
        val topicBytes = "weather".toByteArray()
        val payloadBytes = "temp=22".toByteArray()
        val rawData = ByteArray(2 + topicBytes.size + payloadBytes.size)
        rawData[0] = 0
        rawData[1] = topicBytes.size.toByte()
        System.arraycopy(topicBytes, 0, rawData, 2, topicBytes.size)
        System.arraycopy(payloadBytes, 0, rawData, 2 + topicBytes.size, payloadBytes.size)

        val datagram = UdpDatagram.parse(
            data = rawData,
            source = InetSocketAddress("192.168.1.100", 12345)
        )

        assertEquals("weather", datagram.topic)
        assertArrayEquals(payloadBytes, datagram.payload)
        assertEquals("192.168.1.100", datagram.sourceAddress.address.hostAddress)
        assertEquals(12345, datagram.sourceAddress.port)
    }

    @Test
    fun `serialize datagram creates correct wire format`() {
        val datagram = UdpDatagram(
            topic = "test",
            payload = "hello".toByteArray(),
            sourceAddress = InetSocketAddress("0.0.0.0", 0)
        )

        val bytes = datagram.toBytes()

        assertEquals(0, bytes[0].toInt())
        assertEquals(4, bytes[1].toInt()) // "test" length
        assertEquals("test", String(bytes, 2, 4))
        assertEquals("hello", String(bytes, 6, 5))
    }

    @Test
    fun `empty topic handled correctly`() {
        val rawData = byteArrayOf(0, 0) + "payload".toByteArray()

        val datagram = UdpDatagram.parse(
            data = rawData,
            source = InetSocketAddress("127.0.0.1", 5000)
        )

        assertEquals("", datagram.topic)
        assertEquals("payload", String(datagram.payload))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.api.UdpDatagramTest"`
Expected: FAIL - class UdpDatagram does not exist

**Step 3: Write minimal implementation**

```kotlin
package com.example.udpservice.api

import java.net.InetSocketAddress

data class UdpDatagram(
    val topic: String,
    val payload: ByteArray,
    val sourceAddress: InetSocketAddress,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun parse(data: ByteArray, source: InetSocketAddress): UdpDatagram {
            require(data.size >= 2) { "Data too short for header" }

            val topicLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            require(data.size >= 2 + topicLength) { "Data too short for topic" }

            val topic = if (topicLength > 0) {
                String(data, 2, topicLength, Charsets.UTF_8)
            } else {
                ""
            }

            val payloadStart = 2 + topicLength
            val payload = data.copyOfRange(payloadStart, data.size)

            return UdpDatagram(
                topic = topic,
                payload = payload,
                sourceAddress = source
            )
        }
    }

    fun toBytes(): ByteArray {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        val result = ByteArray(2 + topicBytes.size + payload.size)

        result[0] = ((topicBytes.size shr 8) and 0xFF).toByte()
        result[1] = (topicBytes.size and 0xFF).toByte()

        System.arraycopy(topicBytes, 0, result, 2, topicBytes.size)
        System.arraycopy(payload, 0, result, 2 + topicBytes.size, payload.size)

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpDatagram) return false
        return topic == other.topic &&
               payload.contentEquals(other.payload) &&
               sourceAddress == other.sourceAddress
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + sourceAddress.hashCode()
        return result
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.api.UdpDatagramTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add udp-service/src/
git commit -m "feat: add UdpDatagram data class with wire format parsing"
```

---

### Task 5: Create Coroutine-Based UDP Socket Wrapper

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt`
- Create: `udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.example.udpservice

import com.example.udpservice.api.UdpDatagram
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpSocketTest {

    @Test
    fun `socket receives datagram and emits to flow`() = runTest {
        val udpSocket = UdpSocket(port = 0) // Bind to random available port
        udpSocket.start()
        val boundPort = udpSocket.localPort

        // Send a test packet from another socket
        launch {
            val sender = DatagramSocket()
            val topic = "test"
            val payload = "hello"
            val data = UdpDatagram(topic, payload.toByteArray(), InetSocketAddress("0.0.0.0", 0)).toBytes()
            val packet = DatagramPacket(data, data.size, InetSocketAddress("127.0.0.1", boundPort))
            sender.send(packet)
            sender.close()
        }

        // Receive via flow
        val received = withTimeout(1000) {
            udpSocket.incoming.first()
        }

        assertEquals("test", received.topic)
        assertEquals("hello", String(received.payload))

        udpSocket.stop()
    }

    @Test
    fun `socket sends datagram to destination`() = runTest {
        // Set up receiver
        val receiver = DatagramSocket(0)
        val receiverPort = receiver.localPort
        receiver.soTimeout = 1000

        val udpSocket = UdpSocket(port = 0)
        udpSocket.start()

        // Send via our socket
        val datagram = UdpDatagram(
            topic = "outbound",
            payload = "world".toByteArray(),
            sourceAddress = InetSocketAddress("0.0.0.0", 0)
        )
        udpSocket.send(datagram, InetSocketAddress("127.0.0.1", receiverPort))

        // Receive on plain socket
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        receiver.receive(packet)

        val receivedData = packet.data.copyOf(packet.length)
        val parsed = UdpDatagram.parse(receivedData, InetSocketAddress(packet.address, packet.port))

        assertEquals("outbound", parsed.topic)
        assertEquals("world", String(parsed.payload))

        udpSocket.stop()
        receiver.close()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.UdpSocketTest"`
Expected: FAIL - class UdpSocket does not exist

**Step 3: Write minimal implementation**

```kotlin
package com.example.udpservice

import com.example.udpservice.api.UdpDatagram
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpSocket(
    private val port: Int,
    private val bufferSize: Int = 65535
) {
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incoming = MutableSharedFlow<UdpDatagram>(extraBufferCapacity = 64)
    val incoming: SharedFlow<UdpDatagram> = _incoming.asSharedFlow()

    val localPort: Int
        get() = socket?.localPort ?: -1

    fun start() {
        if (socket != null) return

        socket = DatagramSocket(port)
        receiveJob = scope.launch {
            receiveLoop()
        }
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        socket?.close()
        socket = null
    }

    suspend fun send(datagram: UdpDatagram, destination: InetSocketAddress) {
        withContext(Dispatchers.IO) {
            val data = datagram.toBytes()
            val packet = DatagramPacket(data, data.size, destination)
            socket?.send(packet)
        }
    }

    private suspend fun receiveLoop() {
        val buffer = ByteArray(bufferSize)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive) {
            try {
                withContext(Dispatchers.IO) {
                    socket?.receive(packet)
                }

                val data = packet.data.copyOf(packet.length)
                val source = InetSocketAddress(packet.address, packet.port)

                try {
                    val datagram = UdpDatagram.parse(data, source)
                    _incoming.emit(datagram)
                } catch (e: Exception) {
                    // Skip malformed packets
                }
            } catch (e: Exception) {
                if (isActive) {
                    // Log error but continue
                }
            }
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.UdpSocketTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add udp-service/src/
git commit -m "feat: add coroutine-based UdpSocket with Flow emission"
```

---

## Phase 3: Room Database for Message Buffering

### Task 6: Create Room Database Entities

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/data/MessageEntity.kt`
- Create: `udp-service/src/main/kotlin/com/example/udpservice/data/SubscriptionEntity.kt`

**Step 1: Create MessageEntity**

```kotlin
package com.example.udpservice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("topic"),
        Index("timestamp"),
        Index("delivered")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String,
    val payload: ByteArray,
    val sourceAddress: String,
    val sourcePort: Int,
    val timestamp: Long,
    val delivered: Boolean = false,
    val deliveredToPackage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return id == other.id &&
               topic == other.topic &&
               payload.contentEquals(other.payload) &&
               sourceAddress == other.sourceAddress &&
               sourcePort == other.sourcePort
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + sourceAddress.hashCode()
        result = 31 * result + sourcePort
        return result
    }
}
```

**Step 2: Create SubscriptionEntity**

```kotlin
package com.example.udpservice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [Index("packageName", unique = true)]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val topics: String, // Comma-separated list of topics
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)
```

**Step 3: Verify builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add udp-service/src/main/kotlin/com/example/udpservice/data/
git commit -m "feat: add Room entities for messages and subscriptions"
```

---

### Task 7: Create Room DAOs

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/data/MessageDao.kt`
- Create: `udp-service/src/main/kotlin/com/example/udpservice/data/SubscriptionDao.kt`

**Step 1: Create MessageDao**

```kotlin
package com.example.udpservice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE topic = :topic AND delivered = 0 ORDER BY timestamp ASC")
    suspend fun getUndeliveredByTopic(topic: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE delivered = 0 ORDER BY timestamp ASC")
    suspend fun getAllUndelivered(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE delivered = 0 ORDER BY timestamp ASC")
    fun observeUndelivered(): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET delivered = 1, deliveredToPackage = :packageName WHERE id = :messageId")
    suspend fun markDelivered(messageId: Long, packageName: String)

    @Query("DELETE FROM messages WHERE delivered = 1 AND timestamp < :beforeTimestamp")
    suspend fun deleteDeliveredBefore(beforeTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE delivered = 0")
    suspend fun countUndelivered(): Int

    @Query("SELECT DISTINCT topic FROM messages WHERE delivered = 0")
    suspend fun getTopicsWithUndeliveredMessages(): List<String>
}
```

**Step 2: Create SubscriptionDao**

```kotlin
package com.example.udpservice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity): Long

    @Query("SELECT * FROM subscriptions WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("DELETE FROM subscriptions WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("UPDATE subscriptions SET lastActiveAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastActive(packageName: String, timestamp: Long)

    @Query("SELECT * FROM subscriptions WHERE topics LIKE '%' || :topic || '%'")
    suspend fun getSubscribersForTopic(topic: String): List<SubscriptionEntity>
}
```

**Step 3: Verify builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add udp-service/src/main/kotlin/com/example/udpservice/data/
git commit -m "feat: add Room DAOs for messages and subscriptions"
```

---

### Task 8: Create Room Database

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/data/UdpDatabase.kt`

**Step 1: Create UdpDatabase**

```kotlin
package com.example.udpservice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UdpDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: UdpDatabase? = null

        fun getInstance(context: Context): UdpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UdpDatabase::class.java,
                    "udp_broker.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

**Step 2: Verify builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add udp-service/src/main/kotlin/com/example/udpservice/data/UdpDatabase.kt
git commit -m "feat: add Room database singleton"
```

---

## Phase 4: Message Router and Service Integration

### Task 9: Create Message Router

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/MessageRouter.kt`
- Create: `udp-service/src/test/kotlin/com/example/udpservice/MessageRouterTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.example.udpservice

import com.example.udpservice.api.UdpDatagram
import com.example.udpservice.data.SubscriptionEntity
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class MessageRouterTest {

    @Test
    fun `routes message to subscriber with matching topic`() {
        val subscriptions = listOf(
            SubscriptionEntity(
                packageName = "com.app.weather",
                topics = "weather,alerts"
            ),
            SubscriptionEntity(
                packageName = "com.app.news",
                topics = "news,breaking"
            )
        )

        val datagram = UdpDatagram(
            topic = "weather",
            payload = "sunny".toByteArray(),
            sourceAddress = InetSocketAddress("192.168.1.1", 5000)
        )

        val targets = MessageRouter.findTargets(datagram, subscriptions)

        assertEquals(1, targets.size)
        assertEquals("com.app.weather", targets[0].packageName)
    }

    @Test
    fun `routes message to multiple subscribers with same topic`() {
        val subscriptions = listOf(
            SubscriptionEntity(
                packageName = "com.app.one",
                topics = "shared,unique1"
            ),
            SubscriptionEntity(
                packageName = "com.app.two",
                topics = "shared,unique2"
            )
        )

        val datagram = UdpDatagram(
            topic = "shared",
            payload = "data".toByteArray(),
            sourceAddress = InetSocketAddress("192.168.1.1", 5000)
        )

        val targets = MessageRouter.findTargets(datagram, subscriptions)

        assertEquals(2, targets.size)
        assertTrue(targets.any { it.packageName == "com.app.one" })
        assertTrue(targets.any { it.packageName == "com.app.two" })
    }

    @Test
    fun `returns empty list when no matching subscribers`() {
        val subscriptions = listOf(
            SubscriptionEntity(
                packageName = "com.app.weather",
                topics = "weather"
            )
        )

        val datagram = UdpDatagram(
            topic = "unknown",
            payload = "data".toByteArray(),
            sourceAddress = InetSocketAddress("192.168.1.1", 5000)
        )

        val targets = MessageRouter.findTargets(datagram, subscriptions)

        assertTrue(targets.isEmpty())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.MessageRouterTest"`
Expected: FAIL - class MessageRouter does not exist

**Step 3: Write minimal implementation**

```kotlin
package com.example.udpservice

import com.example.udpservice.api.UdpDatagram
import com.example.udpservice.data.SubscriptionEntity

object MessageRouter {

    fun findTargets(
        datagram: UdpDatagram,
        subscriptions: List<SubscriptionEntity>
    ): List<SubscriptionEntity> {
        return subscriptions.filter { subscription ->
            val topics = subscription.topics.split(",").map { it.trim() }
            datagram.topic in topics
        }
    }

    fun parseTopics(topicsString: String): List<String> {
        return topicsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun serializeTopics(topics: List<String>): String {
        return topics.joinToString(",")
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :udp-service:testDebugUnitTest --tests "com.example.udpservice.MessageRouterTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add udp-service/src/
git commit -m "feat: add MessageRouter for topic-based routing"
```

---

### Task 10: Integrate Socket with Service

**Files:**
- Modify: `udp-service/src/main/kotlin/com/example/udpservice/UdpBrokerService.kt`

**Step 1: Update UdpBrokerService with socket integration**

```kotlin
package com.example.udpservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.udpservice.api.UdpDatagram
import com.example.udpservice.data.MessageEntity
import com.example.udpservice.data.UdpDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.InetSocketAddress

class UdpBrokerService : Service() {

    companion object {
        const val CHANNEL_ID = "udp_broker_channel"
        const val MESSAGE_CHANNEL_ID = "udp_message_channel"
        const val NOTIFICATION_ID = 1
        const val DEFAULT_PORT = 5000
        const val EXTRA_PORT = "port"
    }

    private val binder = LocalBinder()
    private var udpSocket: UdpSocket? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var receiveJob: Job? = null

    private lateinit var database: UdpDatabase

    private val _incomingMessages = MutableSharedFlow<UdpDatagram>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<UdpDatagram> = _incomingMessages.asSharedFlow()

    val localPort: Int
        get() = udpSocket?.localPort ?: -1

    inner class LocalBinder : Binder() {
        fun getService(): UdpBrokerService = this@UdpBrokerService
    }

    override fun onCreate() {
        super.onCreate()
        database = UdpDatabase.getInstance(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT

        val notification = createNotification(port)
        startForeground(NOTIFICATION_ID, notification)

        startSocket(port)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSocket()
        scope.cancel()
    }

    private fun startSocket(port: Int) {
        if (udpSocket != null) return

        udpSocket = UdpSocket(port).also { socket ->
            socket.start()

            receiveJob = scope.launch {
                socket.incoming.collect { datagram ->
                    handleIncomingDatagram(datagram)
                }
            }
        }

        updateNotification()
    }

    private fun stopSocket() {
        receiveJob?.cancel()
        receiveJob = null
        udpSocket?.stop()
        udpSocket = null
    }

    private suspend fun handleIncomingDatagram(datagram: UdpDatagram) {
        // Emit to real-time flow for active listeners
        _incomingMessages.emit(datagram)

        // Store in database for buffered delivery
        val entity = MessageEntity(
            topic = datagram.topic,
            payload = datagram.payload,
            sourceAddress = datagram.sourceAddress.address.hostAddress ?: "unknown",
            sourcePort = datagram.sourceAddress.port,
            timestamp = datagram.timestamp
        )
        database.messageDao().insert(entity)

        // Send automatic ACK back to sender
        sendAck(datagram)

        // Check for subscribers and notify if needed
        notifySubscribersIfNeeded(datagram)
    }

    private suspend fun sendAck(original: UdpDatagram) {
        val ackDatagram = UdpDatagram(
            topic = "_ack",
            payload = original.timestamp.toString().toByteArray(),
            sourceAddress = InetSocketAddress("0.0.0.0", 0)
        )
        udpSocket?.send(ackDatagram, original.sourceAddress)
    }

    private suspend fun notifySubscribersIfNeeded(datagram: UdpDatagram) {
        val subscriptions = database.subscriptionDao().getSubscribersForTopic(datagram.topic)
        val subscribersWithNotifications = subscriptions.filter { it.notificationsEnabled }

        for (subscription in subscribersWithNotifications) {
            showMessageNotification(subscription.packageName, datagram.topic)
        }
    }

    private fun showMessageNotification(packageName: String, topic: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        val notification = Notification.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle("New message for $appName")
            .setContentText("Topic: $topic")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .apply {
                pendingIntent?.let { setContentIntent(it) }
            }
            .build()

        val notificationId = packageName.hashCode()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    suspend fun send(datagram: UdpDatagram, destination: InetSocketAddress) {
        udpSocket?.send(datagram, destination)
    }

    fun subscribe(packageName: String, topics: List<String>, notificationsEnabled: Boolean = true) {
        scope.launch {
            val subscription = com.example.udpservice.data.SubscriptionEntity(
                packageName = packageName,
                topics = MessageRouter.serializeTopics(topics),
                notificationsEnabled = notificationsEnabled
            )
            database.subscriptionDao().upsert(subscription)
        }
    }

    fun unsubscribe(packageName: String) {
        scope.launch {
            database.subscriptionDao().deleteByPackage(packageName)
        }
    }

    suspend fun getBufferedMessages(packageName: String): List<UdpDatagram> {
        val subscription = database.subscriptionDao().getByPackage(packageName) ?: return emptyList()
        val topics = MessageRouter.parseTopics(subscription.topics)

        val messages = mutableListOf<MessageEntity>()
        for (topic in topics) {
            messages.addAll(database.messageDao().getUndeliveredByTopic(topic))
        }

        // Mark as delivered
        for (message in messages) {
            database.messageDao().markDelivered(message.id, packageName)
        }

        return messages.map { entity ->
            UdpDatagram(
                topic = entity.topic,
                payload = entity.payload,
                sourceAddress = InetSocketAddress(entity.sourceAddress, entity.sourcePort),
                timestamp = entity.timestamp
            )
        }
    }

    fun getNetworkInfo(): NetworkInfo {
        val addresses = java.net.NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.map { it.hostAddress ?: "" }
            ?: emptyList()

        return NetworkInfo(
            port = localPort,
            addresses = addresses
        )
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "UDP Broker Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when UDP broker service is running"
        }

        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "UDP Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for incoming UDP messages"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(messageChannel)
    }

    private fun createNotification(port: Int = localPort): Notification {
        val info = if (port > 0) "running on port $port" else "starting..."
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("UDP Broker")
            .setContentText("Service $info")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    data class NetworkInfo(
        val port: Int,
        val addresses: List<String>
    )
}
```

**Step 2: Verify builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add udp-service/src/main/kotlin/com/example/udpservice/UdpBrokerService.kt
git commit -m "feat: integrate UDP socket with service, add routing and buffering"
```

---

## Phase 5: Client API for Consuming Apps

### Task 11: Create UdpBrokerClient

**Files:**
- Create: `udp-service/src/main/kotlin/com/example/udpservice/api/UdpBrokerClient.kt`

**Step 1: Create UdpBrokerClient**

```kotlin
package com.example.udpservice.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.udpservice.UdpBrokerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.net.InetSocketAddress

class UdpBrokerClient(private val context: Context) {

    private var service: UdpBrokerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as UdpBrokerService.LocalBinder
            service = localBinder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun bind(): Boolean {
        val intent = Intent(context, UdpBrokerService::class.java)
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (bound) {
            context.unbindService(connection)
            bound = false
            service = null
        }
    }

    fun isConnected(): Boolean = bound && service != null

    fun subscribe(topics: List<String>, notificationsEnabled: Boolean = true) {
        service?.subscribe(context.packageName, topics, notificationsEnabled)
    }

    fun unsubscribe() {
        service?.unsubscribe(context.packageName)
    }

    fun incomingMessages(): Flow<UdpDatagram> {
        return service?.incomingMessages ?: emptyFlow()
    }

    suspend fun send(topic: String, payload: ByteArray, destination: InetSocketAddress) {
        val datagram = UdpDatagram(
            topic = topic,
            payload = payload,
            sourceAddress = InetSocketAddress("0.0.0.0", 0)
        )
        service?.send(datagram, destination)
    }

    suspend fun getBufferedMessages(): List<UdpDatagram> {
        return service?.getBufferedMessages(context.packageName) ?: emptyList()
    }

    fun getNetworkInfo(): UdpBrokerService.NetworkInfo? {
        return service?.getNetworkInfo()
    }
}
```

**Step 2: Verify builds**

Run: `./gradlew :udp-service:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add udp-service/src/main/kotlin/com/example/udpservice/api/UdpBrokerClient.kt
git commit -m "feat: add UdpBrokerClient for consuming apps"
```

---

## Phase 6: App UI Completion

### Task 12: Update MainActivity with Full Status UI

**Files:**
- Modify: `app/src/main/kotlin/com/example/udpbroker/MainActivity.kt`

**Step 1: Update MainActivity with complete UI**

```kotlin
package com.example.udpbroker

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.udpbroker.ui.theme.UdpBrokerTheme
import com.example.udpservice.UdpBrokerService
import com.example.udpservice.api.UdpDatagram
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private var service: UdpBrokerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as UdpBrokerService.LocalBinder
            service = localBinder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            UdpBrokerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrokerApp(
                        getService = { service },
                        isBound = { bound },
                        onStartService = { startBrokerService() },
                        onStopService = { stopBrokerService() },
                        onBindService = { bindBrokerService() },
                        onUnbindService = { unbindBrokerService() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindBrokerService()
    }

    override fun onStop() {
        super.onStop()
        unbindBrokerService()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startBrokerService() {
        val intent = Intent(this, UdpBrokerService::class.java)
        startForegroundService(intent)
        bindBrokerService()
    }

    private fun stopBrokerService() {
        unbindBrokerService()
        val intent = Intent(this, UdpBrokerService::class.java)
        stopService(intent)
    }

    private fun bindBrokerService() {
        val intent = Intent(this, UdpBrokerService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun unbindBrokerService() {
        if (bound) {
            unbindService(connection)
            bound = false
            service = null
        }
    }
}

@Composable
fun BrokerApp(
    getService: () -> UdpBrokerService?,
    isBound: () -> Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onBindService: () -> Unit,
    onUnbindService: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var networkInfo by remember { mutableStateOf<UdpBrokerService.NetworkInfo?>(null) }
    var messageLog by remember { mutableStateOf(listOf<MessageLogEntry>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isBound()) {
        if (isBound()) {
            val service = getService()
            networkInfo = service?.getNetworkInfo()

            service?.incomingMessages?.collect { datagram ->
                val entry = MessageLogEntry(
                    timestamp = datagram.timestamp,
                    topic = datagram.topic,
                    payload = String(datagram.payload),
                    source = "${datagram.sourceAddress.address.hostAddress}:${datagram.sourceAddress.port}",
                    direction = "IN"
                )
                messageLog = (listOf(entry) + messageLog).take(50)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "UDP Broker",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRunning) "Running" else "Stopped",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                if (isRunning && networkInfo != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Port: ${networkInfo?.port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "IPs: ${networkInfo?.addresses?.joinToString(", ") ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control Button
        Button(
            onClick = {
                if (isRunning) {
                    onStopService()
                    isRunning = false
                    networkInfo = null
                } else {
                    onStartService()
                    isRunning = true
                    scope.launch {
                        kotlinx.coroutines.delay(500)
                        networkInfo = getService()?.getNetworkInfo()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Stop Service" else "Start Service")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Message Log
        Text(
            text = "Message Log",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (messageLog.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(messageLog) { entry ->
                        MessageLogItem(entry)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun MessageLogItem(entry: MessageLogEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[${entry.direction}] ${entry.topic}",
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.direction == "IN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = dateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = entry.payload,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2
        )
        Text(
            text = entry.source,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class MessageLogEntry(
    val timestamp: Long,
    val topic: String,
    val payload: String,
    val source: String,
    val direction: String
)
```

**Step 2: Verify app builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/udpbroker/MainActivity.kt
git commit -m "feat: complete app UI with status display and message log"
```

---

## Phase 7: Python Test Tool

### Task 13: Create Python UDP Sender Tool

**Files:**
- Create: `tools/udp-sender/pyproject.toml`
- Create: `tools/udp-sender/src/udp_sender/__init__.py`
- Create: `tools/udp-sender/src/udp_sender/cli.py`
- Create: `tools/udp-sender/src/udp_sender/sender.py`

**Step 1: Create pyproject.toml**

```toml
[project]
name = "udp-sender"
version = "0.1.0"
description = "UDP test sender for Android UDP Service"
requires-python = ">=3.10"
dependencies = [
    "click>=8.0",
]

[project.scripts]
udp-sender = "udp_sender.cli:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

**Step 2: Create __init__.py**

```python
"""UDP test sender for Android UDP Service."""
```

**Step 3: Create sender.py**

```python
"""UDP sender implementation."""

import socket
import struct
import time
from dataclasses import dataclass


@dataclass
class UdpMessage:
    topic: str
    payload: bytes

    def to_bytes(self) -> bytes:
        """Serialize to wire format: [2-byte topic length][topic][payload]"""
        topic_bytes = self.topic.encode("utf-8")
        header = struct.pack(">H", len(topic_bytes))
        return header + topic_bytes + self.payload


class UdpSender:
    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def send(self, topic: str, payload: str | bytes) -> None:
        """Send a single message."""
        if isinstance(payload, str):
            payload = payload.encode("utf-8")

        message = UdpMessage(topic=topic, payload=payload)
        data = message.to_bytes()
        self.socket.sendto(data, (self.host, self.port))

    def flood(self, topic: str, rate: int, duration: int) -> int:
        """Send messages at specified rate for duration seconds. Returns count sent."""
        interval = 1.0 / rate
        count = 0
        end_time = time.time() + duration

        while time.time() < end_time:
            payload = f"msg-{count}".encode("utf-8")
            message = UdpMessage(topic=topic, payload=payload)
            data = message.to_bytes()
            self.socket.sendto(data, (self.host, self.port))
            count += 1
            time.sleep(interval)

        return count

    def listen_for_ack(self, timeout: float = 1.0) -> tuple[str, bytes] | None:
        """Wait for ACK response. Returns (topic, payload) or None on timeout."""
        self.socket.settimeout(timeout)
        try:
            data, _ = self.socket.recvfrom(65535)
            if len(data) < 2:
                return None

            topic_len = struct.unpack(">H", data[:2])[0]
            if len(data) < 2 + topic_len:
                return None

            topic = data[2:2 + topic_len].decode("utf-8")
            payload = data[2 + topic_len:]
            return topic, payload
        except socket.timeout:
            return None

    def close(self) -> None:
        self.socket.close()
```

**Step 4: Create cli.py**

```python
"""CLI for UDP sender."""

import click

from .sender import UdpSender


@click.group()
def main():
    """UDP test sender for Android UDP Service."""
    pass


@main.command()
@click.option("--host", "-h", required=True, help="Target host IP address")
@click.option("--port", "-p", default=5000, help="Target port (default: 5000)")
@click.option("--topic", "-t", required=True, help="Message topic")
@click.option("--message", "-m", required=True, help="Message payload")
@click.option("--wait-ack", "-w", is_flag=True, help="Wait for ACK response")
def send(host: str, port: int, topic: str, message: str, wait_ack: bool):
    """Send a single UDP message."""
    sender = UdpSender(host, port)

    click.echo(f"Sending to {host}:{port}")
    click.echo(f"  Topic: {topic}")
    click.echo(f"  Payload: {message}")

    sender.send(topic, message)
    click.echo("Sent!")

    if wait_ack:
        click.echo("Waiting for ACK...")
        result = sender.listen_for_ack(timeout=2.0)
        if result:
            ack_topic, ack_payload = result
            click.echo(f"ACK received: topic={ack_topic}, payload={ack_payload.decode('utf-8')}")
        else:
            click.echo("No ACK received (timeout)")

    sender.close()


@main.command()
@click.option("--host", "-h", required=True, help="Target host IP address")
@click.option("--port", "-p", default=5000, help="Target port (default: 5000)")
@click.option("--topic", "-t", default="flood", help="Message topic (default: flood)")
@click.option("--rate", "-r", default=100, help="Messages per second (default: 100)")
@click.option("--duration", "-d", default=10, help="Duration in seconds (default: 10)")
def flood(host: str, port: int, topic: str, rate: int, duration: int):
    """Flood test - send messages at specified rate."""
    sender = UdpSender(host, port)

    click.echo(f"Flooding {host}:{port} at {rate} msg/s for {duration}s")
    click.echo(f"  Topic: {topic}")

    count = sender.flood(topic, rate, duration)
    click.echo(f"Sent {count} messages")

    sender.close()


@main.command()
@click.option("--host", "-h", required=True, help="Target host IP address")
@click.option("--port", "-p", default=5000, help="Target port (default: 5000)")
def interactive(host: str, port: int):
    """Interactive mode - type messages to send."""
    sender = UdpSender(host, port)

    click.echo(f"Interactive mode - sending to {host}:{port}")
    click.echo("Format: <topic> <message>")
    click.echo("Type 'quit' to exit")
    click.echo()

    while True:
        try:
            line = click.prompt("", prompt_suffix="> ")
            if line.lower() == "quit":
                break

            parts = line.split(" ", 1)
            if len(parts) < 2:
                click.echo("Format: <topic> <message>")
                continue

            topic, message = parts
            sender.send(topic, message)
            click.echo(f"Sent: [{topic}] {message}")

        except (KeyboardInterrupt, EOFError):
            break

    sender.close()
    click.echo("Bye!")


if __name__ == "__main__":
    main()
```

**Step 5: Verify Python tool works**

Run: `cd tools/udp-sender && uv run udp-sender --help`
Expected: Shows help with send, flood, interactive commands

**Step 6: Commit**

```bash
git add tools/
git commit -m "feat: add Python UDP sender test tool"
```

---

## Phase 8: Integration Testing

### Task 14: Manual Integration Test

**Step 1: Build and install the app**

Run: `./gradlew :app:installDebug`
Expected: App installed on connected device/emulator

**Step 2: Find device IP**

On the Android device, go to Settings > Network > WiFi > (your network) > IP address
Or run: `adb shell ip addr show wlan0`

**Step 3: Start the service**

- Open UDP Broker app
- Tap "Start Service"
- Note the port and IP shown in the status card

**Step 4: Send a test message**

Run: `cd tools/udp-sender && uv run udp-sender send -h <device-ip> -p 5000 -t test -m "hello world" -w`
Expected: "Sent!" followed by "ACK received"

**Step 5: Verify message in app**

- Check the message log in the app shows the received message
- Topic should be "test", payload should be "hello world"

**Step 6: Test background receive**

- Press home to background the app
- Send another message: `uv run udp-sender send -h <device-ip> -p 5000 -t test -m "background test" -w`
- Check notification appeared: "New message for UDP Broker"
- Tap notification to open app and see message

**Step 7: Commit test documentation**

Create test log and commit:
```bash
git add -A
git commit -m "docs: complete implementation of UDP broker service"
```

---

## Summary

This plan implements:

1. **Multi-module Gradle project** with `:udp-service` library and `:app` standalone broker
2. **Foreground service** with proper Android 10+ permissions
3. **Coroutine-based UDP socket** with Flow for real-time message streaming
4. **Room database** for message buffering and subscription storage
5. **Topic-based message routing** with header format `[2-byte len][topic][payload]`
6. **Automatic ACK** sent back to sender when message is stored
7. **Per-app notifications** with opt-in control
8. **Client API** (`UdpBrokerClient`) for consuming apps
9. **Compose UI** showing status, network info, and message log
10. **Python test tool** for sending test messages

Total tasks: 14
Estimated commits: 14
