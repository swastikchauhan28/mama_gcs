package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class RcPwmParserTest {
    @Test fun rcWireOffsetsAndUnsignedFieldsWorkInBothVersions() {
        val payload = ByteArray(42).apply {
            putUInt32(0, 0xFEDCBA98L)
            repeat(18) { putUInt16(4 + it * 2, 1001 + it * 51) }
            putUInt16(38, 65535)
            this[40] = 24; this[41] = 254.toByte()
        }
        listOf(mavlinkTestFrame(65, payload, 118), mavlink1TestFrame(65, payload, 118)).forEach { bytes ->
            val message = (MavlinkParser().feed(bytes).single() as MavlinkParseResult.Message).message as MavlinkMessage.RcChannels
            assertEquals(24, message.channelCount)
            assertEquals(254, message.rssi)
            assertEquals((0..16).map { 1001 + it * 51 } + 65535, message.channelsMicroseconds)
        }
    }

    @Test fun servoPortAndAllOutputOffsetsAreIndependent() {
        val payload = ByteArray(37).apply {
            putUInt32(0, 0xFFEEDDCCL)
            repeat(8) { putUInt16(4 + it * 2, 1100 + it) }
            this[20] = 200.toByte()
            repeat(8) { putUInt16(21 + it * 2, 2100 + it) }
        }
        val message = servo(mavlinkTestFrame(36, payload, 222))
        assertEquals(200, message.port)
        assertEquals((0..7).map { 1100 + it } + (0..7).map { 2100 + it }, message.outputsMicroseconds)
        val version1 = servo(mavlink1TestFrame(36, payload.copyOf(21), 222))
        assertEquals((0..7).map { 1100 + it } + List<Int?>(8) { null }, version1.outputsMicroseconds)
    }

    @Test fun partialExtensionRestoresHighByteButAbsentExtensionsRemainUnknown() {
        val payload = ByteArray(22).apply { putUInt16(4, 1500); this[21] = 201.toByte() }
        val message = servo(mavlinkTestFrame(36, payload, 222))
        assertEquals(201, message.outputsMicroseconds[8])
        assertNull(message.outputsMicroseconds[9])
        assertEquals(0, message.outputsMicroseconds[1])
    }

    @Test fun version2RestoresTruncatedBaseAndDoesNotInventRcChannels() {
        val rc = (MavlinkParser().feed(mavlinkTestFrame(65, byteArrayOf(1), 118)).single() as MavlinkParseResult.Message)
            .message as MavlinkMessage.RcChannels
        assertEquals(0, rc.channelCount)
        assertEquals(List(18) { 0 }, rc.channelsMicroseconds)
        val output = servo(mavlinkTestFrame(36, byteArrayOf(1), 222))
        assertEquals(0, output.port)
        assertEquals(List(8) { 0 } + List<Int?>(8) { null }, output.outputsMicroseconds)
    }

    @Test fun lengthsCrcAndSignedPolicyAreEnforced() {
        val malformed = listOf(mavlink1TestFrame(65, ByteArray(41), 118),
            mavlink1TestFrame(36, ByteArray(22), 222), mavlinkTestFrame(65, ByteArray(43), 118),
            mavlinkTestFrame(36, ByteArray(38), 222))
        malformed.forEach { assertTrue(MavlinkParser().feed(it).single() is MavlinkParseResult.MalformedMessage) }
        val valid = mavlinkTestFrame(65, ByteArray(42), 118)
        val corrupt = valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(MavlinkParser().feed(corrupt).first() is MavlinkParseResult.InvalidChecksum)
        val signed = valid.copyOf().apply { this[2] = 1 } + ByteArray(13)
        assertEquals(MavlinkParseResult.SignedPacketRejected, MavlinkParser().feed(signed).single())
    }

    @Test fun fragmentedMixedMessagesRetainWireVersionAndOrder() {
        val bytes = mavlink1TestFrame(36, ByteArray(21), 222) + mavlinkTestFrame(65, ByteArray(42), 118)
        val parser = MavlinkParser()
        val results = bytes.flatMap { parser.feed(byteArrayOf(it)) }.map { it as MavlinkParseResult.Message }
        assertEquals(listOf(MavlinkWireVersion.V1, MavlinkWireVersion.V2), results.map { it.wireVersion })
        assertTrue(results[0].message is MavlinkMessage.ServoOutputRaw)
        assertTrue(results[1].message is MavlinkMessage.RcChannels)
    }

    private fun servo(bytes: ByteArray) = (MavlinkParser().feed(bytes).single() as MavlinkParseResult.Message)
        .message as MavlinkMessage.ServoOutputRaw
}
