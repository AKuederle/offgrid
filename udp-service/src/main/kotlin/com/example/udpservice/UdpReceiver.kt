package com.example.udpservice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.udpservice.api.UdpPacket
import com.example.udpservice.api.ReceiverState
import com.example.udpservice.send.OutboundMessage
import com.example.udpservice.send.SendResult
import java.net.InetSocketAddress

/**
 * Interface for receiving UDP packets asynchronously.
 *
 * Implementations of this interface provide reactive streams for observing
 * incoming UDP packets and receiver state changes. Packets are filtered
 * by registered appIds - only packets with valid registered appIds are
 * emitted and persisted.
 */
interface UdpReceiver {
    /**
     * A shared flow of incoming UDP packets.
     *
     * Collectors will receive packets as they arrive. Late collectors
     * will not receive packets that were emitted before they started collecting.
     * Only packets with registered appIds will be emitted.
     */
    val packets: SharedFlow<UdpPacket>

    /**
     * A state flow representing the current state of the receiver.
     *
     * This flow always has a current value and emits updates when the
     * receiver state changes (e.g., from stopped to running).
     */
    val state: StateFlow<ReceiverState>

    /**
     * The set of currently registered appIds.
     */
    val registeredAppIds: Set<String>

    /**
     * Starts the UDP receiver on the specified port.
     *
     * This is a suspending function that will bind to the specified port
     * and begin listening for incoming UDP packets. Received packets will
     * be emitted to the [packets] flow.
     *
     * @param port The UDP port to listen on. Defaults to 5000.
     * @throws IllegalStateException if the receiver is already running.
     */
    suspend fun start(port: Int = 5000)

    /**
     * Stops the UDP receiver.
     *
     * This will close the underlying socket and stop listening for packets.
     * The [state] flow will be updated to reflect the stopped state.
     */
    fun stop()

    /**
     * Register an appId to receive packets for.
     *
     * Packets with this appId prefix will be persisted and emitted to observers.
     * Packets with unregistered appIds are dropped.
     *
     * @param appId The application identifier to register
     */
    fun registerAppId(appId: String)

    /**
     * Unregister an appId.
     *
     * Packets with this appId will no longer be processed.
     * Existing stored packets are not deleted.
     *
     * @param appId The application identifier to unregister
     */
    fun unregisterAppId(appId: String)

    /**
     * A flow of all outbound messages and their delivery status.
     *
     * Emits the current list of all outbound messages whenever
     * any message's status changes.
     */
    val outboundMessages: Flow<List<OutboundMessage>>

    /**
     * Queue a message for reliable delivery to a peer.
     *
     * The message is persisted to the database before this method returns,
     * ensuring it survives app restarts. Actual transmission happens
     * asynchronously with automatic retry on failure.
     *
     * @param destination The target peer address
     * @param payload Message content (max 64KB)
     * @return SendResult indicating success (with message ID) or failure
     */
    suspend fun send(destination: InetSocketAddress, payload: ByteArray): SendResult

    /**
     * Cancel a pending outbound message.
     *
     * Only non-delivered messages can be cancelled.
     *
     * @param messageId The ID of the message to cancel
     * @return true if cancelled, false if not found or already delivered
     */
    suspend fun cancelSend(messageId: Long): Boolean
}
