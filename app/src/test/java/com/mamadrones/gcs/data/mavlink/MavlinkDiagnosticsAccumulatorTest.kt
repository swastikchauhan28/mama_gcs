package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import org.junit.Assert.*
import org.junit.Test

class MavlinkDiagnosticsAccumulatorTest {
    @Test fun `version counts use decoded frames including filtered sources`() {
        val counters = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.CLASSIC, 100)
        counters.recordResult(MavlinkParser().feed(heartbeat1Frame()).single(), MessageDisposition.ACCEPTED, 110)
        counters.recordResult(MavlinkParser().feed(heartbeatFrame()).single(), MessageDisposition.OTHER_SOURCE, 120)
        counters.recordResult(MavlinkParseResult.InvalidChecksum(0), null, 130)
        counters.recordResult(MavlinkParseResult.SignedPacketRejected, null, 130)
        assertEquals(1L, counters.snapshot.decodedV1Messages)
        assertEquals(1L, counters.snapshot.decodedV2Messages)
        assertEquals(2L, counters.snapshot.decodedMessages)
        assertEquals(1L, counters.snapshot.acceptedMessages)
        val reset = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.CLASSIC, 200).snapshot
        assertEquals(0L, reset.decodedV1Messages)
        assertEquals(0L, reset.decodedV2Messages)
    }
    @Test fun `chunks are not messages and empty input is not counted`() {
        val counters = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.BLE, 100)
        counters.recordBytes(0, 110)
        assertEquals(0L, counters.snapshot.receivedChunks)
        assertNull(counters.snapshot.lastBytesAtEpochMillis)
        counters.recordBytes(3, 120)
        counters.recordBytes(18, 130)
        assertEquals(2L, counters.snapshot.receivedChunks)
        assertEquals(21L, counters.snapshot.receivedBytes)
        assertEquals(0L, counters.snapshot.decodedMessages)
        assertEquals(0L, counters.snapshot.parserErrors)
        assertEquals(130L, counters.snapshot.lastBytesAtEpochMillis)
        assertThrows(IllegalArgumentException::class.java) { counters.recordBytes(-1, 140) }
    }

    @Test fun `decoded messages distinguish admission from source filtering`() {
        val counters = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.UDP, 100)
        val heartbeat = MavlinkParser().feed(heartbeatFrame()).single()
        counters.recordResult(heartbeat, MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT, 110)
        counters.recordResult(heartbeat, MessageDisposition.OTHER_SOURCE, 120)
        assertNull(counters.snapshot.lastAcceptedAtEpochMillis)
        counters.recordResult(heartbeat, MessageDisposition.ACCEPTED, 130)
        assertEquals(3L, counters.snapshot.decodedMessages)
        assertEquals(1L, counters.snapshot.acceptedMessages)
        assertEquals(1L, counters.snapshot.ignoredBeforeHeartbeat)
        assertEquals(1L, counters.snapshot.ignoredOtherSource)
        assertEquals(130L, counters.snapshot.lastAcceptedAtEpochMillis)
    }

    @Test fun `each rejection has its own counter without implying valid messages`() {
        val counters = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.BLE, 100)
        listOf(MavlinkParseResult.InvalidChecksum(0), MavlinkParseResult.MalformedMessage(74),
            MavlinkParseResult.UnsupportedMessage(999), MavlinkParseResult.UnsupportedIncompatibilityFlags(2),
            MavlinkParseResult.SignedPacketRejected).forEach { counters.recordResult(it, null, 110) }
        val state = counters.snapshot
        assertEquals(1L, state.checksumFailures)
        assertEquals(1L, state.malformedPayloads)
        assertEquals(2L, state.parserErrors)
        assertEquals(1L, state.unsupportedMessages)
        assertEquals(999, state.lastUnsupportedMessageId)
        assertEquals(1L, state.unsupportedFlags)
        assertEquals(1L, state.signedPacketsRejected)
        assertEquals(0L, state.decodedMessages)
        assertEquals(0L, state.acceptedMessages)
    }

    @Test fun `new accumulator resets old session observations`() {
        val previous = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.BLE, 100)
        previous.recordBytes(64, 110)
        previous.recordResult(MavlinkParseResult.UnsupportedMessage(999), null, 110)
        val next = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.UDP, 200).snapshot
        assertTrue(next.active)
        assertEquals(TelemetryLinkKind.UDP, next.linkKind)
        assertEquals(200L, next.startedAtEpochMillis)
        assertEquals(0L, next.receivedBytes)
        assertEquals(0L, next.unsupportedMessages)
        assertNull(next.lastUnsupportedMessageId)
    }
}
