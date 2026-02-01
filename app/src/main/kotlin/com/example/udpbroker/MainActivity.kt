package com.example.udpbroker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.udpbroker.ui.BrokerScreen
import com.example.udpbroker.ui.ReceiverState
import com.example.udpbroker.ui.ServiceStatus
import com.example.udpbroker.ui.theme.UDPBrokerTheme
import com.example.udpservice.UdpReceiver
import com.example.udpservice.UdpReceiverService
import com.example.udpservice.api.ReceiverState as ServiceReceiverState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var receiver: UdpReceiver? = null
    private var bound = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val packetLog = PacketLog()

    private var serviceBinder: UdpReceiverService.LocalBinder? = null

    // Observable state for UI
    private val _uiState = MutableStateFlow(ReceiverState())
    private val uiState: StateFlow<ReceiverState> = _uiState.asStateFlow()

    private var packetCollectionJob: Job? = null
    private var stateCollectionJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d(TAG, "Service connected")
            serviceBinder = binder as? UdpReceiverService.LocalBinder
            receiver = serviceBinder?.getReceiver()
            bound = true
            startPacketCollection()
            startStateCollection()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "Service disconnected")
            packetCollectionJob?.cancel()
            packetCollectionJob = null
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            serviceBinder = null
            receiver = null
            bound = false
            _uiState.value = ReceiverState()
        }
    }

    private fun startPacketCollection() {
        packetCollectionJob?.cancel()
        val currentReceiver = receiver ?: return
        packetCollectionJob = activityScope.launch {
            currentReceiver.packets.collect { packet ->
                packetLog.add(packet)
            }
        }
    }

    private fun startStateCollection() {
        stateCollectionJob?.cancel()
        val currentReceiver = receiver ?: return
        stateCollectionJob = activityScope.launch {
            currentReceiver.state.collect { receiverState ->
                _uiState.value = ReceiverState(
                    status = when (receiverState) {
                        is ServiceReceiverState.Stopped -> ServiceStatus.STOPPED
                        is ServiceReceiverState.Starting -> ServiceStatus.STARTING
                        is ServiceReceiverState.Running -> ServiceStatus.RUNNING
                        is ServiceReceiverState.Error -> ServiceStatus.ERROR
                    },
                    port = (receiverState as? ServiceReceiverState.Running)?.port,
                    addresses = (receiverState as? ServiceReceiverState.Running)?.addresses ?: emptyList(),
                    error = (receiverState as? ServiceReceiverState.Error)?.message
                )
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w(TAG, "Notification permission denied")
            Toast.makeText(
                this,
                R.string.notification_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        handleIntent(intent)
        setContent {
            UDPBrokerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainContent()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ServiceControlReceiver.EXTRA_AUTO_START, false) == true) {
            val port = intent.getIntExtra(ServiceControlReceiver.EXTRA_PORT, 5000)
            Log.d(TAG, "Auto-starting service on port $port from intent")
            startService(port)
        }
    }

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    override fun onStop() {
        super.onStop()
        unbindFromService()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    @Composable
    private fun MainContent() {
        val state by uiState.collectAsState()
        val packets by packetLog.packets.collectAsState()

        BrokerScreen(
            state = state,
            packets = packets,
            onStartClick = { startService() },
            onStopClick = { stopService() }
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(this, UdpReceiverService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromService() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    private fun startService(port: Int = 5000) {
        Log.d(TAG, "Starting service on port $port")
        val intent = Intent(this, UdpReceiverService::class.java)
        intent.putExtra("port", port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }
        // Note: No need to call bindToService() here - onStart() already does it
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")
        receiver?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service", e)
            }
        }
    }
}
