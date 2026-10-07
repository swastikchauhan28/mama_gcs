package com.mamadrones.gcs.core.security

import org.junit.Assert.*
import org.junit.Test

class DriveSafetyGateTest {
    private val complete = DriveSafetyEvidence(
        trustedOperatorSession = true, drivePermissionGranted = true,
        vehicleIdentityProvisioned = true, mavlinkPeerAuthenticated = true,
        heartbeatFresh = true, approvedVehicleSafetyProfile = true,
        commandRouteValidated = true, physicalEmergencyStopVerified = true,
        linkLossFailsafeVerified = true, modeAndArmingPolicySatisfied = true,
        requiredTelemetryFresh = true, deadmanLeaseActive = true,
    )

    @Test fun `default and empty assessments deny drive admission`() {
        val result = DriveSafetyGate.assess()
        assertFalse(result.mayAcceptDriveIntent)
        assertEquals(DriveSafetyRequirement.entries.toSet(), result.blockers.toSet())
        assertFalse(DriveSafetyAssessment(emptyList()).mayAcceptDriveIntent)
    }

    @Test fun `each missing requirement independently blocks admission`() {
        val cases = listOf(
            DriveSafetyRequirement.TRUSTED_OPERATOR_SESSION to complete.copy(trustedOperatorSession = false),
            DriveSafetyRequirement.DRIVE_PERMISSION to complete.copy(drivePermissionGranted = false),
            DriveSafetyRequirement.VEHICLE_IDENTITY to complete.copy(vehicleIdentityProvisioned = false),
            DriveSafetyRequirement.AUTHENTICATED_MAVLINK to complete.copy(mavlinkPeerAuthenticated = false),
            DriveSafetyRequirement.FRESH_HEARTBEAT to complete.copy(heartbeatFresh = false),
            DriveSafetyRequirement.APPROVED_SAFETY_PROFILE to complete.copy(approvedVehicleSafetyProfile = false),
            DriveSafetyRequirement.VALIDATED_COMMAND_ROUTE to complete.copy(commandRouteValidated = false),
            DriveSafetyRequirement.PHYSICAL_EMERGENCY_STOP to complete.copy(physicalEmergencyStopVerified = false),
            DriveSafetyRequirement.LINK_LOSS_FAILSAFE to complete.copy(linkLossFailsafeVerified = false),
            DriveSafetyRequirement.MODE_AND_ARMING_POLICY to complete.copy(modeAndArmingPolicySatisfied = false),
            DriveSafetyRequirement.FRESH_REQUIRED_TELEMETRY to complete.copy(requiredTelemetryFresh = false),
            DriveSafetyRequirement.DEADMAN_LEASE to complete.copy(deadmanLeaseActive = false),
        )
        assertEquals(DriveSafetyRequirement.entries.size, cases.size)
        cases.forEach { (requirement, evidence) ->
            val result = DriveSafetyGate.assess(evidence)
            assertFalse(requirement.name, result.mayAcceptDriveIntent)
            assertEquals(listOf(requirement), result.blockers)
        }
    }

    @Test fun `complete synthetic evidence satisfies policy without sending commands`() {
        val result = DriveSafetyGate.assess(complete)
        assertTrue(result.mayAcceptDriveIntent)
        assertTrue(result.blockers.isEmpty())
        assertEquals(DriveSafetyRequirement.entries.size, result.checks.size)
    }
}
