package com.mamadrones.gcs.domain.model

/** A configured UDP peer. Opening this endpoint does not establish vehicle identity or health. */
data class UdpEndpoint(
    val remoteHost: String,
    val remotePort: Int = DEFAULT_PORT,
    val localPort: Int = DEFAULT_PORT
) {
    init {
        require(remoteHost.isNotBlank()) { "UDP remote host must not be blank" }
        require(remoteHost == remoteHost.trim()) { "UDP remote host must not have leading or trailing whitespace" }
        require(remotePort in MIN_PORT..MAX_PORT) { "UDP remote port must be between $MIN_PORT and $MAX_PORT" }
        require(localPort in 0..MAX_PORT) { "UDP local port must be between 0 and $MAX_PORT" }
    }

    val displayName: String get() = "$remoteHost:$remotePort"

    companion object {
        const val DEFAULT_PORT = 14_550
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
    }
}
