package com.mamadrones.gcs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TcpSocketTimelineTest {
    @Test fun `records status changes once and rejects invalid elapsed ordering`() {
        var events = TcpSocketTimeline.record(emptyList(), TransportStatus.CONNECTING, 100)
        events = TcpSocketTimeline.record(events, TransportStatus.CONNECTING, 101)
        events = TcpSocketTimeline.record(events, TransportStatus.OPEN, 99)
        events = TcpSocketTimeline.record(events, TransportStatus.OPEN, 110)
        events = TcpSocketTimeline.record(events, TransportStatus.ERROR, 120)
        events = TcpSocketTimeline.record(events, TransportStatus.DISCONNECTED, 120)
        assertEquals(listOf(TransportStatus.CONNECTING, TransportStatus.OPEN, TransportStatus.ERROR,
            TransportStatus.DISCONNECTED), events.map { it.status })
        assertEquals(listOf(100L, 110L, 120L, 120L), events.map { it.atElapsedMillis })
    }

    @Test fun `retains only eight transitions and a new attempt starts empty`() {
        var events = emptyList<TcpSocketTransition>()
        repeat(10) { index ->
            events = TcpSocketTimeline.record(events,
                if (index % 2 == 0) TransportStatus.CONNECTING else TransportStatus.OPEN, index.toLong())
        }
        assertEquals(TcpSocketTimeline.MAX_TRANSITIONS, events.size)
        assertEquals(2L, events.first().atElapsedMillis)
        val newAttempt = TcpSocketTimeline.record(emptyList(), TransportStatus.CONNECTING, 20)
        assertEquals(1, newAttempt.size)
        assertEquals(20L, newAttempt.single().atElapsedMillis)
    }
}
