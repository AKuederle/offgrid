package com.example.udpbroker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.udpservice.send.DeliveryStatus
import com.example.udpservice.send.OutboundMessage
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OutboundMessageList(
    messages: List<OutboundMessage>,
    onRetryClick: ((OutboundMessage) -> Unit)? = null,
    onCancelClick: ((OutboundMessage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Outbound Messages (${messages.size})",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    OutboundMessageItem(
                        message = message,
                        onRetryClick = onRetryClick?.let { { it(message) } },
                        onCancelClick = onCancelClick?.let { { it(message) } }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun OutboundMessageItem(
    message: OutboundMessage,
    onRetryClick: (() -> Unit)? = null,
    onCancelClick: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = remember(message.createdAt) { timeFormat.format(Date(message.createdAt)) }
    val peerString = remember(message.peer) { "${message.peer.hostString}:${message.peer.port}" }
    val displayText = remember(message.payload) { message.displayText() }
    val statusInfo = message.status.toStatusInfo()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        Icon(
            imageVector = statusInfo.icon,
            contentDescription = statusInfo.label,
            tint = statusInfo.color,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Message content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                Text(
                    timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    peerString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    statusInfo.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusInfo.color
                )
            }
            if (message.retryCount > 0) {
                Text(
                    "Retries: ${message.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                displayText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.End
        ) {
            // Retry button - only for WAITING status
            if (message.status == DeliveryStatus.WAITING && onRetryClick != null) {
                IconButton(onClick = onRetryClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Cancel button - only for non-terminal states
            if (!message.isTerminal && onCancelClick != null) {
                IconButton(onClick = onCancelClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private data class StatusInfo(
    val icon: ImageVector,
    val label: String,
    val color: Color
)

@Composable
private fun DeliveryStatus.toStatusInfo(): StatusInfo {
    return when (this) {
        DeliveryStatus.PENDING -> StatusInfo(
            icon = Icons.Default.Pending,
            label = "Pending",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DeliveryStatus.SENDING -> StatusInfo(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "Sending",
            color = MaterialTheme.colorScheme.primary
        )
        DeliveryStatus.DELIVERED -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            label = "Delivered",
            color = Color(0xFF4CAF50) // Green
        )
        DeliveryStatus.RETRYING -> StatusInfo(
            icon = Icons.Default.Refresh,
            label = "Retrying",
            color = MaterialTheme.colorScheme.tertiary
        )
        DeliveryStatus.WAITING -> StatusInfo(
            icon = Icons.Default.HourglassEmpty,
            label = "Waiting",
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun OutboundMessage.displayText(): String {
    return tryDecodeUtf8() ?: toHexPreview()
}

private fun OutboundMessage.tryDecodeUtf8(): String? {
    return try {
        val decoder = Charsets.UTF_8.newDecoder().apply {
            onMalformedInput(CodingErrorAction.REPORT)
            onUnmappableCharacter(CodingErrorAction.REPORT)
        }
        val decoded = decoder.decode(java.nio.ByteBuffer.wrap(payload)).toString()
        if (decoded.length > 100) {
            decoded.substring(0, 100) + "..."
        } else {
            decoded
        }
    } catch (e: CharacterCodingException) {
        null
    }
}

private fun OutboundMessage.toHexPreview(): String {
    return payload.take(24)
        .joinToString(" ") { byte ->
            "%02X".format(byte)
        } + if (payload.size > 24) "..." else ""
}
