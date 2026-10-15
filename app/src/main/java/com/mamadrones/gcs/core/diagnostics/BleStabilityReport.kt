package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/** Export only bounded, completed history. Never include device identity or arbitrary error text. */
object BleStabilityReport {
    const val MAX_BYTES = 16 * 1024

    fun capture(runs: List<BleStabilityRun>, capturedAt: Long): String = buildString {
        val saved = runs.takeLast(20).asReversed()
        appendLine("MAMA GCS - BLE STABILITY REPORT v1")
        appendLine("Captured UTC: ${Instant.ofEpochMilli(capturedAt)}")
        appendLine("Frozen snapshot of saved results on this phone; newest saved result first.")
        appendLine("Saved results included: ${saved.size} (maximum 20). Active/unsaved tests are excluded.")
        appendLine()
        saved.forEachIndexed { index, run ->
            val outcome = run.outcome.takeIf { it in setOf("PASSED", "FAILED", "STOPPED", "INTERRUPTED") } ?: "UNKNOWN"
            appendLine("TEST ${index + 1}")
            appendLine("Outcome: $outcome")
            appendLine("Started UTC: ${Instant.ofEpochMilli(run.startedAtEpochMillis)}")
            appendLine("Finished/recovered UTC: ${Instant.ofEpochMilli(run.finishedAtEpochMillis)}")
            appendLine("Recorded duration: ${run.durationMillis.coerceAtLeast(0)} ms")
            appendLine("Reason category: ${reasonCategory(run.disconnectReason)}")
            if (run.finishedAtEpochMillis < run.startedAtEpochMillis || run.finishedAtEpochMillis > capturedAt) {
                appendLine("Calendar timestamps inconsistent; do not use them to infer connection duration.")
            }
            appendLine()
        }
        appendLine("INTERPRETATION")
        appendLine("PASSED: foreground GATT connection stayed open for the ten-minute trial.")
        appendLine("FAILED: an unexpected disconnect was observed; its physical cause is not established.")
        appendLine("STOPPED: trial ended early; not a pass. INTERRUPTED: unfinished run recovered after app restart.")
        appendLine("Interrupted duration uses calendar time and includes time the app was closed; it is not connected time.")
        appendLine("History is local, bounded and not tamper-evident. It cannot establish which remote was tested.")
        appendLine("No device names/addresses, UUIDs, raw error text, BLE payloads, location or account data included.")
        appendLine("Connection continuity does not prove notifications, MAVLink, Cube/R12 reception, range or rover control.")
        appendLine("Remote power-loss response, physical emergency stop and vehicle safety are NOT VERIFIED by this report.")
        appendLine("No vehicle commands were added by this feature. Share this file manually only after reviewing it.")
    }

    private fun reasonCategory(reason: String?): String = when (reason) {
        null -> "NONE RECORDED"
        "Stopped manually" -> "MANUAL TEST STOP"
        "Disconnected manually" -> "MANUAL DISCONNECT"
        "App moved to background" -> "APP BACKGROUNDED"
        "App process ended while the test was running." -> "APP PROCESS ENDED"
        "BLE device disconnected. Reconnect to retry." -> "GATT DISCONNECTED"
        else -> "OTHER (details omitted)"
    }

    fun write(content: String, openStream: () -> OutputStream?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Report exceeds supported size" }
        val stream = openStream() ?: throw IOException("No document stream")
        stream.use { it.write(bytes); it.flush() }
    }
}
