package com.mamadrones.gcs.core.health

import com.mamadrones.gcs.domain.model.*

/**
 * Converts protocol facts into evidence only. No freshness threshold, battery
 * limit, or hardware-health rule is assumed here because none has been
 * provisioned for this vehicle.
 */
object HealthAssessmentEvaluator {
    fun evaluate(state: VehicleState): HealthAssessment = HealthAssessment(
        communication = if (state.connected) HealthEvidence.REPORTED else HealthEvidence.NOT_RECEIVED,
        gps = gpsEvidence(state.gps),
        battery = batteryEvidence(state),
        autopilotSensors = sensorEvidence(state.systemStatus),
        blockers = listOf(
            "No vehicle-specific health profile or hardware limits are configured.",
            "VESC, spray, and hydraulic telemetry paths are not configured.",
            "This receive-only build cannot establish operational readiness or control authority."
        )
    )

    private fun gpsEvidence(gps: GpsState): HealthEvidence = when {
        gps.lastUpdatedAtEpochMillis == null -> HealthEvidence.NOT_RECEIVED
        gps.fix == GpsFix.NO_FIX || gps.fix == GpsFix.UNKNOWN || gps.latitude == null || gps.longitude == null -> HealthEvidence.REPORTED_ISSUE
        else -> HealthEvidence.REPORTED
    }

    private fun batteryEvidence(state: VehicleState): HealthEvidence {
        val samples = state.batteries.ifEmpty { listOf(state.battery) }
        if (samples.all { it.lastUpdatedAtEpochMillis == null }) return HealthEvidence.NOT_RECEIVED
        return if (samples.any { it.chargeState in REPORTED_BATTERY_ISSUES }) {
            HealthEvidence.REPORTED_ISSUE
        } else {
            HealthEvidence.REPORTED
        }
    }

    private fun sensorEvidence(status: AutopilotSystemStatus): HealthEvidence {
        if (status.lastUpdatedAtEpochMillis == null) return HealthEvidence.NOT_RECEIVED
        val enabled = status.sensorsEnabled
        val healthy = status.sensorsHealthy
        if (enabled == null || healthy == null) return HealthEvidence.REPORTED
        return if ((enabled and healthy.inv()) != 0L) HealthEvidence.REPORTED_ISSUE else HealthEvidence.REPORTED
    }

    // MAVLink MAV_BATTERY_CHARGE_STATE: low, critical, emergency, failed, unhealthy.
    private val REPORTED_BATTERY_ISSUES = setOf(2, 3, 4, 5, 6)
}
