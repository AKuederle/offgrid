package com.example.udpbroker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.udpbroker.AppState
import com.example.udpservice.persistence.PacketDao
import com.example.udpservice.persistence.PacketEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying messages for a specific prefix.
 *
 * Observes packets via Room Flow and automatically marks messages as read
 * when the screen is visible. Sets AppState.activePrefix on enter and
 * clears it on exit.
 *
 * @param prefix The app prefix to display messages for
 * @param packetsFlow Flow of packets from the database
 * @param onMarkAsRead Callback to mark all messages as read
 * @param onPacketClick Callback when a packet is clicked
 */
@Composable
fun MessagesScreen(
    prefix: String,
    packetsFlow: Flow<List<PacketEntity>>,
    onMarkAsRead: (String) -> Unit,
    onPacketClick: (PacketEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val packets by packetsFlow.collectAsState(initial = emptyList())

    // Set/clear active prefix for foreground detection
    DisposableEffect(prefix) {
        AppState.activePrefix = prefix
        onDispose {
            AppState.activePrefix = null
        }
    }

    // For test app: Don't auto-mark messages as read
    // This allows notifications to show even when the app is in foreground
    // Production apps would auto-mark here:
    // LaunchedEffect(prefix, packets) {
    //     if (packets.isNotEmpty()) { onMarkAsRead(prefix) }
    // }

    Column(modifier = modifier.fillMaxSize()) {
        if (packets.isEmpty()) {
            EmptyState(prefix = prefix)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(packets, key = { it.id }) { packet ->
                    MessageCard(
                        packet = packet,
                        onClick = { onPacketClick(packet) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(prefix: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No messages",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Messages for \"$prefix\" will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageCard(
    packet: PacketEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (packet.isRead)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${packet.sourceIp}:${packet.sourcePort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormat.format(Date(packet.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String(packet.data, Charsets.UTF_8),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
