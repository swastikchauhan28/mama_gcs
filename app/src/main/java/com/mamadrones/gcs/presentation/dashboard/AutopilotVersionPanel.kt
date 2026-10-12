package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.PanelSpec

object AutopilotVersionPanel {
    fun firmware(value: Long): String {
        if (value == 0L) return "ZERO / UNSPECIFIED (0)"
        val kind = (value and 255).toInt()
        val release = when (kind) {
            in 0..63 -> "development"
            in 64..127 -> "alpha"
            in 128..191 -> "beta"
            in 192..254 -> "release candidate"
            else -> "official"
        }
        return "${(value shr 24) and 255}.${(value shr 16) and 255}.${(value shr 8) and 255} · $release (type $kind)"
    }

    fun panel(state: VehicleState, now: Long): PanelSpec {
        val sample = state.autopilotVersion
        val started = state.mavlinkDiagnostics.startedAtEpochMillis
        val time = sample?.receivedAtEpochMillis
        val eligible = time != null && started != null && time >= started && time <= now
        val heartbeat = state.lastHeartbeatAtEpochMillis
        val live = state.connected && state.mavlinkDiagnostics.active && started != null &&
            heartbeat != null && heartbeat >= started && heartbeat <= now && now - heartbeat <= 5_000
        val status = when {
            sample == null -> "NOT RECEIVED"
            !eligible -> "SESSION / CLOCK MISMATCH · VALUES HIDDEN"
            !live -> "LAST OBSERVATION · LINK NOT LIVE"
            else -> "OBSERVED THIS SESSION · NOT VERIFIED"
        }
        fun raw(value: Long?) = if (!eligible || value == null) "—" else if (value == 0L)
            "ZERO / UNSPECIFIED (0)" else "0x${value.toString(16).uppercase().padStart(8, '0')}"
        return PanelSpec("Autopilot firmware", status, listOf(
            "Firmware version" to if (eligible) firmware(sample.firmwareVersion) else "—",
            "Firmware · raw" to raw(sample?.firmwareVersion),
            "Middleware · raw" to raw(sample?.middlewareVersion),
            "OS · raw" to raw(sample?.osVersion),
            "Board version · raw" to raw(sample?.boardVersion),
            "Vendor ID · raw" to raw(sample?.vendorId?.toLong()),
            "Product ID · raw" to raw(sample?.productId?.toLong()),
        ), note = if (eligible) "Observed ${(now - time!!) / 1_000} s ago. Retained metadata, not a continuous live measurement. " +
            "Zero fields are not used to infer a version or model; board/vendor IDs do not verify Cube model or wiring."
            else "No usable version observation in this session. No firmware or hardware identity is inferred.")
    }
}
