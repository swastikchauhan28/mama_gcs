package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.PanelSpec

/** The five-second display window is a UI timeout, never a hardware failsafe. */
object RcPwmPanels {
    private const val DISPLAY_WINDOW_MILLIS = 5_000L

    fun sampleStatus(state: VehicleState, timestamp: Long?, now: Long): String {
        if (timestamp == null) return "NOT RECEIVED"
        val started = state.mavlinkDiagnostics.startedAtEpochMillis
        if (started == null || timestamp < started) return "NOT FROM CURRENT SESSION"
        if (!state.connected || !state.mavlinkDiagnostics.active) return "LINK NOT LIVE · VALUES HIDDEN"
        val heartbeat = state.lastHeartbeatAtEpochMillis
        if (heartbeat == null || heartbeat < started || heartbeat > now || now - heartbeat > DISPLAY_WINDOW_MILLIS)
            return "HEARTBEAT NOT CURRENT · VALUES HIDDEN"
        if (timestamp > now) return "CLOCK INCONSISTENT · VALUES HIDDEN"
        if (now - timestamp > DISPLAY_WINDOW_MILLIS) return "STALE · VALUES HIDDEN"
        return "RECENTLY RECEIVED · NOT VERIFIED"
    }

    fun rc(state: VehicleState, now: Long): PanelSpec {
        val sample = state.rcInput
        val status = sampleStatus(state, sample.lastUpdatedAtEpochMillis, now)
        val show = status == "RECENTLY RECEIVED · NOT VERIFIED"
        return PanelSpec("RC inputs · RC_CHANNELS", status,
            listOf(
                "Reported channel count" to if (show) sample.channelCount?.toString() ?: "UNKNOWN" else "—",
                "RSSI · device-specific raw scale" to if (show) sample.rssiRaw?.toString() ?: "UNKNOWN" else "—",
            ) + List(18) { index -> "RC ${index + 1}" to if (show)
                sample.channelsMicroseconds.getOrNull(index)?.let { "$it µs" } ?: "UNUSED / UNAVAILABLE"
                else "—" },
            note = "${age(sample.lastUpdatedAtEpochMillis, now)}. First 18 channels only; reported count may be larger. " +
                "RSSI is not a percentage. No channel-to-stick mapping or neutral calibration is assumed.")
    }

    fun pwm(state: VehicleState, port: Int, now: Long): PanelSpec {
        val sample = state.pwmOutputs.firstOrNull { it.port == port }
        val status = sampleStatus(state, sample?.lastUpdatedAtEpochMillis, now)
        val show = status == "RECENTLY RECEIVED · NOT VERIFIED"
        return PanelSpec("PWM outputs · reported port $port", status,
            List(16) { index -> "Output field ${index + 1}" to if (show)
                sample?.outputsMicroseconds?.getOrNull(index)?.let { "$it µs" }
                    ?: if (index >= 8) "ZERO / NOT PROVIDED" else "UNKNOWN"
                else "—" },
            note = "${age(sample?.lastUpdatedAtEpochMillis, now)}. SERVO_OUTPUT_RAW fields, not verified physical pins or motor feedback. " +
                "Fields 9–16 are MAVLink 2 extensions; zero and unavailable cannot be distinguished. " +
                "A value of 0 or 1500 does not prove the rover is stopped.")
    }

    private fun age(timestamp: Long?, now: Long): String = when {
        timestamp == null -> "No sample"
        timestamp > now -> "Sample clock is ahead"
        else -> "Last received ${(now - timestamp) / 1_000} s ago"
    }
}
