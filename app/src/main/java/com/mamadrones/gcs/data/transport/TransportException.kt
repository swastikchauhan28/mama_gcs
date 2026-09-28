package com.mamadrones.gcs.data.transport

sealed class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : TransportException("Transport is not connected")
    class UnsupportedTransport(name: String) : TransportException("$name is not implemented in this phase")
    class ConnectionFailed(cause: Throwable) : TransportException("Unable to establish transport connection", cause)
}
