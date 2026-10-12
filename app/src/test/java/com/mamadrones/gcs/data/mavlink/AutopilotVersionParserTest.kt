package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class AutopilotVersionParserTest {
    // Wire offsets/length/CRC-extra checked against MAVLink's generated standard header.
    private fun payload() = ByteArray(78).apply {
        putUInt32(16, 0x040700FF); putUInt32(20, 0x81234567)
        putUInt32(24, 0xFEDCBA98); putUInt32(28, 0xFFFFFFFF)
        putUInt16(32, 0xABCD); putUInt16(34, 0xFEDC)
        for (index in 36 until size) this[index] = 0x55
    }
    private fun decoded(bytes: ByteArray) = (MavlinkParser().feed(bytes).single() as MavlinkParseResult.Message)
        .message as MavlinkMessage.AutopilotVersion

    @Test fun bothVersionsDecodeUnsignedFieldsAtTheirWireOffsets() {
        val v1 = decoded(mavlink1TestFrame(148, payload().copyOf(60), 178, 42, 1))
        val v2 = decoded(mavlinkTestFrame(148, payload(), 178, 42, 1))
        assertEquals(v1, v2)
        assertEquals(42, v2.systemId); assertEquals(1, v2.componentId)
        assertEquals(0x040700FFL, v2.firmwareVersion)
        assertEquals(0x81234567L, v2.middlewareVersion)
        assertEquals(0xFEDCBA98L, v2.osVersion)
        assertEquals(0xFFFFFFFFL, v2.boardVersion)
        assertEquals(0xABCD, v2.vendorId); assertEquals(0xFEDC, v2.productId)
    }

    @Test fun version2RestoresTrailingZerosIncludingPartiallySentFields() {
        val packet = mavlinkTestFrame(148, ByteArray(17).apply { this[16] = 7 }, 178)
        val result = decoded(packet)
        assertEquals(7L, result.firmwareVersion)
        assertEquals(0L, result.boardVersion); assertEquals(0, result.productId)
        assertEquals(0L, decoded(mavlinkTestFrame(148, byteArrayOf(0), 178)).firmwareVersion)
    }

    @Test fun v1MustHaveFullBaseAndV2MustStayWithinMessageBounds() {
        listOf(59, 61, 78).forEach { size ->
            assertTrue(MavlinkParser().feed(mavlink1TestFrame(148, ByteArray(size), 178)).single() is MavlinkParseResult.MalformedMessage)
        }
        listOf(0, 79).forEach { size ->
            assertTrue(MavlinkParser().feed(mavlinkTestFrame(148, ByteArray(size), 178)).single() is MavlinkParseResult.MalformedMessage)
        }
    }

    @Test fun uidCapabilitiesAndCustomVersionsAreNotRetained() {
        val original = payload()
        val changed = original.copyOf().apply {
            for (index in 0 until 16) this[index] = 0x7F
            for (index in 36 until size) this[index] = 0x33
        }
        assertEquals(decoded(mavlinkTestFrame(148, original, 178)), decoded(mavlinkTestFrame(148, changed, 178)))
    }

    @Test fun rejectsCorruptionSigningAndUnsupportedFlags() {
        val frame = mavlinkTestFrame(148, payload(), 178)
        val corrupt = frame.copyOf().apply { this[30] = (this[30].toInt() xor 1).toByte() }
        assertTrue(MavlinkParser().feed(corrupt).first() is MavlinkParseResult.InvalidChecksum)
        val signed = frame.copyOf().apply { this[2] = 1 } + ByteArray(13)
        assertEquals(MavlinkParseResult.SignedPacketRejected, MavlinkParser().feed(signed).single())
        val flags = frame.copyOf().apply { this[2] = 2 }
        assertTrue(MavlinkParser().feed(flags).single() is MavlinkParseResult.UnsupportedIncompatibilityFlags)
    }

    @Test fun fragmentedAndMixedVersionFramesAreDecodedInOrder() {
        val v1 = mavlink1TestFrame(148, payload().copyOf(60), 178)
        val v2 = mavlinkTestFrame(148, payload(), 178)
        val parser = MavlinkParser()
        assertTrue(parser.feed(v1.copyOfRange(0, 12)).isEmpty())
        val results = parser.feed(v1.copyOfRange(12, v1.size) + v2).map { it as MavlinkParseResult.Message }
        assertEquals(listOf(MavlinkWireVersion.V1, MavlinkWireVersion.V2), results.map { it.wireVersion })
        assertEquals(results[0].message, results[1].message)
    }
}
