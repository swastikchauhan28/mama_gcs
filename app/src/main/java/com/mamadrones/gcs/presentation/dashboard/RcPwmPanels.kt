package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.ObservedMavlinkMessage
import com.mamadrones.gcs.presentation.components.PanelSpec

/** The five-second display window is a UI timeout, never a hardware failsafe. */
object RcPwmPanels {
    private const val DISPLAY_WINDOW_MILLIS = 5_000L

    /** RC uses elapsed receipt evidence. */
    fun rc(state: VehicleState, nowElapsedMillis: Long?): PanelSpec {
        val sample = state.rcInput
        val diagnostics = state.mavlinkDiagnostics
        val started = diagnostics.startedAtElapsedMillis
        val sampleAt = sample.lastUpdatedAtElapsedMillis
        val now = nowElapsedMillis
        fun current(time: Long?): Boolean = started != null && started >= 0 && now != null && now >= started &&
            time != null && time >= started && time <= now && now - time <= DISPLAY_WINDOW_MILLIS
        fun accepted(type: ObservedMavlinkMessage): Boolean = diagnostics.messageObservations.any {
            it.type == type && it.accepted > 0 && current(it.lastAcceptedAtElapsedMillis)
        }
        val status = when {
            sampleAt == null && sample.lastUpdatedAtEpochMillis == null -> "NOT RECEIVED"
            !state.connected || !diagnostics.active -> "LINK NOT LIVE · VALUES HIDDEN"
            started == null || started < 0 || now == null || now < started || sampleAt == null || sampleAt < 0 ->
                "ELAPSED TIME UNAVAILABLE · VALUES HIDDEN"
            sampleAt < started -> "NOT FROM CURRENT SESSION"
            sampleAt > now -> "CLOCK INCONSISTENT · VALUES HIDDEN"
            state.systemId == null || state.systemId !in 1..255 || state.componentId == null || state.componentId !in 1..255 -> "AUTOPILOT NOT CONFIRMED · VALUES HIDDEN"
            !current(state.lastHeartbeatAtElapsedMillis) || !accepted(ObservedMavlinkMessage.HEARTBEAT) ->
                "HEARTBEAT NOT CURRENT · VALUES HIDDEN"
            now - sampleAt > DISPLAY_WINDOW_MILLIS -> "STALE · VALUES HIDDEN"
            !accepted(ObservedMavlinkMessage.RC_CHANNELS) -> "RC RECEIPT NOT CONFIRMED · VALUES HIDDEN"
            else -> "RECENTLY RECEIVED · NOT VERIFIED"
        }
        val show = status == "RECENTLY RECEIVED · NOT VERIFIED"
        val sampleAge = when {
            sampleAt == null -> "Elapsed receipt time unavailable"
            now == null || sampleAt < 0 || now < 0 || sampleAt > now -> "Elapsed receipt time inconsistent"
            else -> "Last received ${(now - sampleAt) / 1_000} s ago (elapsed time)"
        }
        return PanelSpec("RC inputs · RC_CHANNELS", status,
            listOf(
                "Reported channel count" to if (show) sample.channelCount?.toString() ?: "UNKNOWN" else "—",
                "RSSI · device-specific raw scale" to if (show) sample.rssiRaw?.toString() ?: "UNKNOWN" else "—",
            ) + List(18) { index -> "RC ${index + 1}" to if (show)
                sample.channelsMicroseconds.getOrNull(index)?.let { "$it µs" } ?: "UNUSED / UNAVAILABLE"
                else "—" },
            note = "$sampleAge. First 18 channels only; reported count may be larger. " +
                "RSSI is not a percentage. No channel-to-stick mapping or neutral calibration is assumed.")
    }

    /** Each port must have its own recent sample; another port cannot refresh it. */
    fun pwm(state: VehicleState, port: Int, nowElapsedMillis: Long?): PanelSpec {
        val sample = state.pwmOutputs.firstOrNull { it.port == port }
        val diagnostics = state.mavlinkDiagnostics
        val started = diagnostics.startedAtElapsedMillis
        val sampleAt = sample?.lastUpdatedAtElapsedMillis
        val now = nowElapsedMillis
        fun current(time: Long?): Boolean = started != null && started >= 0 && now != null && now >= started &&
            time != null && time >= started && time <= now && now - time <= DISPLAY_WINDOW_MILLIS
        fun accepted(type: ObservedMavlinkMessage): Boolean = diagnostics.messageObservations.any {
            it.type == type && it.accepted > 0 && current(it.lastAcceptedAtElapsedMillis)
        }
        val status = when {
            sample == null -> "NOT RECEIVED"
            !state.connected || !diagnostics.active -> "LINK NOT LIVE · VALUES HIDDEN"
            started == null || started < 0 || now == null || now < started || sampleAt == null || sampleAt < 0 ->
                "ELAPSED TIME UNAVAILABLE · VALUES HIDDEN"
            sampleAt < started -> "NOT FROM CURRENT SESSION"
            sampleAt > now -> "CLOCK INCONSISTENT · VALUES HIDDEN"
            state.systemId == null || state.systemId !in 1..255 || state.componentId == null || state.componentId !in 1..255 ->
                "AUTOPILOT NOT CONFIRMED · VALUES HIDDEN"
            !current(state.lastHeartbeatAtElapsedMillis) || !accepted(ObservedMavlinkMessage.HEARTBEAT) ->
                "HEARTBEAT NOT CURRENT · VALUES HIDDEN"
            now - sampleAt > DISPLAY_WINDOW_MILLIS -> "STALE · VALUES HIDDEN"
            !accepted(ObservedMavlinkMessage.SERVO_OUTPUT_RAW) -> "PWM RECEIPT NOT CONFIRMED · VALUES HIDDEN"
            else -> "RECENTLY RECEIVED · NOT VERIFIED"
        }
        val show = status == "RECENTLY RECEIVED · NOT VERIFIED"
        val sampleAge = when {
            sampleAt == null -> "Elapsed receipt time unavailable"
            now == null || sampleAt < 0 || now < 0 || sampleAt > now -> "Elapsed receipt time inconsistent"
            else -> "Last received ${(now - sampleAt) / 1_000} s ago (elapsed time)"
        }
        return PanelSpec("PWM outputs · reported port $port", status,
            List(16) { index -> "Output field ${index + 1}" to if (show)
                sample?.outputsMicroseconds?.getOrNull(index)?.let { "$it µs" }
                    ?: if (index >= 8) "ZERO / NOT PROVIDED" else "UNKNOWN"
                else "—" },
            note = "$sampleAge. SERVO_OUTPUT_RAW fields, not verified physical pins or motor feedback. " +
                "Fields 9–16 are MAVLink 2 extensions; zero and unavailable cannot be distinguished. " +
                "A value of 0 or 1500 does not prove the rover is stopped.")
    }
}
