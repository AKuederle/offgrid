package com.example.udpservice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.udpservice.api.UdpPacket
import com.example.udpservice.api.ReceiverState

/**
 * Interface for receiving UDP packets asynchronously.
 *
 * Implementations of this interface provide reactive streams for observing
 * incoming UDP packets and receiver state changes.
 */
interface UdpReceiver {
    /**
     * A shared flow of incoming UDP packets.
     *
     * Collectors will receive packets as they arrive. Late collectors
     * will not receive packets that were emitted before they started collecting.
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
}
