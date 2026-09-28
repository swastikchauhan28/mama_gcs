package com.mamadrones.gcs.data.transport.udp

/** User-configurable UDP endpoints. No connection occurs until [UdpTransport.connect]. */
data class UdpTransportConfig(
    val remoteHost: String,
    val remotePort: Int = DEFAULT_MAVLINK_GCS_PORT,
    val localPort: Int = DEFAULT_MAVLINK_GCS_PORT,
    val receiveBufferBytes: Int = DEFAULT_RECEIVE_BUFFER_BYTES,
    val socketReadTimeoutMillis: Int = DEFAULT_SOCKET_READ_TIMEOUT_MILLIS
) {
    init {
        require(remoteHost.isNotBlank()) { "UDP remote host must not be blank" }
        require(remotePort in 1..MAX_PORT) { "UDP remote port must be between 1 and $MAX_PORT" }
        require(localPort in 0..MAX_PORT) { "UDP local port must be between 0 and $MAX_PORT" }
        require(receiveBufferBytes > 0) { "Receive buffer must be positive" }
        require(socketReadTimeoutMillis > 0) { "Socket read timeout must be positive" }
    }

    companion object {
        const val DEFAULT_MAVLINK_GCS_PORT = 14550
        const val DEFAULT_RECEIVE_BUFFER_BYTES = 2_048
        const val DEFAULT_SOCKET_READ_TIMEOUT_MILLIS = 1_000
        private const val MAX_PORT = 65_535
    }
}
