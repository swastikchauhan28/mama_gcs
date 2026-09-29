package com.mamadrones.gcs.data.transport.udp

/** User-configurable UDP socket details. No connection occurs until [UdpTransport.connect]. */
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
        require(receiveBufferBytes in 1..MAX_UDP_PAYLOAD_BYTES) { "Receive buffer must be between 1 and $MAX_UDP_PAYLOAD_BYTES bytes" }
        require(socketReadTimeoutMillis > 0) { "Socket read timeout must be positive" }
    }

    companion object {
        const val MAX_UDP_PAYLOAD_BYTES = 65_507
        const val DEFAULT_MAVLINK_GCS_PORT = 14550
        /** A complete UDP payload so byte-oriented consumers never receive a silently truncated packet. */
        const val DEFAULT_RECEIVE_BUFFER_BYTES = MAX_UDP_PAYLOAD_BYTES
        const val DEFAULT_SOCKET_READ_TIMEOUT_MILLIS = 1_000
        private const val MAX_PORT = 65_535
    }
}
