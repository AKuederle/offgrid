package com.example.udpbroker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.udpservice.UdpReceiverService

/**
 * Broadcast receiver for controlling the UDP service via adb commands.
 *
 * Due to Android 12+ restrictions on starting foreground services from background,
 * START_SERVICE launches the MainActivity which then starts the service.
 *
 * Usage:
 *   Start: adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver -a com.example.udpbroker.START_SERVICE --ei port 5000
 *   Stop:  adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver -a com.example.udpbroker.STOP_SERVICE
 */
class ServiceControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.example.udpbroker.START_SERVICE"
        const val ACTION_STOP = "com.example.udpbroker.STOP_SERVICE"
        const val EXTRA_PORT = "port"
        const val EXTRA_AUTO_START = "auto_start"
        const val DEFAULT_PORT = 5000
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("ServiceControl", "Received broadcast: ${intent.action}")

        when (intent.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
                android.util.Log.d("ServiceControl", "Launching activity to start service on port $port")
                launchActivityToStart(context, port)
            }
            ACTION_STOP -> {
                android.util.Log.d("ServiceControl", "Stopping service")
                stopService(context)
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
