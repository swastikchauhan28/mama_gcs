package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class HomePositionParserTest {
    // MAVLink generated HOME_POSITION header: ID 242, CRC 104, base 52 / maximum 60.
    private fun payload() = ByteArray(60).apply {
        putUInt32(0, -353632621L); putUInt32(4, 1491652374); putUInt32(8, -12345)
        for (i in 12 until size) this[i] = 0x55
    }
    private fun decode(frame: ByteArray) = (MavlinkParser().feed(frame).single() as MavlinkParseResult.Message)
        .message as MavlinkMessage.HomePosition

    @Test fun bothVersionsUseSignedGeographicFieldsNotLocalOffsets() {
        val v1 = decode(mavlink1TestFrame(242, payload().copyOf(52), 104, 42, 1))
        val v2 = decode(mavlinkTestFrame(242, payload(), 104, 42, 1))
        assertEquals(v1, v2)
        assertEquals(MavlinkMessage.HomePosition(42, 1, -353632621, 1491652374, -12345), v2)
    }
    @Test fun localCoordinatesOrientationAndTimestampAreNotRetained() {
        val other = payload().apply { for (i in 12 until size) this[i] = 0x33 }
        assertEquals(decode(mavlinkTestFrame(242, payload(), 104)), decode(mavlinkTestFrame(242, other, 104)))
    }
    @Test fun truncatedV2RestoresZeroFieldsWithoutTreatingZeroCoordinatesAsMissing() {
        assertEquals(MavlinkMessage.HomePosition(1, 1, 0, 0, 0), decode(mavlinkTestFrame(242, byteArrayOf(0), 104)))
        assertEquals(7, decode(mavlinkTestFrame(242, ByteArray(9).apply { this[8] = 7 }, 104)).altitudeMillimetersMsl)
    }
    @Test fun payloadBoundsAreVersionSpecific() {
        listOf(0, 51, 53, 60).forEach {
            assertTrue(MavlinkParser().feed(mavlink1TestFrame(242, ByteArray(it), 104)).single() is MavlinkParseResult.MalformedMessage)
        }
        listOf(0, 61).forEach {
            assertTrue(MavlinkParser().feed(mavlinkTestFrame(242, ByteArray(it), 104)).single() is MavlinkParseResult.MalformedMessage)
        }
    }
    @Test fun corruptedSignedAndUnsupportedFlagPacketsAreRejected() {
        val packet = mavlinkTestFrame(242, payload(), 104)
        assertTrue(MavlinkParser().feed(packet.copyOf().apply { this[10] = (this[10].toInt() xor 1).toByte() })
            .first() is MavlinkParseResult.InvalidChecksum)
        assertEquals(MavlinkParseResult.SignedPacketRejected,
            MavlinkParser().feed(packet.copyOf().apply { this[2] = 1 } + ByteArray(13)).single())
        assertTrue(MavlinkParser().feed(packet.copyOf().apply { this[2] = 2 }).single() is MavlinkParseResult.UnsupportedIncompatibilityFlags)
    }
    @Test fun fragmentedMixedVersionStreamRetainsOrdering() {
        val one = mavlink1TestFrame(242, payload().copyOf(52), 104)
        val parser = MavlinkParser()
        assertTrue(parser.feed(one.copyOfRange(0, 15)).isEmpty())
        val results = parser.feed(one.copyOfRange(15, one.size) + mavlinkTestFrame(242, payload(), 104))
            .map { it as MavlinkParseResult.Message }
        assertEquals(listOf(MavlinkWireVersion.V1, MavlinkWireVersion.V2), results.map { it.wireVersion })
        assertEquals(results[0].message, results[1].message)
    }
}
