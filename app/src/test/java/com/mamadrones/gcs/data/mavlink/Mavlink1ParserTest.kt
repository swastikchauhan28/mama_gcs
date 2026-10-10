package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class Mavlink1ParserTest {
    @Test fun fixedHeartbeatVectorDoesNotUseProductionChecksumToBuildInput() {
        // Fixed wire vector with CRC independently calculated using bitwise CRC-16/MCRF4XX.
        val bytes = "fe 09 07 2a 01 00 0a 00 00 00 0a 03 80 04 03 39 cf"
            .split(' ').map { it.toInt(16).toByte() }.toByteArray()
        val result = MavlinkParser().feed(bytes).single() as MavlinkParseResult.Message
        assertEquals(MavlinkWireVersion.V1, result.wireVersion)
        val heartbeat = result.message as MavlinkMessage.Heartbeat
        assertEquals(42, heartbeat.systemId)
        assertEquals(10L, heartbeat.customMode)
        assertTrue(heartbeat.isArmed)
    }
    @Test fun everySplitOfHeartbeatPreservesIdentityAndWireVersion() {
        val frame = heartbeat1Frame(systemId = 42)
        for (split in 1 until frame.size) {
            val parser = MavlinkParser()
            assertTrue(parser.feed(frame.copyOfRange(0, split)).isEmpty())
            val result = parser.feed(frame.copyOfRange(split, frame.size)).single() as MavlinkParseResult.Message
            assertEquals(MavlinkWireVersion.V1, result.wireVersion)
            val heartbeat = result.message as MavlinkMessage.Heartbeat
            assertEquals(42, heartbeat.systemId)
            assertEquals(1, heartbeat.componentId)
            // This payload field is NOT the over-the-wire framing version.
            assertEquals(3, heartbeat.mavlinkVersion)
        }
    }

    @Test fun acceptsAllSupportedBaseMessagesAndMatchesVersion2Fields() {
        val definitions = listOf(Triple(0, 9, 50), Triple(1, 31, 124), Triple(24, 30, 24),
            Triple(30, 28, 39), Triple(33, 28, 104), Triple(74, 20, 20), Triple(147, 36, 154), Triple(253, 51, 83))
        definitions.forEach { (id, size, extra) ->
            val payload = ByteArray(size)
            val v1 = MavlinkParser().feed(mavlink1TestFrame(id, payload, extra)).single() as MavlinkParseResult.Message
            val v2 = MavlinkParser().feed(mavlinkTestFrame(id, payload, extra)).single() as MavlinkParseResult.Message
            assertEquals(MavlinkWireVersion.V1, v1.wireVersion)
            assertEquals(MavlinkWireVersion.V2, v2.wireVersion)
            if (v1.message is MavlinkMessage.StatusText) {
                assertArrayEquals(v1.message.textChunk, (v2.message as MavlinkMessage.StatusText).textChunk)
            } else assertEquals(v2.message, v1.message)
        }
    }

    @Test fun rejectsTruncationAndExtensionBytesInVersion1() {
        listOf(Triple(0, 9, 50), Triple(1, 31, 124), Triple(24, 30, 24), Triple(30, 28, 39),
            Triple(33, 28, 104), Triple(74, 20, 20), Triple(147, 36, 154), Triple(253, 51, 83)).forEach { (id, size, extra) ->
            listOf(0, size - 1, size + 1).forEach { wrong ->
                assertEquals(MavlinkParseResult.MalformedMessage(id),
                    MavlinkParser().feed(mavlink1TestFrame(id, ByteArray(wrong), extra)).single())
            }
        }
    }

    @Test fun decodesMixedVersionsByteByByteWithNoise() {
        val input = byteArrayOf(1, 2, 3) + heartbeat1Frame() + heartbeatFrame() + heartbeat1Frame()
        val parser = MavlinkParser()
        val results = input.flatMap { parser.feed(byteArrayOf(it)) }.filterIsInstance<MavlinkParseResult.Message>()
        assertEquals(listOf(MavlinkWireVersion.V1, MavlinkWireVersion.V2, MavlinkWireVersion.V1), results.map { it.wireVersion })
    }

    @Test fun sequenceBytesAreNotTreatedAsVersion2Flags() {
        for (sequence in listOf(1, 2, 0xFD, 0xFE, 255)) {
            val frame = mavlink1TestFrame(0, byteArrayOf(0, 0, 0, 0, 10, 3, 0, 4, 3), 50, sequence = sequence)
            assertTrue(MavlinkParser().feed(frame).single() is MavlinkParseResult.Message)
        }
    }

    @Test fun crcCorruptionCannotUpdateTelemetryAndNextFrameIsRecovered() {
        val corrupt = heartbeat1Frame().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val results = MavlinkParser().feed(corrupt + heartbeatFrame())
        assertTrue(results.first() is MavlinkParseResult.InvalidChecksum)
        assertEquals(MavlinkWireVersion.V2, results.filterIsInstance<MavlinkParseResult.Message>().single().wireVersion)
    }

    @Test fun corruptLengthRecoversAfterCandidateCompletes() {
        val corrupt = byteArrayOf(0xFE.toByte(), 255.toByte(), 0, 1, 1, 0)
        val results = MavlinkParser().feed(corrupt + heartbeat1Frame() + ByteArray(280))
        assertEquals(1, results.filterIsInstance<MavlinkParseResult.Message>().size)
    }

    @Test fun resetDropsPartialVersion1Frame() {
        val parser = MavlinkParser()
        val frame = heartbeat1Frame()
        parser.feed(frame.copyOfRange(0, 5))
        parser.reset()
        assertTrue(parser.feed(frame.copyOfRange(5, frame.size)).isEmpty())
        assertEquals(1, parser.feed(frame).filterIsInstance<MavlinkParseResult.Message>().size)
    }

    @Test fun version1HasNoBatteryOrStatusTextExtensions() {
        val battery = (MavlinkParser().feed(mavlink1TestFrame(147, ByteArray(36), 154)).single() as MavlinkParseResult.Message).message as MavlinkMessage.BatteryStatus
        assertNull(battery.chargeState)
        assertEquals(10, battery.cellVoltagesMillivolts.size)
        val status = (MavlinkParser().feed(mavlink1TestFrame(253, ByteArray(51), 83)).single() as MavlinkParseResult.Message).message as MavlinkMessage.StatusText
        assertEquals(0, status.id)
        assertEquals(0, status.chunkSequence)
    }

    @Test fun signedVersion2CannotSmuggleEmbeddedVersion1Frame() {
        val payload = heartbeat1Frame()
        val signed = mavlinkTestFrame(253, payload, 83).also { it[2] = 1 } + ByteArray(13)
        val results = MavlinkParser().feed(signed + heartbeat1Frame())
        assertEquals(MavlinkParseResult.SignedPacketRejected, results.first())
        assertEquals(1, results.filterIsInstance<MavlinkParseResult.Message>().size)
    }

    @Test fun unsupportedVersion1MessageIsNotDecoded() {
        assertEquals(MavlinkParseResult.UnsupportedMessage(200),
            MavlinkParser().feed(mavlink1TestFrame(200, ByteArray(4), 0)).single())
    }
}
