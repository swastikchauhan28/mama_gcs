package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BleProfileReportTest {
    private val option = BleNotifyCharacteristic("000018f0-0000-1000-8000-00805f9b34fb",
        "0000fff1-0000-1000-8000-00805f9b34fb", false, 2, 3)

    @Test fun recordsExactInstancesMethodsAndNonVerificationBoundary() {
        val text = BleProfileReport.capture(listOf(option, option.copy(supportsIndication = true, serviceInstanceId = 7)), 4, 0)
        assertTrue(text.contains("1970-01-01T00:00:00Z"))
        assertTrue(text.contains(option.serviceUuid))
        assertTrue(text.contains(option.characteristicUuid))
        assertTrue(text.contains("Service instance: 2; characteristic instance: 3"))
        assertTrue(text.contains("Service instance: 7"))
        assertTrue(text.contains("App receive method: NOTIFY"))
        assertTrue(text.contains("App receive method: INDICATE"))
        assertTrue(text.contains("does not subscribe"))
        assertTrue(text.contains("not a device identifier"))
    }

    @Test fun zeroOptionsIsExplicitAndDoesNotDeclareUnsupportedDevice() {
        val text = BleProfileReport.capture(emptyList(), 1, 0)
        assertTrue(text.contains("No eligible receive options discovered"))
        assertTrue(text.contains("does not prove that the device has no data interface"))
    }

    @Test fun boundedReportDeclaresTruncation() {
        val text = BleProfileReport.capture(List(1000) { option.copy(serviceInstanceId = it) }, 1, 0)
        assertTrue(text.contains("Eligible receive options: 1000; included: 64"))
        assertTrue(text.contains("TRUNCATED"))
        assertTrue(text.contains("OPTION 64"))
        assertFalse(text.contains("OPTION 65"))
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= BleProfileReport.MAX_BYTES)
    }

    @Test fun invalidUuidCannotInjectPrivateOrMisleadingLines() {
        val text = BleProfileReport.capture(listOf(option.copy(serviceUuid = "private-device\nMAVLINK VERIFIED",
            characteristicUuid = "AA:BB:CC:DD:EE:FF")), 1, 0)
        assertFalse(text.contains("private-device"))
        assertFalse(text.contains("MAVLINK VERIFIED"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(text.contains("INVALID UUID (omitted)"))
    }

    @Test fun requiresSuccessfulConnection() {
        assertThrows(IllegalArgumentException::class.java) { BleProfileReport.capture(emptyList(), 0, 0) }
    }

    @Test fun writesUtf8ClosesStreamAndRejectsOversizeBeforeOpening() {
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val text = BleProfileReport.capture(listOf(option), 1, 0)
        BleProfileReport.write(text) { stream }
        assertEquals(text, stream.toString("UTF-8"))
        assertTrue(closed)
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            BleProfileReport.write("x".repeat(BleProfileReport.MAX_BYTES + 1)) { opened = true; stream }
        }
        assertFalse(opened)
        assertThrows(IOException::class.java) { BleProfileReport.write(text) { null } }
    }
}
