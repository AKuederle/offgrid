package com.example.udpbroker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.udpservice.persistence.PacketEntity
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI-specific receiver state model.
 * Named with "Ui" prefix to avoid collision with [com.example.udpservice.api.ReceiverState].
 */
data class UiReceiverState(
    val status: UiServiceStatus = UiServiceStatus.STOPPED,
    val port: Int? = null,
    val addresses: List<String> = emptyList(),
    val error: String? = null
)

/**
 * UI-specific service status enum.
 * Named with "Ui" prefix to avoid collision with service-layer types.
 */
enum class UiServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

/**
 * Compact service status and control panel without Scaffold.
 * Use this when embedding in another layout (e.g., as header content).
 */
@Composable
fun ServiceControlPanel(
    state: UiReceiverState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (state.status) {
                UiServiceStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                UiServiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                UiServiceStatus.STARTING -> MaterialTheme.colorScheme.secondaryContainer
                UiServiceStatus.STOPPED -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (state.status) {
                            UiServiceStatus.STOPPED -> "Stopped"
                            UiServiceStatus.STARTING -> "Starting..."
                            UiServiceStatus.RUNNING -> "Running"
                            UiServiceStatus.ERROR -> "Error"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = when (state.status) {
                            UiServiceStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                            UiServiceStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (state.status == UiServiceStatus.RUNNING && state.addresses.isNotEmpty()) {
                        Text(
                            "${state.addresses.first()}:${state.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    state.error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                when (state.status) {
                    UiServiceStatus.STOPPED, UiServiceStatus.ERROR -> {
                        Button(onClick = onStartClick) {
                            Text("Start")
                        }
                    }
                    UiServiceStatus.RUNNING, UiServiceStatus.STARTING -> {
                        OutlinedButton(onClick = onStopClick) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerScreen(
    state: UiReceiverState,
    packets: List<PacketEntity>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onEraseClick: (() -> Unit)? = null,
    onPacketClick: ((PacketEntity) -> Unit)? = null,
    showPacketList: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showEraseDialog by remember { mutableStateOf(false) }

    // Erase confirmation dialog
    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Erase All Data?") },
            text = { Text("This will permanently delete all ${packets.size} stored packets. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseDialog = false
                        onEraseClick?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
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
                        UiServiceStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                        UiServiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                        UiServiceStatus.STARTING -> MaterialTheme.colorScheme.secondaryContainer
                        UiServiceStatus.STOPPED -> MaterialTheme.colorScheme.surfaceContainer
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
                            UiServiceStatus.STOPPED -> "Stopped"
                            UiServiceStatus.STARTING -> "Starting"
                            UiServiceStatus.RUNNING -> "Running"
                            UiServiceStatus.ERROR -> "Error"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = when (state.status) {
                            UiServiceStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                            UiServiceStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            UiServiceStatus.STARTING -> MaterialTheme.colorScheme.onSecondaryContainer
                            UiServiceStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Show network info when running
                    if (state.status == UiServiceStatus.RUNNING && state.port != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

                        // Show IP addresses
                        if (state.addresses.isNotEmpty()) {
                            state.addresses.forEach { address ->
                                Text(
                                    "$address:${state.port}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = contentColor
                                )
                            }
                        } else {
                            Text(
                                "Port: ${state.port}",
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                        }
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
                enabled = state.status == UiServiceStatus.STOPPED || state.status == UiServiceStatus.ERROR
            ) {
                Text("Start")
            }

            if (state.status == UiServiceStatus.RUNNING || state.status == UiServiceStatus.STARTING) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onStopClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop")
                }
            }

            // Erase All Data button - only enabled when service is stopped and there are packets
            if (onEraseClick != null && packets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showEraseDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.status == UiServiceStatus.STOPPED,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Erase All Data")
                }
            }

            // Packet list (only shown when showPacketList is true)
            if (showPacketList && packets.isNotEmpty()) {
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
                            key = { it.id }
                        ) { packet ->
                            PacketItem(
                                packet = packet,
                                onClick = onPacketClick?.let { { it(packet) } }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PacketItem(
    packet: PacketEntity,
    onClick: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeString = remember(packet.timestamp) { timeFormat.format(Date(packet.timestamp)) }
    val sourceString = remember(packet.sourceIp, packet.sourcePort) {
        "${packet.sourceIp}:${packet.sourcePort}"
    }
    val displayText = remember(packet.data) { packet.displayText() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
            displayText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Human-readable representation of the packet data.
 * Returns UTF-8 decoded string (truncated to 200 chars) if valid UTF-8,
 * otherwise returns a hex preview of the first 32 bytes.
 */
private fun PacketEntity.displayText(): String {
    return tryDecodeUtf8() ?: toHexPreview()
}

private fun PacketEntity.tryDecodeUtf8(): String? {
    return try {
        val decoder = Charsets.UTF_8.newDecoder().apply {
            onMalformedInput(CodingErrorAction.REPORT)
            onUnmappableCharacter(CodingErrorAction.REPORT)
        }
        val decoded = decoder.decode(java.nio.ByteBuffer.wrap(data)).toString()
        if (decoded.length > 200) {
            decoded.substring(0, 200)
        } else {
            decoded
        }
    } catch (e: CharacterCodingException) {
        null
    }
}

private fun PacketEntity.toHexPreview(): String {
    return data.take(32)
        .joinToString(" ") { byte ->
            "%02X".format(byte)
        }
}
