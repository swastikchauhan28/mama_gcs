package com.mamadrones.gcs.domain.model

/** Ideal straight-pass spacing only; a signed margin is not a measurement of worked soil. */
data class SidewaysOverlapEstimate(val nominalMeters: Double, val marginMeters: Double, val perPassErrorMeters: Double)

object SidewaysOverlapEstimator {
    fun calculate(width: Double?, overlap: Double?, perPassError: Double?): SidewaysOverlapEstimate? {
        if (FieldCoveragePlanner.settingsError(width, overlap) != null || perPassError == null ||
            !perPassError.isFinite() || perPassError !in 0.0..50.0) return null
        val nominal = width!! * overlap!! / 100.0
        return SidewaysOverlapEstimate(nominal, nominal - 2.0 * perPassError, perPassError)
    }
}
