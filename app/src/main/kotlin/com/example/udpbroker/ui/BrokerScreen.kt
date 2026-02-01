package com.example.udpbroker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.udpservice.api.UdpPacket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiverState(
    val status: ServiceStatus = ServiceStatus.STOPPED,
    val port: Int? = null,
    val error: String? = null
)

enum class ServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerScreen(
    state: ReceiverState,
    packets: List<UdpPacket>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "UDP Broker",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Navigate to settings */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (state.status) {
                        ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                        ServiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                        ServiceStatus.STARTING -> MaterialTheme.colorScheme.secondaryContainer
                        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.surfaceContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (state.status) {
                            ServiceStatus.STOPPED -> "Stopped"
                            ServiceStatus.STARTING -> "Starting"
                            ServiceStatus.RUNNING -> "Running"
                            ServiceStatus.ERROR -> "Error"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = when (state.status) {
                            ServiceStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                            ServiceStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            ServiceStatus.STARTING -> MaterialTheme.colorScheme.onSecondaryContainer
                            ServiceStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Show port when running
                    if (state.status == ServiceStatus.RUNNING && state.port != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Port: ${state.port}",
                            style = MaterialTheme.typography.labelLarge,
                            color = when (state.status) {
                                ServiceStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                                ServiceStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                ServiceStatus.STARTING -> MaterialTheme.colorScheme.onSecondaryContainer
                                ServiceStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Show error message if present
                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            state.error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Control Buttons
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status == ServiceStatus.STOPPED || state.status == ServiceStatus.ERROR
            ) {
                Text("Start")
            }

            if (state.status == ServiceStatus.RUNNING || state.status == ServiceStatus.STARTING) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onStopClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop")
                }
            }

            // Packet list
            if (packets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Received Packets (${packets.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        items(
                            items = packets,
                            key = { it.timestamp }
                        ) { packet ->
                            PacketItem(packet)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PacketItem(packet: UdpPacket) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeString = remember(packet.timestamp) { timeFormat.format(Date(packet.timestamp)) }
    val sourceString = remember(packet.sourceAddress) {
        "${packet.sourceAddress.address.hostAddress}:${packet.sourceAddress.port}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row {
            Text(
                timeString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                sourceString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            packet.displayText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
