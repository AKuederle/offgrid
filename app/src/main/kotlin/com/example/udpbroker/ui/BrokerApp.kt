package com.example.udpbroker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.udpservice.persistence.PacketDao
import com.example.udpservice.persistence.PacketEntity
import kotlinx.coroutines.flow.Flow

/**
 * Main app composable with tab navigation for different message prefixes.
 *
 * Displays a tab bar with "broker" and "alerts" tabs, each showing
 * the MessagesScreen for that prefix.
 *
 * @param packetDao DAO for packet database operations
 * @param onMarkAsRead Callback to mark messages as read
 * @param onPacketClick Callback when a packet is clicked
 * @param initialPrefix Initial prefix to show (for deep link navigation)
 * @param headerContent Optional header content to display above the tabs (e.g., service status)
 */
@Composable
fun BrokerApp(
    packetDao: PacketDao,
    onMarkAsRead: (String) -> Unit,
    onPacketClick: (PacketEntity) -> Unit = {},
    initialPrefix: String? = null,
    headerContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = listOf("broker", "alerts")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // React to deep link changes
    LaunchedEffect(initialPrefix) {
        initialPrefix?.let { prefix ->
            val index = tabs.indexOf(prefix)
            if (index >= 0) {
                selectedTabIndex = index
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header content (service status, etc.)
            headerContent()

            // Tab row for switching between prefixes
            TabRow(
                selectedTabIndex = selectedTabIndex
            ) {
                tabs.forEachIndexed { index, prefix ->
                    val unreadCount by packetDao.observeUnreadCount(prefix)
                        .collectAsState(initial = 0)

                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            if (unreadCount > 0) {
                                Text("$prefix ($unreadCount)")
                            } else {
                                Text(prefix)
                            }
                        }
                    )
                }
            }

            // Messages screen for selected tab
            val currentPrefix = tabs[selectedTabIndex]
            MessagesScreen(
                prefix = currentPrefix,
                packetsFlow = packetDao.observePacketsByAppId(currentPrefix),
                onMarkAsRead = onMarkAsRead,
                onPacketClick = onPacketClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
