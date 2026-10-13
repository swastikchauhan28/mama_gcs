package com.mamadrones.gcs.presentation.mission

import com.mamadrones.gcs.domain.model.MissionTravelEstimate
import kotlin.math.ceil

object MissionTimingFormat {
    /** Decimal dot/comma accepted; no exponents, units, thousands separators or silent clamping. */
    fun parseSpeed(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (!Regex("(?:[0-9]{1,2}(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})").matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf {
            it.isFinite() && it in MissionTravelEstimate.MIN_PLANNING_SPEED_KMH..MissionTravelEstimate.MAX_PLANNING_SPEED_KMH
        }
    }

    /** Round up to whole seconds; do not imply sub-second accuracy. */
    fun duration(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds < 0.0 || seconds >= Long.MAX_VALUE.toDouble()) return "NOT ESTIMATED"
        val whole = ceil(seconds).toLong()
        val days = whole / 86_400
        val hours = whole % 86_400 / 3_600
        val minutes = whole % 3_600 / 60
        val remaining = whole % 60
        return buildList {
            if (days > 0) add("${days}d")
            if (hours > 0 || days > 0) add("${hours}h")
            if (minutes > 0 || hours > 0 || days > 0) add("${minutes}m")
            add("${remaining}s")
        }.joinToString(" ")
    }
}
