package com.mamadrones.gcs.presentation.parameters

import com.mamadrones.gcs.domain.model.ParameterSnapshot
import java.io.OutputStream

/** Explicit, local export of the same bounded evidence shown in Hardware summary. */
object ParameterHardwareReport {
    const val MAX_BYTES = 128 * 1024

    fun capture(snapshot: ParameterSnapshot): String = buildString {
        appendLine("MAMA GCS - HARDWARE FILE REVIEW - FORMAT 1")
        appendLine("UNVERIFIED FILE EVIDENCE ONLY - NOT LIVE CONFIGURATION OR SAFETY APPROVAL")
        appendLine("Current file SHA-256: ${snapshot.sha256}")
        appendLine("Total imported entries: ${snapshot.entries.size}")
        appendLine("File date, vehicle identity and installed firmware: NOT VERIFIED")
        appendLine("Fingerprint identifies file bytes, not authenticity or freshness.")
        appendLine("Selected current-file fields only; reference file and UI filters are excluded.")
        appendLine("Missing means NOT IN FILE, not disabled or default. Labels are not firmware-matched.")
        appendLine()
        ParameterHardwareSummary.sections(snapshot).forEach { section ->
            appendLine(section.title)
            appendLine(section.guidance)
            section.fields.forEach { field ->
                appendLine("${field.name}: ${field.entry?.rawValue ?: "NOT IN FILE"}" +
                    (field.entry?.let { " | Source line ${it.line}" } ?: ""))
                field.hint?.let { appendLine("  Reference note: $it") }
            }
            appendLine()
        }
        appendLine("HARDWARE TEAM - DETAILS STILL TO CONFIRM")
        appendLine("1. Exact installed firmware/build and whether this export is current.")
        appendLine("2. Physical output-to-left/right motor wiring, reversal and ESC calibration.")
        appendLine("3. Receiver TX/RX to Cube connector and baud; identify SBUS/RCIN separately.")
        appendLine("4. Phone/remote protocol and successful live telemetry through the actual route.")
        appendLine("5. Neutral brake/coast behavior and physical emergency-stop action.")
        appendLine("6. Phone/app loss, radio loss and remote manual-takeover behavior, with test evidence.")
        appendLine("7. Approved operating speed and how the maximum is enforced.")
        appendLine("8. VESC feedback route and supported motor measurements, if required.")
        appendLine()
        appendLine("This report does not change parameters, authorize movement or test hardware.")
        appendLine("Vehicle commands and app emergency stop remain unavailable. Use the physical safety system.")
        appendLine("PRIVACY: Contains selected configuration values and a file fingerprint; review before sharing.")
        appendLine("No original file path/name, comments, arbitrary parameters, account or live telemetry data is exported.")
    }

    fun write(content: String, openStream: () -> OutputStream?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Report exceeds supported size." }
        val stream = openStream() ?: throw java.io.IOException("No document stream")
        stream.use { it.write(bytes); it.flush() }
    }
}
