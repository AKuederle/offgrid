package com.example.reliableudp

/**
 * Represents the lifecycle state of a ReliableSocket.
 */
sealed class SocketState {
    /** Socket has not been bound to a port */
    object Unbound : SocketState() {
        override fun toString() = "Unbound"
    }

    /** Socket is in the process of binding */
    object Binding : SocketState() {
        override fun toString() = "Binding"
    }

    /** Socket is bound and ready for communication */
    data class Bound(val port: Int) : SocketState() {
        override fun toString() = "Bound(port=$port)"
    }

    /** Socket encountered an error */
    data class Error(val message: String) : SocketState() {
        override fun toString() = "Error($message)"
    }

    /** Socket has been closed */
    object Closed : SocketState() {
        override fun toString() = "Closed"
    }
}
