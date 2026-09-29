package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.assertEquals
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

    private fun heartbeatFrame(customMode: Long, baseMode: Int): ByteArray {
        val payload = byteArrayOf(
            customMode.toByte(), (customMode shr 8).toByte(), (customMode shr 16).toByte(), (customMode shr 24).toByte(),
            10, 3, baseMode.toByte(), 4, 3
        )
        val frameWithoutChecksum = byteArrayOf(0xFD.toByte(), 9, 0, 0, 7, 1, 1, 0, 0, 0) + payload
        val checksum = MavlinkChecksum.calculate(frameWithoutChecksum.copyOfRange(1, frameWithoutChecksum.size), 50)
        return frameWithoutChecksum + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
    }
}
