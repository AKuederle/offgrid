package com.example.udpbroker

import com.example.udpservice.api.UdpPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thread-safe state holder for received UDP packets.
 * Maintains a list of packets with a maximum of [maxSize] entries, newest first.
 */
class PacketLog(private val maxSize: Int = 100) {
    private val _packets = MutableStateFlow<List<UdpPacket>>(emptyList())
    val packets: StateFlow<List<UdpPacket>> = _packets.asStateFlow()

    /**
     * Adds a packet to the log atomically.
     * If the log exceeds [maxSize], oldest packets are removed.
     */
    fun add(packet: UdpPacket) {
        _packets.update { current ->
            listOf(packet) + current.take(maxSize - 1)
        }
    }

    /**
     * Clears all packets from the log atomically.
     */
    fun clear() {
        _packets.update { emptyList() }
    }
}
