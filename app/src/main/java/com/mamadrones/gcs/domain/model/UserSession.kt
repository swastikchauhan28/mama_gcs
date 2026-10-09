package com.mamadrones.gcs.domain.model

enum class UserRole { ADMIN, OPERATOR, VIEWER }

/** Issued only by the device-local credential provider, never from a UI role selector. */
data class UserSession(
    val userId: String,
    val role: UserRole,
    val enabled: Boolean,
    val expiresAtMonotonicMillis: Long,
    val sessionId: String = "",
)

enum class Permission { MONITOR, DRIVE, MISSION, SPRAY, HYDRAULIC, DIAGNOSTICS, CONFIGURE, MANAGE_USERS }

data class AuditRecord(
    val timestampEpochMillis: Long,
    val actorUserId: String?,
    val vehicleId: String?,
    val event: AuditEvent,
    val outcome: String,
    val subject: String? = null,
)

enum class AuditEvent {
    ADMIN_INITIALIZED, LOGIN_SUCCEEDED, LOGIN_FAILED, LOGOUT,
    ACCOUNT_CREATED, ACCOUNT_ENABLED, ACCOUNT_DISABLED,
    UDP_ENDPOINT_CHANGE_REQUESTED,
    PASSWORD_CHANGED, PASSWORD_CHANGE_FAILED, SESSION_EXPIRED,
}

data class AccessAccount(
    val id: String,
    val username: String,
    val role: UserRole,
    val enabled: Boolean,
    val createdAtEpochMillis: Long,
)

enum class AccessSetupState { LOADING, ADMIN_REQUIRED, READY, STORAGE_UNAVAILABLE }

data class AccessState(
    val setup: AccessSetupState = AccessSetupState.LOADING,
    val accounts: List<AccessAccount> = emptyList(),
    val session: UserSession? = null,
    val audit: List<AuditRecord> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val lockedOutUntilMonotonicMillis: Long? = null,
    val busy: Boolean = false,
)
