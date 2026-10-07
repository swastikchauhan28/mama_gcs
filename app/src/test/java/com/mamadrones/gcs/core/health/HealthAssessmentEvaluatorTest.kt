package com.mamadrones.gcs.core.health

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HealthAssessmentEvaluatorTest {
    @Test fun `missing telemetry never implies readiness`() {
        val result = HealthAssessmentEvaluator.evaluate(VehicleState())
        assertEquals(HealthEvidence.NOT_RECEIVED, result.communication)
        assertEquals(HealthEvidence.NOT_RECEIVED, result.gps)
        assertEquals(HealthEvidence.NOT_RECEIVED, result.battery)
        assertEquals(HealthEvidence.NOT_RECEIVED, result.autopilotSensors)
        assertEquals(OperationalReadiness.NOT_ASSESSED, result.readiness)
        assertEquals(HealthEvidence.NOT_CONFIGURED, result.healthProfile)
        assertTrue(result.blockers.isNotEmpty())
    }

    @Test fun `reported telemetry is evidence not operational clearance`() {
        val state = VehicleState(
            connectionStatus = VehicleConnectionState.CONNECTED,
            gps = GpsState(fix = GpsFix.FIX_3D, latitude = -35.0, longitude = 149.0, lastUpdatedAtEpochMillis = 1),
            battery = BatteryState(chargeState = 1, lastUpdatedAtEpochMillis = 1),
            systemStatus = AutopilotSystemStatus(sensorsEnabled = 3, sensorsHealthy = 3, lastUpdatedAtEpochMillis = 1),
        )
        val result = HealthAssessmentEvaluator.evaluate(state)
        assertEquals(HealthEvidence.REPORTED, result.communication)
        assertEquals(HealthEvidence.REPORTED, result.gps)
        assertEquals(HealthEvidence.REPORTED, result.battery)
        assertEquals(HealthEvidence.REPORTED, result.autopilotSensors)
        assertEquals(OperationalReadiness.NOT_ASSESSED, result.readiness)
        assertEquals(HealthEvidence.NOT_RECEIVED,
            HealthAssessmentEvaluator.evaluate(state.copy(connectionStatus = VehicleConnectionState.DEGRADED)).communication)
    }

    @Test fun `protocol issues from GPS any battery and enabled sensors remain visible`() {
        (2..6).forEach { chargeState ->
            val result = HealthAssessmentEvaluator.evaluate(VehicleState(
                gps = GpsState(fix = GpsFix.NO_FIX, lastUpdatedAtEpochMillis = 1),
                batteries = listOf(
                    BatteryState(chargeState = 1, lastUpdatedAtEpochMillis = 1),
                    BatteryState(chargeState = chargeState, lastUpdatedAtEpochMillis = 1),
                ),
                systemStatus = AutopilotSystemStatus(sensorsEnabled = 3, sensorsHealthy = 1, lastUpdatedAtEpochMillis = 1),
            ))
            assertEquals(HealthEvidence.REPORTED_ISSUE, result.gps)
            assertEquals(HealthEvidence.REPORTED_ISSUE, result.battery)
            assertEquals(HealthEvidence.REPORTED_ISSUE, result.autopilotSensors)
            assertEquals(OperationalReadiness.NOT_ASSESSED, result.readiness)
        }
    }
}
