package com.mamadrones.gcs.domain.model

/** Receive recency only; does not establish battery health or safe operation. */
enum class BatteryFreshness { FRESH, STALE, UNAVAILABLE }

object BatteryEvidence {
    // Diagnostic display window, not a verified hardware reporting rate.
    const val MAX_AGE_MILLIS = 10_000L
    fun freshness(sample: BatteryState, connected: Boolean, nowElapsedMillis: Long): BatteryFreshness {
        val received = sample.lastUpdatedAtElapsedMillis
        if (!connected || nowElapsedMillis < 0 || received == null || received < 0 || received > nowElapsedMillis)
            return BatteryFreshness.UNAVAILABLE
        return if (nowElapsedMillis - received <= MAX_AGE_MILLIS) BatteryFreshness.FRESH else BatteryFreshness.STALE
    }
}
