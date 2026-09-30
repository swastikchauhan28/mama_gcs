package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusTextAssemblerTest {
    @Test
    fun `reassembles ordered chunks until the protocol null terminator`() {
        val assembler = StatusTextAssembler()
        val first = MavlinkMessage.StatusText(1, 1, 4, ByteArray(50) { 'A'.code.toByte() }, 9, 0)
        val finalText = " low battery\u0000".encodeToByteArray()
        val second = MavlinkMessage.StatusText(1, 1, 2, finalText + ByteArray(50 - finalText.size), 9, 1)

        assertNull(assembler.append(first, 1_000L))
        val complete = assembler.append(second, 1_100L)

        assertEquals(4, complete?.severity)
        assertEquals("A".repeat(50) + " low battery", complete?.text)
    }

    @Test
    fun `drops out of order and oversized partial messages`() {
        val assembler = StatusTextAssembler(maxMessageBytes = 55)
        val first = MavlinkMessage.StatusText(1, 1, 3, ByteArray(50) { 65 }, 1, 0)
        val skipped = MavlinkMessage.StatusText(1, 1, 3, byteArrayOf(66, 0), 1, 2)

        assertNull(assembler.append(first, 1_000L))
        assertNull(assembler.append(skipped, 1_100L))
        assertNull(assembler.append(first.copy(id = 2), 2_000L))
        val oversized = MavlinkMessage.StatusText(1, 1, 3, ByteArray(50) { 67 } + 0.toByte(), 2, 1)
        assertNull(assembler.append(oversized, 2_100L))
    }
}
