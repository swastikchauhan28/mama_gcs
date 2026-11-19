package com.mamadrones.gcs.domain.model

/** Volatile observed TCP state only; no endpoint, error text or vehicle identity is retained. */
data class TcpSocketTransition(val status: TransportStatus, val atElapsedMillis: Long)

object TcpSocketTimeline {
    const val MAX_TRANSITIONS = 8

    fun record(current: List<TcpSocketTransition>, status: TransportStatus, atElapsedMillis: Long): List<TcpSocketTransition> {
        if (atElapsedMillis < 0 || current.lastOrNull()?.status == status ||
            (current.lastOrNull()?.atElapsedMillis ?: 0L) > atElapsedMillis) return current
        return (current + TcpSocketTransition(status, atElapsedMillis)).takeLast(MAX_TRANSITIONS)
    }
}
