package com.example.udpbroker

import com.example.udpservice.api.UdpPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for received UDP packets.
 * Maintains a list of packets with a maximum of 100 entries, newest first.
 */
class PacketLog(private val maxSize: Int = 100) {
    private val _packets = MutableStateFlow<List<UdpPacket>>(emptyList())
    val packets: StateFlow<List<UdpPacket>> = _packets.asStateFlow()

    fun add(packet: UdpPacket) {
        _packets.value = listOf(packet) + _packets.value.take(maxSize - 1)
    }

    fun clear() {
        _packets.value = emptyList()
    }
}
