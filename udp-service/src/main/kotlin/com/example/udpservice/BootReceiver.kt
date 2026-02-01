package com.example.udpservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Broadcast receiver that starts the UDP service when the device boots.
 *
 * To use this, register it in your AndroidManifest.xml:
 * ```xml
 * <receiver
 *     android:name="com.example.udpservice.BootReceiver"
 *     android:exported="true"
 *     android:enabled="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED" />
 *     </intent-filter>
 * </receiver>
 * ```
 *
 * And add the permission:
 * ```xml
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 * ```
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val DEFAULT_PORT = 5000
        const val PREF_AUTOSTART = "udp_autostart"
        const val PREF_PORT = "udp_port"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed, checking if autostart enabled")

        val prefs = context.getSharedPreferences("udp_service", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(PREF_AUTOSTART, false)
        val port = prefs.getInt(PREF_PORT, DEFAULT_PORT)

        if (autoStart) {
            Log.d(TAG, "Starting UDP service on port $port")
            val serviceIntent = Intent(context, UdpReceiverService::class.java).apply {
                putExtra("port", port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "Autostart disabled, not starting service")
        }
    }
}
