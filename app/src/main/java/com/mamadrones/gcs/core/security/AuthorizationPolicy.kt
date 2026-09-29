package com.mamadrones.gcs.core.security

import com.mamadrones.gcs.domain.model.Permission
import com.mamadrones.gcs.domain.model.UserRole
import com.mamadrones.gcs.domain.model.UserSession

/** Pure domain policy. An allowed role is necessary but never sufficient to send a command. */
class AuthorizationPolicy {
    fun allows(session: UserSession?, permission: Permission, nowMonotonicMillis: Long): Boolean {
        if (session == null || session.userId.isBlank() || !session.enabled ||
            nowMonotonicMillis < 0 || nowMonotonicMillis >= session.expiresAtMonotonicMillis
        ) return false
        return when (session.role) {
            UserRole.ADMIN -> true
            UserRole.OPERATOR -> permission in setOf(
                Permission.MONITOR, Permission.DRIVE, Permission.MISSION,
                Permission.SPRAY, Permission.HYDRAULIC, Permission.DIAGNOSTICS
            )
            UserRole.VIEWER -> permission == Permission.MONITOR
        }
    }
}
