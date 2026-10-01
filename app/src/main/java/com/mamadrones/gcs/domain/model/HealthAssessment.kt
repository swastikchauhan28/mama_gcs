package com.mamadrones.gcs.domain.model

/**
 * A transparent summary of telemetry evidence. It is deliberately not a
 * machine-safety verdict: that needs a provisioned, vehicle-specific health
 * profile and the required hardware telemetry routes.
 */
enum class OperationalReadiness { NOT_ASSESSED }

enum class HealthEvidence { NOT_RECEIVED, REPORTED, REPORTED_ISSUE, NOT_CONFIGURED }

data class HealthAssessment(
    val readiness: OperationalReadiness = OperationalReadiness.NOT_ASSESSED,
    val healthProfile: HealthEvidence = HealthEvidence.NOT_CONFIGURED,
    val communication: HealthEvidence = HealthEvidence.NOT_RECEIVED,
    val gps: HealthEvidence = HealthEvidence.NOT_RECEIVED,
    val battery: HealthEvidence = HealthEvidence.NOT_RECEIVED,
    val autopilotSensors: HealthEvidence = HealthEvidence.NOT_RECEIVED,
    val blockers: List<String> = emptyList()
)
