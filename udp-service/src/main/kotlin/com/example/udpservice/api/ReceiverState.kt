package com.example.udpservice.api

sealed interface ReceiverState {
    data object Stopped : ReceiverState
    data object Starting : ReceiverState
    data class Running(val port: Int, val addresses: List<String>) : ReceiverState
    data class Error(val message: String) : ReceiverState
}
