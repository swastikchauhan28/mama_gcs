package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class BleStabilityReportTest {
    private fun run(outcome: String = "PASSED", reason: String? = null) =
        BleStabilityRun(1000, 601000, outcome, 600000, reason)

    @Test fun reportIncludesEveryTerminalOutcomeAndExplicitLimitations() {
        val report = BleStabilityReport.capture(listOf("PASSED", "FAILED", "STOPPED", "INTERRUPTED").map { run(it) }, 700000)
        listOf("PASSED", "FAILED", "STOPPED", "INTERRUPTED").forEach { assertTrue(report.contains("Outcome: $it")) }
        assertTrue(report.contains("Recorded duration: 600000 ms"))
        assertTrue(report.contains("1970-01-01T00:00:01Z"))
        assertTrue(report.contains("not connected time"))
        assertTrue(report.contains("does not prove notifications"))
        assertTrue(report.contains("NOT VERIFIED"))
    }

    @Test fun reportKeepsLatestTwentyInReverseSaveOrderWithoutMutatingHistory() {
        val runs = (1L..25L).map { run().copy(durationMillis = it) }
        val report = BleStabilityReport.capture(runs, 700000)
        assertTrue(report.contains("Saved results included: 20"))
        assertTrue(report.indexOf("Recorded duration: 25 ms") < report.indexOf("Recorded duration: 6 ms"))
        assertFalse(report.contains("Recorded duration: 5 ms"))
        assertEquals(25, runs.size)
        assertTrue(report.toByteArray(Charsets.UTF_8).size < BleStabilityReport.MAX_BYTES)
    }

    @Test fun arbitraryOutcomesAndReasonsCannotLeakIdentityOrInjectReportLines() {
        val privateText = "T12_private 00:01:02:03:04:05\nOutcome: PASSED"
        val report = BleStabilityReport.capture(listOf(run(privateText, privateText)), 700000)
        assertFalse(report.contains(privateText))
        assertFalse(report.contains("00:01:02:03:04:05"))
        assertFalse(report.contains("Outcome: PASSED"))
        assertTrue(report.contains("Outcome: UNKNOWN"))
        assertTrue(report.contains("OTHER (details omitted)"))
    }

    @Test fun knownReasonsUseOnlyFixedCategories() {
        val reasons = mapOf("Stopped manually" to "MANUAL TEST STOP", "Disconnected manually" to "MANUAL DISCONNECT",
            "App moved to background" to "APP BACKGROUNDED", "App process ended while the test was running." to "APP PROCESS ENDED",
            "BLE device disconnected. Reconnect to retry." to "GATT DISCONNECTED")
        reasons.forEach { (reason, category) ->
            assertTrue(BleStabilityReport.capture(listOf(run(reason = reason)), 700000).contains("Reason category: $category"))
        }
    }

    @Test fun clockInconsistencyAndInvalidDurationAreExplicit() {
        val report = BleStabilityReport.capture(listOf(run().copy(finishedAtEpochMillis = 0, durationMillis = -1), run()), 1000)
        assertEquals(2, Regex("Calendar timestamps inconsistent").findAll(report).count())
        assertTrue(report.contains("Recorded duration: 0 ms"))
    }

    @Test fun writesExactUtf8AndClosesStream() {
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        BleStabilityReport.write("Report · µ", { output })
        assertEquals("Report · µ", output.toString("UTF-8"))
        assertTrue(closed)
    }

    @Test fun sizeLimitIsCheckedInBytesBeforeOpeningDestination() {
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            BleStabilityReport.write("µ".repeat(BleStabilityReport.MAX_BYTES)) { opened = true; ByteArrayOutputStream() }
        }
        assertFalse(opened)
    }

    @Test fun nullStreamFailsAndBrokenStreamCloses() {
        assertThrows(IOException::class.java) { BleStabilityReport.write("report") { null } }
        var closed = false
        val broken = object : OutputStream() {
            override fun write(b: Int) { throw IOException("provider error") }
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { BleStabilityReport.write("report") { broken } }
        assertTrue(closed)
    }
}
