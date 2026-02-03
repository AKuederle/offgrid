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
import com.example.udpbroker.ui.BrokerApp
import com.example.udpbroker.ui.PacketDetailView
import com.example.udpbroker.ui.ServiceControlPanel
import com.example.udpbroker.ui.UiReceiverState
import com.example.udpbroker.ui.UiServiceStatus
import com.example.udpbroker.ui.theme.UDPBrokerTheme
import com.example.udpservice.UdpReceiver
import com.example.udpservice.UdpReceiverService
import com.example.udpservice.persistence.PacketDatabase
import com.example.udpservice.persistence.PacketEntity
import com.example.udpservice.registration.AppRegistration
import com.example.udpservice.registration.RegistrationRepositoryImpl
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
        // App prefixes for receiving UDP packets
        // The broker app registers both prefixes and broadcasts to itself for testing
        val APP_PREFIXES = listOf("broker", "alerts")
    }

    private var receiver: UdpReceiver? = null
    private var bound = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Database for packet persistence
    private val database by lazy { PacketDatabase.getInstance(applicationContext) }
    private val packetDao by lazy { database.packetDao() }
    private val registrationRepository by lazy {
        RegistrationRepositoryImpl(database.appRegistrationDao())
    }

    // Flow of packets from database (filtered by our appId)
    private val _packets = MutableStateFlow<List<PacketEntity>>(emptyList())

    // Currently selected packet for detail view (null = show list)
    private val _selectedPacket = MutableStateFlow<PacketEntity?>(null)

    // Deep link prefix to navigate to
    private val _deepLinkPrefix = MutableStateFlow<String?>(null)

    private var serviceBinder: UdpReceiverService.LocalBinder? = null

    // Observable state for UI
    private val _uiState = MutableStateFlow(UiReceiverState())
    private val uiState: StateFlow<UiReceiverState> = _uiState.asStateFlow()

    private var packetCollectionJob: Job? = null
    private var stateCollectionJob: Job? = null
    private var databaseObserveJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d(TAG, "Service connected")
            serviceBinder = binder as? UdpReceiverService.LocalBinder
            receiver = serviceBinder?.getReceiver()
            bound = true

            // Register all our app prefixes to receive packets
            APP_PREFIXES.forEach { prefix ->
                receiver?.registerAppId(prefix)
                Log.d(TAG, "Registered appId: $prefix")
            }

            startStateCollection()
            startDatabaseObservation()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "Service disconnected")
            packetCollectionJob?.cancel()
            packetCollectionJob = null
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            databaseObserveJob?.cancel()
            databaseObserveJob = null
            serviceBinder = null
            receiver = null
            bound = false
            _uiState.value = UiReceiverState()
        }
    }

    private fun startDatabaseObservation() {
        // Database observation is now handled by BrokerApp/MessagesScreen
        // This method is kept for backward compatibility but does nothing
    }

    private fun startStateCollection() {
        stateCollectionJob?.cancel()
        val currentReceiver = receiver ?: return
        stateCollectionJob = activityScope.launch {
            currentReceiver.state.collect { receiverState ->
                _uiState.value = UiReceiverState(
                    status = when (receiverState) {
                        is ServiceReceiverState.Stopped -> UiServiceStatus.STOPPED
                        is ServiceReceiverState.Starting -> UiServiceStatus.STARTING
                        is ServiceReceiverState.Running -> UiServiceStatus.RUNNING
                        is ServiceReceiverState.Error -> UiServiceStatus.ERROR
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
        registerAppPrefixes()
        handleIntent(intent)
        setContent {
            UDPBrokerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainContent()
                }
            }
        }
    }

    /**
     * Register the app prefixes with notification configuration.
     * This is done on startup to ensure prefixes are registered before messages arrive.
     */
    private fun registerAppPrefixes() {
        activityScope.launch {
            APP_PREFIXES.forEach { prefix ->
                try {
                    val registration = AppRegistration(
                        prefix = prefix,
                        packageName = packageName,
                        notificationsEnabled = true,
                        deepLinkUri = "udptest://messages/$prefix"
                    )
                    registrationRepository.register(registration)
                    Log.d(TAG, "Registered prefix: $prefix with deep link udptest://messages/$prefix")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register prefix: $prefix", e)
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

        // Handle deep link (udptest://messages/{prefix})
        val data = intent?.data
        if (data != null && data.scheme == "udptest" && data.host == "messages") {
            val prefix = data.pathSegments.firstOrNull()
            if (prefix != null && prefix in APP_PREFIXES) {
                Log.d(TAG, "Deep link navigation to prefix: $prefix")
                _deepLinkPrefix.value = prefix
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-start the service when app opens
        startService()
    }

    override fun onStop() {
        super.onStop()
        // Clear active prefix so notifications will show when backgrounded
        AppState.activePrefix = null
        unbindFromService()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    @Composable
    private fun MainContent() {
        val state by uiState.collectAsState()
        val selectedPacket by _selectedPacket.collectAsState()
        val deepLinkPrefix by _deepLinkPrefix.collectAsState()

        selectedPacket?.let { packet ->
            PacketDetailView(
                packet = packet,
                onBackClick = { _selectedPacket.value = null }
            )
        } ?: BrokerApp(
            packetDao = packetDao,
            onMarkAsRead = { prefix -> markAsRead(prefix) },
            onPacketClick = { packet -> _selectedPacket.value = packet },
            initialPrefix = deepLinkPrefix,
            headerContent = {
                ServiceControlPanel(
                    state = state,
                    onStartClick = { startService() },
                    onStopClick = { stopService() }
                )
            }
        )
    }

    private fun markAsRead(prefix: String) {
        activityScope.launch {
            try {
                val count = packetDao.markAllAsRead(prefix)
                if (count > 0) {
                    Log.d(TAG, "Marked $count messages as read for prefix: $prefix")
                    // Note: For production apps, consider deleting read messages periodically
                    // For this test app, we keep read messages visible for debugging
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark messages as read", e)
            }
        }
    }

    private fun eraseAllData() {
        activityScope.launch {
            try {
                var totalDeleted = 0
                APP_PREFIXES.forEach { prefix ->
                    totalDeleted += packetDao.deletePacketsByAppId(prefix)
                }
                Log.d(TAG, "Erased $totalDeleted packets")
                Toast.makeText(
                    this@MainActivity,
                    "Erased $totalDeleted packets",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to erase data", e)
                Toast.makeText(
                    this@MainActivity,
                    "Failed to erase data",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
        if (bound) return
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
        // Bind to get state updates
        bindToService()
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")
        try {
            // Must unbind first - Android won't stop a bound service
            unbindFromService()
            receiver = null
            val serviceIntent = Intent(this, UdpReceiverService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }
}
