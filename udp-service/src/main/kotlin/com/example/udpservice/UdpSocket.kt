package com.example.udpservice

import android.util.Log
import com.example.reliableudp.ReliableSocketImpl
import com.example.reliableudp.SocketState
import com.example.udpservice.api.PacketParser
import com.example.udpservice.api.ReceiverState
import com.example.udpservice.api.UdpPacket
import com.example.udpservice.send.OutboundMessage
import com.example.udpservice.send.SendQueue
import com.example.udpservice.send.SendQueueImpl
import com.example.udpservice.send.SendResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of UdpReceiver using the reliable transport layer.
 *
 * This class wraps ReliableSocketImpl and adapts it to the UdpReceiver interface,
 * providing automatic retry, fragmentation/reassembly, and delivery confirmation
 * while maintaining backward compatibility with existing code.
 *
 * @param ioDispatcher The dispatcher to use for socket operations.
 *                     Defaults to Dispatchers.IO.
 */
class UdpSocket(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UdpReceiver {

    companion object {
        private const val TAG = "UdpSocket"
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
    }

    private val _packets = MutableSharedFlow<UdpPacket>(extraBufferCapacity = 64)
    override val packets: SharedFlow<UdpPacket> = _packets.asSharedFlow()

    private val _state = MutableStateFlow<ReceiverState>(ReceiverState.Stopped)
    override val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private val _registeredAppIds = ConcurrentHashMap.newKeySet<String>()
    override val registeredAppIds: Set<String> get() = _registeredAppIds.toSet()

    private var reliableSocket: ReliableSocketImpl? = null
    private var collectJob: Job? = null
    private var stateCollectJob: Job? = null
    private var scope: CoroutineScope? = null

    /**
     * The send queue for outbound message management.
     * Set by the service after construction.
     * When set, wires up the transmitter to use the reliable socket.
     */
    var sendQueue: SendQueue? = null
        set(value) {
            field = value
            // Wire up the transmitter if it's a SendQueueImpl
            (value as? SendQueueImpl)?.transmitter = { destination, payload, onResult ->
                val socket = reliableSocket
                    ?: throw IllegalStateException("Socket not bound")
                socket.sendAsync(destination, payload, onResult)
            }
        }

    /**
     * Callback invoked when a valid packet is received.
     * Set by the service to persist packets to database.
     */
    var onPacketReceived: (suspend (UdpPacket, String, ByteArray) -> Unit)? = null

    override suspend fun start(port: Int) {
        require(port in MIN_PORT..MAX_PORT) {
            "Port must be between $MIN_PORT and $MAX_PORT, got $port"
        }

        when (val currentState = _state.value) {
            is ReceiverState.Running -> {
                throw IllegalStateException("Cannot start: already running on port ${currentState.port}")
            }
            is ReceiverState.Starting -> {
                throw IllegalStateException("Cannot start: already starting")
            }
            else -> {
                _state.value = ReceiverState.Starting
            }
        }

        try {
            Log.d(TAG, "Creating reliable socket on port $port")
            val socket = ReliableSocketImpl(ioDispatcher = ioDispatcher)
            reliableSocket = socket

            // Bind the reliable socket
            socket.bind(port)

            // Create scope for message collection
            val newScope = CoroutineScope(ioDispatcher + SupervisorJob())
            scope = newScope

            // Collect state changes from reliable socket
            stateCollectJob = newScope.launch {
                socket.state.collect { socketState ->
                    val receiverState = mapSocketState(socketState)
                    _state.value = receiverState
                    Log.d(TAG, "State changed to: $receiverState")
                }
            }

            // Collect messages and convert to UdpPacket
            collectJob = newScope.launch {
                socket.messages.collect { message ->
                    if (!message.isPresence) {
                        processReceivedMessage(message.payload, message.source, message.receivedAt)
                    }
                    // Notify SendQueue of peer activity (for WAITING message resume)
                    sendQueue?.onPeerActivity(message.source)
                }
            }

            // Start the send queue for outbound messages
            sendQueue?.start()

            Log.d(TAG, "Reliable socket started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start socket", e)
            _state.value = ReceiverState.Error(e.message ?: "Unknown error")
            reliableSocket?.close()
            reliableSocket = null
        }
    }

    private suspend fun processReceivedMessage(
        data: ByteArray,
        source: java.net.InetSocketAddress,
        timestamp: Long
    ) {
        // Parse appId prefix
        val parsed = PacketParser.parse(data)
        if (parsed == null) {
            Log.d(TAG, "Dropped message: invalid appId prefix from ${source.address}:${source.port}")
            return
        }

        // Check if appId is registered
        if (!_registeredAppIds.contains(parsed.appId)) {
            Log.d(TAG, "Dropped message: unregistered appId '${parsed.appId}' from ${source.address}:${source.port}")
            return
        }

        Log.d(TAG, "Received message: ${data.size} bytes, appId='${parsed.appId}' from ${source.address}:${source.port}")

        val udpPacket = UdpPacket(
            data = data,
            sourceAddress = source,
            timestamp = timestamp
        )

        // Notify callback for persistence
        onPacketReceived?.invoke(udpPacket, parsed.appId, parsed.payload)

        _packets.emit(udpPacket)
    }

    private fun mapSocketState(socketState: SocketState): ReceiverState {
        return when (socketState) {
            is SocketState.Unbound -> ReceiverState.Stopped
            is SocketState.Binding -> ReceiverState.Starting
            is SocketState.Bound -> {
                val addresses = NetworkUtils.getLocalIpAddresses()
                ReceiverState.Running(socketState.port, addresses)
            }
            is SocketState.Error -> ReceiverState.Error(socketState.message)
            is SocketState.Closed -> ReceiverState.Stopped
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping socket")

        // Stop the send queue first
        sendQueue?.stop()

        collectJob?.cancel()
        collectJob = null

        stateCollectJob?.cancel()
        stateCollectJob = null

        scope?.cancel()
        scope = null

        reliableSocket?.close()
        reliableSocket = null

        _state.value = ReceiverState.Stopped
        Log.d(TAG, "Socket stopped")
    }

    override fun registerAppId(appId: String) {
        _registeredAppIds.add(appId)
        Log.d(TAG, "Registered appId: $appId (total: ${_registeredAppIds.size})")
    }

    override fun unregisterAppId(appId: String) {
        _registeredAppIds.remove(appId)
        Log.d(TAG, "Unregistered appId: $appId (total: ${_registeredAppIds.size})")
    }

    override val outboundMessages: Flow<List<OutboundMessage>>
        get() = sendQueue?.observeAll() ?: emptyFlow()

    override suspend fun send(destination: InetSocketAddress, payload: ByteArray): SendResult {
        val queue = sendQueue
            ?: return SendResult.Failed("Send queue not initialized")

        Log.d(TAG, "Queueing message for ${destination.hostString}:${destination.port} (${payload.size} bytes)")
        return queue.enqueue(destination, payload)
    }

    override suspend fun cancelSend(messageId: Long): Boolean {
        val queue = sendQueue ?: return false
        Log.d(TAG, "Cancelling message $messageId")
        return queue.cancel(messageId)
    }
}
