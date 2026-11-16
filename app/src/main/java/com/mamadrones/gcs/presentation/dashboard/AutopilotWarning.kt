package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.VehicleStatusText

/** Show only text already accepted from the selected autopilot in the live session. */
fun latestAutopilotWarning(state: VehicleState): VehicleStatusText? =
    state.forDisplay().statusTexts.firstOrNull { it.severity in 0..4 }

fun autopilotWarningAge(message: VehicleStatusText, nowEpochMillis: Long): String =
    if (message.receivedAtEpochMillis <= 0L || message.receivedAtEpochMillis > nowEpochMillis) {
        "Receive time unavailable"
    } else {
        "${(nowEpochMillis - message.receivedAtEpochMillis) / 1_000L}s ago"
    }
