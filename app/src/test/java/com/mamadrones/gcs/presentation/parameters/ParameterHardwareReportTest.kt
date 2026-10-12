package com.mamadrones.gcs.presentation.parameters

import com.mamadrones.gcs.data.parameters.ParameterFileReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class ParameterHardwareReportTest {
    private fun snapshot(text: String) = ParameterFileReader.read(text.byteInputStream())

    @Test fun reportPreservesSourceEvidenceAndMissingFieldsWithoutSafetyApproval() {
        val source = snapshot("# internal comment\nSERVO1_FUNCTION,74\nSERVO1_TRIM,1500\nFS_GCS_ENABLE,0")
        val report = ParameterHardwareReport.capture(source)
        assertTrue(report.contains("Current file SHA-256: ${source.sha256}"))
        assertTrue(report.contains("SERVO1_FUNCTION: 74 | Source line 2"))
        assertTrue(report.contains("Right throttle (reference label)"))
        assertTrue(report.contains("SERVO1_MIN: NOT IN FILE"))
        assertTrue(report.contains("NOT LIVE CONFIGURATION OR SAFETY APPROVAL"))
        assertTrue(report.contains("physical emergency-stop action"))
        assertFalse(report.contains("internal comment"))
    }

    @Test fun onlySummaryAllowlistIsExportedWithUnknownValuesUnchanged() {
        val report = ParameterHardwareReport.capture(snapshot("SECRET,987654\nSERVO33_FUNCTION,73\nSERIAL10_BAUD,57\nSERIAL1_PROTOCOL,999\nSERVO2_FUNCTION,73.1"))
        assertFalse(report.contains("SECRET"))
        assertFalse(report.contains("987654"))
        assertFalse(report.contains("SERVO33"))
        assertFalse(report.contains("SERIAL10"))
        assertTrue(report.contains("SERIAL1_PROTOCOL: 999"))
        assertTrue(report.contains("Protocol not interpreted"))
        assertTrue(report.contains("SERVO2_FUNCTION: 73.1"))
        assertFalse(report.contains("Left throttle (reference label)"))
    }

    @Test fun largestSupportedSummaryFitsExportBoundAndIsDeterministic() {
        val text = buildString {
            for (i in 1..32) for (suffix in listOf("FUNCTION", "MIN", "TRIM", "MAX", "REVERSED"))
                appendLine("SERVO${i}_$suffix,${"0".repeat(63)}1")
            for (i in 0..9) for (suffix in listOf("PROTOCOL", "BAUD", "OPTIONS"))
                appendLine("SERIAL${i}_$suffix,${"0".repeat(63)}1")
        }
        val source = snapshot(text)
        val first = ParameterHardwareReport.capture(source)
        assertEquals(first, ParameterHardwareReport.capture(source))
        assertTrue(first.toByteArray(Charsets.UTF_8).size < ParameterHardwareReport.MAX_BYTES)
    }

    @Test fun writesExactUtf8PreviewAndClosesDestination() {
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val report = ParameterHardwareReport.capture(snapshot("SERVO1_FUNCTION,73"))
        ParameterHardwareReport.write(report) { output }
        assertEquals(report, output.toString("UTF-8"))
        assertTrue(closed)
    }

    @Test fun excessiveOutputIsRejectedBeforeOpeningDestination() {
        var opened = false
        try {
            ParameterHardwareReport.write("x".repeat(ParameterHardwareReport.MAX_BYTES + 1)) { opened = true; ByteArrayOutputStream() }
            fail("Expected size rejection")
        } catch (_: IllegalArgumentException) { }
        assertFalse(opened)
    }

    @Test fun writeFailureClosesStreamAndNullStreamFails() {
        var closed = false
        val failing = object : OutputStream() {
            override fun write(value: Int) { throw IOException() }
            override fun close() { closed = true }
        }
        try { ParameterHardwareReport.write("report") { failing }; fail("Expected failure") } catch (_: IOException) { }
        assertTrue(closed)
        try { ParameterHardwareReport.write("report") { null }; fail("Expected failure") } catch (_: IOException) { }
    }
}
