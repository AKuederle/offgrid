package com.example.udpservice

import android.util.Log
import com.example.udpservice.api.ReceiverState
import com.example.udpservice.api.UdpPacket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Implementation of UdpReceiver that manages a UDP socket for receiving packets.
 *
 * This class handles the lifecycle of a UDP socket, including binding to a port,
 * receiving packets, and managing state transitions. Thread-safe via mutex.
 *
 * @param ioDispatcher The dispatcher to use for blocking socket operations.
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

    private val stateMutex = Mutex()
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var scope: CoroutineScope? = null

    override suspend fun start(port: Int) {
        require(port in MIN_PORT..MAX_PORT) {
            "Port must be between $MIN_PORT and $MAX_PORT, got $port"
        }

        stateMutex.withLock {
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
        }

        try {
            withContext(ioDispatcher) {
                Log.d(TAG, "Creating socket on port $port")
                val newSocket = DatagramSocket(port)
                socket = newSocket
                val boundPort = newSocket.localPort
                Log.d(TAG, "Socket bound to port $boundPort")

                // Get local addresses for display
                val addresses = NetworkUtils.getLocalIpAddresses()

                _state.value = ReceiverState.Running(boundPort, addresses)

                // Create a scope for the receive loop
                val newScope = CoroutineScope(ioDispatcher + SupervisorJob())
                scope = newScope

                // Start the receive loop
                Log.d(TAG, "Starting receive loop")
                receiveJob = newScope.launch {
                    receiveLoop(newSocket)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start socket", e)
            _state.value = ReceiverState.Error(e.message ?: "Unknown error")
            socket?.close()
            socket = null
        }
    }

    private suspend fun receiveLoop(socket: DatagramSocket) {
        Log.d(TAG, "Receive loop started")
        val buffer = ByteArray(65535)

        while (currentCoroutineContext().isActive && !socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet) // Blocking call
                Log.d(TAG, "Received packet: ${packet.length} bytes from ${packet.address}:${packet.port}")

                val data = packet.data.copyOf(packet.length)
                val sourceAddress = InetSocketAddress(packet.address, packet.port)

                val udpPacket = UdpPacket(
                    data = data,
                    sourceAddress = sourceAddress
                )

                _packets.emit(udpPacket)
            } catch (e: Exception) {
                // Socket closed or error - exit loop
                if (!socket.isClosed) {
                    Log.e(TAG, "Receive error", e)
                    _state.value = ReceiverState.Error(e.message ?: "Receive error")
                }
                break
            }
        }
        Log.d(TAG, "Receive loop ended")
    }

    override fun stop() {
        Log.d(TAG, "Stopping socket")
        receiveJob?.cancel()
        receiveJob = null

        scope?.cancel()
        scope = null

        socket?.close()
        socket = null

        _state.value = ReceiverState.Stopped
        Log.d(TAG, "Socket stopped")
    }
}
