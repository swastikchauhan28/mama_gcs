package com.mamadrones.gcs.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Ideal straight-pass arithmetic only; this does not measure worked soil. */
data class RequiredOverlapEstimate(val percentRoundedUp: BigDecimal, val targetMarginMeters: Double) {
    val exceedsPlannerLimit: Boolean get() = percentRoundedUp > BigDecimal("80.00")
}

object RequiredOverlapEstimator {
    fun calculate(width: Double?, perPassError: Double?, targetMargin: Double?): RequiredOverlapEstimate? {
        if (FieldCoveragePlanner.settingsError(width, 0.0) != null || perPassError == null || targetMargin == null ||
            !perPassError.isFinite() || perPassError !in 0.0..50.0 ||
            !targetMargin.isFinite() || targetMargin !in 0.0..50.0) return null
        // Decimal division rounds upward so the displayed minimum never understates the inputs.
        val required = BigDecimal.valueOf(perPassError).multiply(BigDecimal.valueOf(2))
            .add(BigDecimal.valueOf(targetMargin)).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(width!!), 2, RoundingMode.CEILING)
        return RequiredOverlapEstimate(required, targetMargin)
    }
}
