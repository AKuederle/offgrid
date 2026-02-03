package com.example.udpbroker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.udpbroker.ui.MessagesScreen
import com.example.udpservice.persistence.PacketDao
import com.example.udpservice.persistence.PacketEntity
import kotlinx.coroutines.flow.Flow

/**
 * Navigation route definitions for the app.
 */
object Routes {
    const val HOME = "home"
    const val MESSAGES = "messages/{prefix}"

    fun messages(prefix: String) = "messages/$prefix"
}

/**
 * Deep link URI pattern for message screens.
 *
 * Format: udptest://messages/{prefix}
 * Example: udptest://messages/broker
 */
const val DEEP_LINK_URI_PATTERN = "udptest://messages/{prefix}"

/**
 * Main navigation host for the app.
 *
 * Handles navigation between screens and deep link support.
 *
 * @param navController The navigation controller
 * @param packetDao DAO for packet database operations
 * @param onMarkAsRead Callback to mark messages as read
 * @param onPacketClick Callback when a packet is clicked
 * @param homeContent Content to display for the home route
 * @param modifier Modifier for the NavHost
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    packetDao: PacketDao,
    onMarkAsRead: suspend (String) -> Unit,
    onPacketClick: (PacketEntity) -> Unit = {},
    homeContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            homeContent()
        }

        composable(
            route = Routes.MESSAGES,
            arguments = listOf(
                navArgument("prefix") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = DEEP_LINK_URI_PATTERN }
            )
        ) { backStackEntry ->
            val prefix = backStackEntry.arguments?.getString("prefix") ?: ""
            MessagesScreen(
                prefix = prefix,
                packetsFlow = packetDao.observePacketsByAppId(prefix),
                onMarkAsRead = { prefixToMark ->
                    // This will be called from LaunchedEffect in MessagesScreen
                    kotlinx.coroutines.runBlocking {
                        onMarkAsRead(prefixToMark)
                    }
                },
                onPacketClick = onPacketClick
            )
        }
    }
}
