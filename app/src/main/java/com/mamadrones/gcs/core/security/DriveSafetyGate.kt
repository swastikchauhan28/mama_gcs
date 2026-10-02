package com.mamadrones.gcs.core.security

/**
 * Explicit evidence required before a future drive-command use case may accept an intent.
 * Every value defaults to false; UI input must never be used to mark a safety check as satisfied.
 */
data class DriveSafetyEvidence(
    val trustedOperatorSession: Boolean = false,
    val drivePermissionGranted: Boolean = false,
    val vehicleIdentityProvisioned: Boolean = false,
    val mavlinkPeerAuthenticated: Boolean = false,
    val heartbeatFresh: Boolean = false,
    val approvedVehicleSafetyProfile: Boolean = false,
    val commandRouteValidated: Boolean = false,
    val physicalEmergencyStopVerified: Boolean = false,
    val linkLossFailsafeVerified: Boolean = false,
    val modeAndArmingPolicySatisfied: Boolean = false,
    val requiredTelemetryFresh: Boolean = false,
    val deadmanLeaseActive: Boolean = false,
)

data class DriveSafetyCheck(
    val requirement: DriveSafetyRequirement,
    val satisfied: Boolean,
)

enum class DriveSafetyRequirement(val description: String) {
    TRUSTED_OPERATOR_SESSION("Trusted authenticated operator session"),
    DRIVE_PERMISSION("Drive permission granted at the command boundary"),
    VEHICLE_IDENTITY("Provisioned vehicle identity selected and verified"),
    AUTHENTICATED_MAVLINK("Authenticated MAVLink peer and signing policy"),
    FRESH_HEARTBEAT("Fresh heartbeat from the pinned vehicle"),
    APPROVED_SAFETY_PROFILE("Approved vehicle-specific safety profile"),
    VALIDATED_COMMAND_ROUTE("Validated command transport and route"),
    PHYSICAL_EMERGENCY_STOP("Independent physical emergency stop verified"),
    LINK_LOSS_FAILSAFE("Vehicle link-loss behavior tested and verified"),
    MODE_AND_ARMING_POLICY("Allowed vehicle mode and arming conditions verified"),
    FRESH_REQUIRED_TELEMETRY("Required drive telemetry is fresh and valid"),
    DEADMAN_LEASE("Operator deadman is actively held within its approved timeout"),
}

data class DriveSafetyAssessment(
    val checks: List<DriveSafetyCheck>,
) {
    /** This is necessary admission evidence, not proof that a vehicle is safe to operate. */
    val mayAcceptDriveIntent: Boolean
        get() = checks.isNotEmpty() && checks.all(DriveSafetyCheck::satisfied)

    val blockers: List<DriveSafetyRequirement>
        get() = checks.filterNot(DriveSafetyCheck::satisfied).map(DriveSafetyCheck::requirement)
}

/** Pure, fail-closed admission policy. No transport or command sending is performed here. */
object DriveSafetyGate {
    fun assess(evidence: DriveSafetyEvidence = DriveSafetyEvidence()): DriveSafetyAssessment =
        DriveSafetyAssessment(
            checks = listOf(
                DriveSafetyCheck(
                    DriveSafetyRequirement.TRUSTED_OPERATOR_SESSION,
                    evidence.trustedOperatorSession,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.DRIVE_PERMISSION,
                    evidence.drivePermissionGranted,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.VEHICLE_IDENTITY,
                    evidence.vehicleIdentityProvisioned,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.AUTHENTICATED_MAVLINK,
                    evidence.mavlinkPeerAuthenticated,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.FRESH_HEARTBEAT,
                    evidence.heartbeatFresh,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.APPROVED_SAFETY_PROFILE,
                    evidence.approvedVehicleSafetyProfile,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.VALIDATED_COMMAND_ROUTE,
                    evidence.commandRouteValidated,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.PHYSICAL_EMERGENCY_STOP,
                    evidence.physicalEmergencyStopVerified,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.LINK_LOSS_FAILSAFE,
                    evidence.linkLossFailsafeVerified,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.MODE_AND_ARMING_POLICY,
                    evidence.modeAndArmingPolicySatisfied,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.FRESH_REQUIRED_TELEMETRY,
                    evidence.requiredTelemetryFresh,
                ),
                DriveSafetyCheck(
                    DriveSafetyRequirement.DEADMAN_LEASE,
                    evidence.deadmanLeaseActive,
                ),
            ),
        )
}
