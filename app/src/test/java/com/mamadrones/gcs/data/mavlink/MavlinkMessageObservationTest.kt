package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MavlinkMessageObservationTest {
    @Test fun versionsAndDispositionsHaveSeparateCountsAndTimes() {
        val accumulator = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.CLASSIC, 100)
        val v1 = MavlinkParser().feed(heartbeat1Frame()).single()
        val v2 = MavlinkParser().feed(heartbeatFrame()).single()
        accumulator.recordResult(v1, MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT, 110)
        accumulator.recordResult(v2, MessageDisposition.ACCEPTED, 120)
        accumulator.recordResult(v1, MessageDisposition.OTHER_SOURCE, 130)
        val row = accumulator.snapshot.messageObservations.single()
        assertEquals(ObservedMavlinkMessage.HEARTBEAT, row.type)
        assertEquals(3L, row.decoded)
        assertEquals(2L, row.decodedV1)
        assertEquals(1L, row.decodedV2)
        assertEquals(1L, row.accepted)
        assertEquals(1L, row.beforeHeartbeat)
        assertEquals(1L, row.otherSource)
        assertEquals(130L, row.lastDecodedAtEpochMillis)
        assertEquals(120L, row.lastAcceptedAtEpochMillis)
    }

    @Test fun everySupportedWireIdMapsToItsOwnRow() {
        val accumulator = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.UDP, 100)
        // ID, base payload size, CRC extra: exercised through the real frame decoder.
        val frames = listOf(Triple(0, 9, 50), Triple(1, 31, 124), Triple(24, 30, 24),
            Triple(30, 28, 39), Triple(33, 28, 104), Triple(36, 21, 222), Triple(65, 42, 118),
            Triple(74, 20, 20), Triple(147, 36, 154), Triple(148, 60, 178), Triple(242, 52, 104), Triple(253, 51, 83))
        frames.reversed().forEach { (id, length, crc) ->
            val result = MavlinkParser().feed(mavlinkTestFrame(id, ByteArray(length), crc)).single()
            assertTrue("ID $id must decode", result is MavlinkParseResult.Message)
            accumulator.recordResult(result, MessageDisposition.ACCEPTED, 110)
        }
        val rows = accumulator.snapshot.messageObservations
        assertEquals(ObservedMavlinkMessage.entries, rows.map { it.type })
        assertEquals(frames.map { it.first }, rows.map { it.type.messageId })
        assertTrue(rows.all { it.accepted == 1L && it.decoded == 1L })
    }

    @Test fun rawChunksAndAllRejectedFramesCannotCreateRows() {
        val accumulator = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.BLE, 100)
        accumulator.recordBytes(500, 110)
        listOf(MavlinkParseResult.InvalidChecksum(0), MavlinkParseResult.MalformedMessage(74),
            MavlinkParseResult.UnsupportedMessage(999), MavlinkParseResult.UnsupportedIncompatibilityFlags(2),
            MavlinkParseResult.SignedPacketRejected).forEach { accumulator.recordResult(it, null, 110) }
        assertTrue(accumulator.snapshot.messageObservations.isEmpty())
    }

    @Test fun oldSnapshotsRemainImmutableAndRepeatedMessagesDoNotGrowTheList() {
        val accumulator = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.CLASSIC, 100)
        val heartbeat = MavlinkParser().feed(heartbeatFrame()).single()
        accumulator.recordResult(heartbeat, MessageDisposition.ACCEPTED, 110)
        val old = accumulator.snapshot
        repeat(1_000) { accumulator.recordResult(heartbeat, MessageDisposition.OTHER_SOURCE, 120) }
        assertEquals(1, accumulator.snapshot.messageObservations.size)
        assertEquals(1L, old.messageObservations.single().decoded)
        assertEquals(1_001L, accumulator.snapshot.messageObservations.single().decoded)
        assertEquals(110L, accumulator.snapshot.messageObservations.single().lastAcceptedAtEpochMillis)
    }

    @Test fun filteredOnlyAndNewSessionNeverInventAnAcceptedReceipt() {
        val accumulator = MavlinkDiagnosticsAccumulator(TelemetryLinkKind.UDP, 100)
        accumulator.recordResult(MavlinkParser().feed(heartbeatFrame()).single(), MessageDisposition.OTHER_SOURCE, 110)
        assertEquals(0L, accumulator.snapshot.messageObservations.single().accepted)
        assertNull(accumulator.snapshot.messageObservations.single().lastAcceptedAtEpochMillis)
        assertTrue(MavlinkDiagnosticsAccumulator(TelemetryLinkKind.CLASSIC, 200).snapshot.messageObservations.isEmpty())
    }
}
