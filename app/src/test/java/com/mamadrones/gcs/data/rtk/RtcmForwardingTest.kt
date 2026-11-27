package com.mamadrones.gcs.data.rtk

import com.mamadrones.gcs.data.mavlink.MavlinkChecksum
import org.junit.Assert.*
import org.junit.Test

class RtcmForwardingTest {
    private fun correction(payloadSize: Int): ByteArray {
        val frame = ByteArray(payloadSize + 6)
        frame[0] = 0xd3.toByte()
        frame[1] = (payloadSize shr 8).toByte()
        frame[2] = payloadSize.toByte()
        for (index in 0 until payloadSize) frame[index + 3] = (index * 37).toByte()
        val crc = Rtcm3Framer.crc24q(frame, frame.size - 3)
        frame[frame.size - 3] = (crc shr 16).toByte()
        frame[frame.size - 2] = (crc shr 8).toByte()
        frame[frame.size - 1] = crc.toByte()
        return frame
    }

    @Test fun crc24qMatchesPublishedCheckValue() {
        assertEquals(0xcde703, Rtcm3Framer.crc24q("123456789".toByteArray()))
    }

    @Test fun splitFrameAndCorruptFrameResynchronizeWithoutForwardingCorruption() {
        val parser = Rtcm3Framer()
        val valid = correction(40)
        val corrupt = valid.clone().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertTrue(parser.feed(byteArrayOf(1, 2) + valid.copyOfRange(0, 12)).isEmpty())
        assertArrayEquals(valid, parser.feed(valid.copyOfRange(12, valid.size)).single().bytes)
        assertTrue(parser.feed(corrupt).isEmpty())
        assertArrayEquals(valid, parser.feed(valid).single().bytes)
        assertTrue(parser.rejectedFrames > 0)
    }

    @Test fun oversizedRtcmIsRejectedBeforeMavlinkFragmentation() {
        val parser = Rtcm3Framer()
        assertTrue(parser.feed(correction(715)).isEmpty()) // Full RTCM frame exceeds four 180-byte fragments.
        assertArrayEquals(correction(714), parser.feed(correction(714)).single().bytes)
    }

    @Test fun validatedFrameCannotBeChangedAfterParsing() {
        val source = correction(12)
        val frame = Rtcm3Framer().feed(source).single()
        source[3] = 0
        val exposed = frame.bytes
        exposed[3] = 0
        assertArrayEquals(correction(12), frame.bytes)
    }

    @Test fun mavlink2PacketsCarryOnlyMessage233WithValidCrcAndOrderedFragments() {
        val source = correction(400)
        val frame = Rtcm3Framer().feed(source).single()
        val packets = MavlinkRtcmEncoder().encode(frame)
        assertEquals(3, packets.size)
        val payload = ArrayList<Byte>()
        packets.forEachIndexed { index, packet ->
            assertEquals(194, packet.size)
            assertEquals(0xfd, packet[0].toInt() and 0xff)
            assertEquals(182, packet[1].toInt() and 0xff)
            assertEquals(index, packet[4].toInt() and 0xff)
            assertEquals(255, packet[5].toInt() and 0xff)
            assertEquals(233, packet[7].toInt() and 0xff)
            assertEquals(1 or (index shl 1), packet[10].toInt() and 0xff)
            val length = packet[11].toInt() and 0xff
            assertEquals(if (index < 2) 180 else 46, length)
            payload.addAll(packet.copyOfRange(12, 12 + length).toList())
            assertEquals(MavlinkChecksum.calculate(packet.copyOfRange(1, 192), 35),
                (packet[192].toInt() and 0xff) or ((packet[193].toInt() and 0xff) shl 8))
        }
        assertArrayEquals(source, payload.toByteArray())
    }

    @Test fun casterUrlIsParsedWithoutRetainingCredentialsInDisplayOrErrors() {
        val settings = NtripSettings.parse("http://demo:sample@127.0.0.1:2105/VRS_RTCM32")
        assertEquals(NtripScheme.HTTP, settings.scheme)
        assertEquals("127.0.0.1", settings.host)
        assertEquals(2105, settings.port)
        assertEquals("VRS_RTCM32", settings.mountpoint)
        assertEquals("demo", settings.username)
        assertEquals("sample", settings.password)
        assertEquals("http://127.0.0.1:2105/VRS_RTCM32", settings.displayTarget)
        assertFalse(settings.toString().contains("sample"))
        assertEquals(NtripScheme.HTTPS, NtripSettings.parse("https://caster.example:2101/MOUNT").scheme)
        assertThrows(IllegalArgumentException::class.java) { NtripCasterClient(settings, false) }
        listOf("ftp://u:p@host.test:2101/M", "http://u:p@host.test/M",
            "http://u:p@host.test:2101/../M", "http://u:p@host.test:2101/M?token=x",
            "http://u%0d:p@host.test:2101/M", "http://u:p@host.test:2101/M/next",
            "http://u:p@host..test:2101/M").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { NtripSettings.parse(invalid) }
        }
    }
}
