package com.mamadrones.gcs.domain.model

/** Observations only: channel numbers are not actuator identities or calibration. */
data class RcInputState(
    val channelCount: Int? = null,
    val channelsMicroseconds: List<Int?> = emptyList(),
    val rssiRaw: Int? = null,
    val lastUpdatedAtEpochMillis: Long? = null,
    // Volatile current-session receipt time, not a control or safety signal.
    val lastUpdatedAtElapsedMillis: Long? = null,
)

data class PwmOutputState(
    val port: Int,
    val outputsMicroseconds: List<Int?> = emptyList(),
    val lastUpdatedAtEpochMillis: Long? = null,
)
