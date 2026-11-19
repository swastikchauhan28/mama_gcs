package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TcpSocketTransition
import com.mamadrones.gcs.domain.model.TransportStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TcpTimelineUiStateTest {
    @Test fun `normal remote close does not invent socket error`() {
        val before = TcpUiState(active = true, connection = ConnectionState(TransportStatus.DISCONNECTED,
            "TCP server closed the connection."), socketTransitions = listOf(
            TcpSocketTransition(TransportStatus.OPEN, 100), TcpSocketTransition(TransportStatus.DISCONNECTED, 200)))
        val after = before.afterDisconnect("TCP server closed the connection.", 201)
        assertEquals(listOf(TransportStatus.OPEN, TransportStatus.DISCONNECTED),
            after.socketTransitions.map { it.status })
        assertEquals(false, after.active)
    }

    @Test fun `failed connect records error then closed and next attempt resets`() {
        val before = TcpUiState(active = true, connection = ConnectionState(TransportStatus.CONNECTING),
            socketTransitions = listOf(TcpSocketTransition(TransportStatus.CONNECTING, 100)))
        val after = before.afterDisconnect("Connection failed", 200)
        assertEquals(listOf(TransportStatus.CONNECTING, TransportStatus.ERROR, TransportStatus.DISCONNECTED),
            after.socketTransitions.map { it.status })
        assertEquals(1, com.mamadrones.gcs.domain.model.TcpSocketTimeline.record(emptyList(),
            TransportStatus.CONNECTING, 300).size)
    }
}
