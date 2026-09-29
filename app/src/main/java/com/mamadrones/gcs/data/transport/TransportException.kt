package com.mamadrones.gcs.data.transport

sealed class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : TransportException("Transport is not connected")
    class Closed : TransportException("Transport has been closed")
    class AlreadyConnected : TransportException("A different transport is already open")
    class PacketTooLarge(size: Int) : TransportException("UDP packet is $size bytes; the maximum is 65,507 bytes")
    class UnsupportedTransport(name: String) : TransportException("$name is not implemented in this phase")
    class ConnectionFailed(cause: Throwable) : TransportException("Unable to establish transport connection", cause)
}
