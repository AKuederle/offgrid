package com.example.udpservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.udpservice.api.UdpPacket
import com.example.udpservice.persistence.PacketDatabase
import com.example.udpservice.persistence.PacketDao
import com.example.udpservice.persistence.PacketEntity
import com.example.udpservice.presence.BroadcastResult
import com.example.udpservice.presence.PresenceBroadcaster
import com.example.udpservice.presence.PresenceBroadcasterImpl
import com.example.udpservice.presence.PresenceConfig
import com.example.udpservice.send.SendQueue
import com.example.udpservice.send.SendQueueImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for receiving UDP packets.
 *
 * This service manages the lifecycle of a UDP receiver and exposes it to bound clients
 * through the LocalBinder pattern. The service runs in the foreground with a persistent
 * notification showing the listening port.
 *
 * The service creates and manages a [UdpSocket] instance that handles actual UDP
 * packet reception. Clients can bind to this service to access the [UdpReceiver]
 * interface.
 */
class UdpReceiverService : Service() {

    // Create socket immediately so it's available when clients bind
    private val udpSocket = UdpSocket()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Database for packet persistence
    private val database by lazy { PacketDatabase.getInstance(applicationContext) }
    private val packetDao: PacketDao by lazy { database.packetDao() }

    // Send queue for outbound messages
    private val sendQueue: SendQueue by lazy {
        SendQueueImpl(database.outboundMessageDao())
    }

    // Presence broadcaster for peer discovery
    private val presenceBroadcaster: PresenceBroadcaster by lazy {
        PresenceBroadcasterImpl(PresenceConfig(broadcastPort = DEFAULT_PORT))
    }

    companion object {
        private const val TAG = "UdpReceiverService"
        private const val NOTIFICATION_CHANNEL_ID = "udp_receiver_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "UDP Receiver"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_PORT = 5000
    }

    /**
     * Binder for clients to access the UdpReceiver from this service.
     */
    inner class LocalBinder : Binder() {
        fun getReceiver(): UdpReceiver = udpSocket
        fun getService(): UdpReceiverService = this@UdpReceiverService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupPersistence()
        setupSendQueue()
    }

    /**
     * Set up the send queue for outbound message management.
     * Links the send queue to the UDP socket.
     */
    private fun setupSendQueue() {
        udpSocket.sendQueue = sendQueue
    }

    /**
     * Set up packet persistence callback.
     * When a valid packet is received, persist it to the database.
     */
    private fun setupPersistence() {
        udpSocket.onPacketReceived = { packet, appId, payload ->
            persistPacket(packet, appId, payload)
        }
    }

    /**
     * Persist a received packet to the database.
     * Runs in the service scope to not block the receive loop.
     */
    private suspend fun persistPacket(packet: UdpPacket, appId: String, payload: ByteArray) {
        try {
            val entity = packet.toEntity(appId, payload)
            packetDao.insertPacket(entity)
            Log.d(TAG, "Persisted packet: appId=$appId, size=${payload.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist packet", e)
            // Continue receiving - don't let DB errors stop service
        }
    }

    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT
        Log.d(TAG, "Starting on port $port")

        // Start foreground with notification
        val notification = createForegroundNotification(port)
        startForeground(NOTIFICATION_ID, notification)

        // Start the UDP socket and send presence broadcast
        serviceScope.launch {
            try {
                udpSocket.start(port)
                sendPresenceBroadcast()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start socket", e)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying service")
        super.onDestroy()
        udpSocket.stop()
        serviceScope.cancel()
    }

    /**
     * Send a presence broadcast to notify peers that this device is online.
     * Rate-limited to prevent broadcast storms.
     */
    private suspend fun sendPresenceBroadcast() {
        when (val result = presenceBroadcaster.broadcast()) {
            is BroadcastResult.Success -> {
                Log.d(TAG, "Presence broadcast sent")
            }
            is BroadcastResult.RateLimited -> {
                Log.d(TAG, "Presence broadcast rate-limited, wait ${result.waitMillis}ms")
            }
            is BroadcastResult.Failed -> {
                Log.w(TAG, "Presence broadcast failed: ${result.reason}")
            }
        }
    }

    /**
     * Creates a notification channel for the foreground service notification.
     * This is required for Android 8.0+ devices.
     * Uses IMPORTANCE_LOW for silent notifications (no sound/vibration).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for UDP receiver service"
            setShowBadge(false)
        }

        val notificationManager =
            getSystemService(NotificationManager::class.java) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates the foreground notification to display while the service is running.
     * The notification shows the UDP port that the service is listening on.
     * Uses PRIORITY_LOW for silent notifications.
     */
    private fun createForegroundNotification(port: Int): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("UDP Receiver")
            .setContentText("Listening on port $port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
