package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkParserTest {
    @Test
    fun `parses a valid MAVLink 2 armed Rover heartbeat across chunks`() {
        val parser = MavlinkParser()
        val frame = heartbeatFrame(customMode = 10, baseMode = 128)

        assertTrue(parser.feed(frame.copyOfRange(0, 6)).isEmpty())
        val result = parser.feed(frame.copyOfRange(6, frame.size)).single() as MavlinkParseResult.Message
        val heartbeat = result.message as MavlinkMessage.Heartbeat

        assertEquals(1, heartbeat.systemId)
        assertEquals(1, heartbeat.componentId)
        assertEquals(10L, heartbeat.customMode)
        assertTrue(heartbeat.isArmed)
    }

    @Test
    fun `rejects a heartbeat with an invalid checksum`() {
        val frame = heartbeatFrame(customMode = 0, baseMode = 0)
        frame[frame.lastIndex] = (frame.last().toInt() xor 0x01).toByte()

        val result = MavlinkParser().feed(frame).single()

        assertEquals(MavlinkParseResult.InvalidChecksum(0), result)
    }

    @Test
    fun `rejects signed frames when signature verification is not configured`() {
        val unsigned = heartbeatFrame(customMode = 0, baseMode = 0)
        unsigned[2] = 1
        val crcStart = 1
        val crcEndExclusive = unsigned.size - 2
        val checksum = MavlinkChecksum.calculate(unsigned.copyOfRange(crcStart, crcEndExclusive), 50)
        unsigned[unsigned.lastIndex - 1] = checksum.toByte()
        unsigned[unsigned.lastIndex] = (checksum shr 8).toByte()
        val signed = unsigned + ByteArray(13)

        assertEquals(MavlinkParseResult.SignedPacketRejected, MavlinkParser().feed(signed).single())
    }

    @Test
    fun `rejects unknown incompatibility flags`() {
        val frame = heartbeatFrame(customMode = 0, baseMode = 0)
        frame[2] = 0x02

        assertEquals(MavlinkParseResult.UnsupportedIncompatibilityFlags(0x02), MavlinkParser().feed(frame).single())
    }

    @Test
    fun `does not let a corrupt length consume a following valid heartbeat`() {
        val oversizedCandidate = byteArrayOf(0xFD.toByte(), 0xFF.toByte(), 0, 0, 0, 1, 1, 0, 0, 0)
        val valid = heartbeatFrame(customMode = 3, baseMode = 0)
        val bytes = oversizedCandidate + valid + ByteArray(280)

        val result = MavlinkParser().feed(bytes).filterIsInstance<MavlinkParseResult.Message>().single()

        assertEquals(3L, (result.message as MavlinkMessage.Heartbeat).customMode)
    }

    @Test
    fun `reset discards an incomplete frame between sessions`() {
        val parser = MavlinkParser()
        val heartbeat = heartbeatFrame(customMode = 0, baseMode = 0)
        parser.feed(heartbeat.copyOfRange(0, 5))
        parser.reset()

        assertTrue(parser.feed(heartbeat.copyOfRange(5, heartbeat.size)).isEmpty())
        assertFalse(parser.feed(heartbeat).isEmpty())
    }

    internal fun heartbeatFrame(
        customMode: Long,
        baseMode: Int,
        systemId: Int = 1,
        componentId: Int = 1,
        autopilot: Int = 3
    ): ByteArray {
        val payload = byteArrayOf(
            customMode.toByte(), (customMode shr 8).toByte(), (customMode shr 16).toByte(), (customMode shr 24).toByte(),
            10, autopilot.toByte(), baseMode.toByte(), 4, 3
        )
        val frameWithoutChecksum = byteArrayOf(0xFD.toByte(), 9, 0, 0, 7, systemId.toByte(), componentId.toByte(), 0, 0, 0) + payload
        val checksum = MavlinkChecksum.calculate(frameWithoutChecksum.copyOfRange(1, frameWithoutChecksum.size), 50)
        return frameWithoutChecksum + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
    }
}
