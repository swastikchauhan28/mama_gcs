package com.mamadrones.gcs.core.security

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AuthorizationPolicyTest {
    private val policy = AuthorizationPolicy()
    @Test fun `missing disabled expired and invalid sessions fail closed for every permission`() {
        val sessions = listOf(null,
            UserSession("admin", UserRole.ADMIN, false, 200),
            UserSession("admin", UserRole.ADMIN, true, 100),
            UserSession("", UserRole.ADMIN, true, 200)
        )
        sessions.forEach { session -> Permission.entries.forEach { assertFalse(policy.allows(session, it, 100)) } }
    }
    @Test fun `viewer can only monitor`() {
        val session = UserSession("viewer", UserRole.VIEWER, true, 200)
        Permission.entries.forEach { assertEquals(it == Permission.MONITOR, policy.allows(session, it, 100)) }
    }
    @Test fun `operator cannot administer or configure`() {
        val session = UserSession("operator", UserRole.OPERATOR, true, 200)
        Permission.entries.forEach { permission ->
            assertEquals(permission !in setOf(Permission.CONFIGURE, Permission.MANAGE_USERS), policy.allows(session, permission, 100))
        }
    }
    @Test fun `admin requires an active session and valid monotonic clock`() {
        val session = UserSession("admin", UserRole.ADMIN, true, 200)
        Permission.entries.forEach {
            assertTrue(policy.allows(session, it, 199))
            assertFalse(policy.allows(session, it, 200))
            assertFalse(policy.allows(session, it, -1))
        }
    }
}
