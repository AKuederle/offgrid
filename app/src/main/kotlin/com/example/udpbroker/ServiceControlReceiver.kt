package com.example.udpbroker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.udpservice.UdpReceiverService

/**
 * Broadcast receiver for controlling the UDP service via adb commands.
 *
 * Due to Android 12+ restrictions on starting foreground services from background,
 * START_SERVICE launches the MainActivity which then starts the service.
 *
 * Note: This receiver is protected by a signature-level permission. Only apps signed
 * with the same certificate can send broadcasts to it.
 *
 * Usage (from adb with shell user):
 *   Start: adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver -a com.example.udpbroker.START_SERVICE --ei port 5000
 *   Stop:  adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver -a com.example.udpbroker.STOP_SERVICE
 */
class ServiceControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceControlReceiver"
        const val ACTION_START = "com.example.udpbroker.START_SERVICE"
        const val ACTION_STOP = "com.example.udpbroker.STOP_SERVICE"
        const val EXTRA_PORT = "port"
        const val EXTRA_AUTO_START = "auto_start"
        const val DEFAULT_PORT = 5000
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                Log.d(TAG, "Launching activity to start service on port $port")
                launchActivityToStart(context, port)
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopService(context)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    private fun launchActivityToStart(context: Context, port: Int) {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_AUTO_START, true)
        }
        context.startActivity(activityIntent)
    }

    private fun stopService(context: Context) {
        val serviceIntent = Intent(context, UdpReceiverService::class.java)
        context.stopService(serviceIntent)
    }
}
