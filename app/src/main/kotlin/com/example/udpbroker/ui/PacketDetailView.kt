package com.example.udpbroker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.udpservice.persistence.PacketEntity
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen detail view for a packet.
 * Displays all packet metadata and the full payload with scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketDetailView(
    packet: PacketEntity,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val timeString = remember(packet.timestamp) { timeFormat.format(Date(packet.timestamp)) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Packet Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetadataRow("ID", packet.id.toString())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("App ID", packet.appId)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Source", "${packet.sourceIp}:${packet.sourcePort}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Timestamp", timeString)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Size", "${packet.data.size} bytes")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payload Card - Text View
            Text(
                "Payload (Text)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = packet.displayTextFull(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payload Card - Hex View
            Text(
                "Payload (Hex)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = packet.toHexDump(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Full text representation of the packet payload.
 * Attempts UTF-8 decode, falls back to hex preview.
 */
private fun PacketEntity.displayTextFull(): String {
    return try {
        val decoder = Charsets.UTF_8.newDecoder().apply {
            onMalformedInput(CodingErrorAction.REPORT)
            onUnmappableCharacter(CodingErrorAction.REPORT)
        }
        decoder.decode(java.nio.ByteBuffer.wrap(data)).toString()
    } catch (e: CharacterCodingException) {
        "(Binary data - see hex view)"
    }
}

/**
 * Hex dump of the packet payload with 16 bytes per line.
 */
private fun PacketEntity.toHexDump(): String {
    if (data.isEmpty()) return "(empty)"

    return data.asSequence()
        .chunked(16)
        .mapIndexed { lineIndex, bytes ->
            val offset = String.format("%04X", lineIndex * 16)
            val hex = bytes.joinToString(" ") { String.format("%02X", it) }
            val ascii = bytes.map { byte ->
                val char = byte.toInt().toChar()
                if (char.isLetterOrDigit() || char in "!@#\$%^&*()_+-=[]{}|;':\",./<>? ") char else '.'
            }.joinToString("")
            "$offset  ${hex.padEnd(47)}  $ascii"
        }
        .joinToString("\n")
}
