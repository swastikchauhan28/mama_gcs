package com.mamadrones.gcs.domain.model

enum class UserRole { ADMIN, OPERATOR, VIEWER }

/** Created only by a future trusted local authentication provider, never from a UI role selector. */
data class UserSession(
    val userId: String,
    val role: UserRole,
    val enabled: Boolean,
    val expiresAtMonotonicMillis: Long
)

enum class Permission { MONITOR, DRIVE, MISSION, SPRAY, HYDRAULIC, DIAGNOSTICS, CONFIGURE, MANAGE_USERS }

data class AuditRecord(
    val timestampEpochMillis: Long,
    val userId: String,
    val vehicleId: String?,
    val action: Permission,
    val result: String
)
