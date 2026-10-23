package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class RadioStatusParserTest {
    private fun payload() = ByteArray(9).apply {
        putUInt16(0, 17)
        putUInt16(2, 23)
        this[4] = 95.toByte()
        this[5] = 100.toByte()
        this[6] = 80.toByte()
        this[7] = 4
        this[8] = 5
    }

    @Test fun `v1 and v2 radio frames are validated but never vehicle messages`() {
        val frames = listOf(
            mavlink1TestFrame(109, payload(), 185, systemId = 68, componentId = 68),
            mavlinkTestFrame(109, payload(), 185, systemId = 68, componentId = 68),
        )
        frames.forEachIndexed { index, bytes ->
            val result = MavlinkParser().feed(bytes).single() as MavlinkParseResult.RadioStatus
            assertEquals(68, result.systemId)
            assertEquals(68, result.componentId)
            assertEquals(17, result.rxErrors)
            assertEquals(23, result.fixedPackets)
            assertEquals(95, result.rssi)
            assertEquals(100, result.remoteRssi)
            assertEquals(80, result.txBufferPercent)
            assertEquals(4, result.noise)
            assertEquals(5, result.remoteNoise)
            assertEquals(if (index == 0) MavlinkWireVersion.V1 else MavlinkWireVersion.V2, result.wireVersion)
        }
    }

    @Test fun `bad CRC and wrong payload length cannot become radio observations`() {
        val valid = mavlink1TestFrame(109, payload(), 185)
        val corrupt = valid.copyOf().apply { this[8] = (this[8].toInt() xor 1).toByte() }
        assertTrue(MavlinkParser().feed(corrupt).none { it is MavlinkParseResult.RadioStatus })
        assertTrue(MavlinkParser().feed(corrupt).any { it is MavlinkParseResult.InvalidChecksum })
        assertEquals(MavlinkParseResult.MalformedMessage(109),
            MavlinkParser().feed(mavlink1TestFrame(109, ByteArray(8), 185)).single())
        assertEquals(MavlinkParseResult.MalformedMessage(109),
            MavlinkParser().feed(mavlinkTestFrame(109, ByteArray(10), 185)).single())
    }

    @Test fun `v2 zero suffix may be truncated and signed candidate remains rejected`() {
        val short = mavlinkTestFrame(109, byteArrayOf(1), 185)
        val decoded = MavlinkParser().feed(short).single() as MavlinkParseResult.RadioStatus
        assertEquals(1, decoded.rxErrors)
        assertEquals(0, decoded.remoteNoise)
        val signed = mavlinkTestFrame(109, payload(), 185).copyOf().apply { this[2] = 1 } + ByteArray(13)
        assertEquals(MavlinkParseResult.SignedPacketRejected, MavlinkParser().feed(signed).single())
    }
}
