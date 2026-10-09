package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.*
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAccountServiceTest {
    private val original = "original passphrase"
    private val replacement = "replacement passphrase"

    @Test fun firstAdministratorRequiresExplicitLoginAndNoPublicAccountList() = runTest {
        val f = Fixture(this)
        val secret = original.toCharArray()
        f.service.initializeAdministrator(" Admin ", secret)
        assertTrue(secret.all { it == '\u0000' })
        assertNull(f.service.state.value.session)
        assertTrue(f.service.state.value.accounts.isEmpty())
        assertTrue(f.service.state.value.audit.isEmpty())
        assertEquals(AccessSetupState.READY, f.service.state.value.setup)
        f.login()
        assertEquals(UserRole.ADMIN, f.session().role)
        assertEquals("admin", f.service.state.value.accounts.single().username)
        assertTrue(f.session().sessionId.isNotBlank())
    }

    @Test fun rejectedLoginAuditCannotLeaveHiddenAuthenticatedSession() = runTest {
        val f = Fixture(this)
        f.bootstrap()
        f.store.failNextWrite = true
        f.login()
        assertNull(f.service.state.value.session)
        assertEquals(AccessSetupState.STORAGE_UNAVAILABLE, f.service.state.value.setup)
        f.service.clearMessage()
        f.service.refreshSession()
        assertNull(f.service.state.value.session)
        assertFalse(f.store.db!!.audit.any { it.event == AuditEvent.LOGIN_SUCCEEDED })
    }

    @Test fun logoutRevokesEvenWhenAuditWriteFails() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val oldSession = f.session()
        f.store.failNextWrite = true
        f.service.signOut()
        assertNull(f.service.state.value.session)
        assertTrue(f.service.state.value.accounts.isEmpty())
        assertFalse(f.service.recordEndpointChangeRequest(oldSession, null))
    }

    @Test fun sessionTimerExpiresWithoutAnotherUserAction() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        runCurrent()
        advanceTimeBy(AccessLimits.SESSION_TTL_MILLIS)
        runCurrent()
        assertNull(f.service.state.value.session)
        assertEquals(AuditEvent.SESSION_EXPIRED, f.store.db!!.audit.last().event)
        assertTrue(f.service.state.value.message!!.contains("expired"))
    }

    @Test fun foregroundRefreshUsesElapsedTimeIncludingDeviceSleep() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        f.elapsedOffset += AccessLimits.SESSION_TTL_MILLIS
        f.service.refreshSession()
        assertNull(f.service.state.value.session)
        assertEquals(AuditEvent.SESSION_EXPIRED, f.store.db!!.audit.last().event)
    }

    @Test fun expiryAuditFailureStillRevokesSession() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        f.store.failNextWrite = true
        f.elapsedOffset += AccessLimits.SESSION_TTL_MILLIS
        f.service.refreshSession()
        assertNull(f.service.state.value.session)
        assertEquals(AccessSetupState.STORAGE_UNAVAILABLE, f.service.state.value.setup)
    }

    @Test fun previousTimerAndPreviousSessionCannotInvalidateOrAuthorizeNewLogin() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val previous = f.session()
        advanceTimeBy(100)
        f.service.signOut()
        f.login()
        val current = f.session()
        assertNotEquals(previous.sessionId, current.sessionId)
        assertFalse(f.service.recordEndpointChangeRequest(previous, null))
        assertEquals(current, f.session())
        runCurrent()
        advanceTimeBy(AccessLimits.SESSION_TTL_MILLIS - 100)
        runCurrent()
        assertEquals(current, f.session())
        advanceTimeBy(100)
        runCurrent()
        assertNull(f.service.state.value.session)
    }

    @Test fun passwordChangePreservesIdentityUsesNewSaltAndRequiresNewLogin() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val actor = f.session()
        val before = f.store.db!!.accounts.single()
        val oldSecret = original.toCharArray()
        val newSecret = replacement.toCharArray()
        f.service.changePassword(actor, oldSecret, newSecret)
        assertTrue(oldSecret.all { it == '\u0000' })
        assertTrue(newSecret.all { it == '\u0000' })
        assertNull(f.service.state.value.session)
        val after = f.store.db!!.accounts.single()
        assertEquals(before.id, after.id)
        assertEquals(before.role, after.role)
        assertFalse(before.salt.contentEquals(after.salt))
        assertEquals(AuditEvent.PASSWORD_CHANGED, f.store.db!!.audit.last().event)
        f.login(original)
        assertNull(f.service.state.value.session)
        f.login(replacement)
        assertEquals(actor.userId, f.session().userId)
        assertFalse(f.service.recordEndpointChangeRequest(actor, null))
    }

    @Test fun wrongCurrentPasswordNeverUpdatesVerifierAndSharesLoginThrottle() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val actor = f.session()
        val before = f.store.db!!.accounts.single().passwordHash.copyOf()
        repeat(AccessLimits.MAX_FAILED_ATTEMPTS) {
            f.service.changePassword(actor, "wrong password".toCharArray(), replacement.toCharArray())
        }
        assertArrayEquals(before, f.store.db!!.accounts.single().passwordHash)
        assertNull(f.service.state.value.session)
        assertEquals(AccessLimits.MAX_FAILED_ATTEMPTS,
            f.store.db!!.audit.count { it.event == AuditEvent.PASSWORD_CHANGE_FAILED })
        f.login()
        assertNull(f.service.state.value.session)
        advanceTimeBy(AccessLimits.LOCKOUT_MILLIS)
        f.login()
        assertNotNull(f.service.state.value.session)
    }

    @Test fun passwordWriteFailureKeepsOldCredentialOnRestartAndRevokesSession() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        f.store.failNextWrite = true
        f.service.changePassword(f.session(), original.toCharArray(), replacement.toCharArray())
        assertNull(f.service.state.value.session)
        val restarted = f.restart()
        restarted.signIn("admin", replacement.toCharArray())
        assertNull(restarted.state.value.session)
        restarted.signIn("admin", original.toCharArray())
        assertNotNull(restarted.state.value.session)
    }

    @Test fun shortOrUnchangedReplacementIsRejectedWithoutChangingAccount() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val actor = f.session()
        val before = f.store.db!!.accounts.single().passwordHash.copyOf()
        f.service.changePassword(actor, original.toCharArray(), "short".toCharArray())
        assertNotNull(f.service.state.value.error)
        f.service.changePassword(actor, original.toCharArray(), original.toCharArray())
        assertTrue(f.service.state.value.error!!.contains("different"))
        assertArrayEquals(before, f.store.db!!.accounts.single().passwordHash)
        assertEquals(actor, f.session())
    }

    @Test fun expirationDuringHashingRejectsPasswordOrAccountMutation() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val before = f.store.db!!.accounts.single().passwordHash.copyOf()
        f.passwords.onCreate = { f.elapsedOffset += AccessLimits.SESSION_TTL_MILLIS }
        f.service.changePassword(f.session(), original.toCharArray(), replacement.toCharArray())
        assertArrayEquals(before, f.store.db!!.accounts.single().passwordHash)
        f.service.refreshSession()
        assertNull(f.service.state.value.session)
        f.login()
        f.service.createAccount(f.session(), "worker", original.toCharArray(), UserRole.OPERATOR)
        assertEquals(1, f.store.db!!.accounts.size)
    }

    @Test fun deniedAdminActionKeepsViewerSessionAndDoesNotExposeOtherAccountsOrAudit() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        f.service.createAccount(f.session(), "viewer", original.toCharArray(), UserRole.VIEWER)
        f.service.signOut()
        f.service.signIn("viewer", original.toCharArray())
        val viewer = f.session()
        assertEquals(listOf("viewer"), f.service.state.value.accounts.map { it.username })
        assertTrue(f.service.state.value.audit.isEmpty())
        f.service.createAccount(viewer, "intruder", original.toCharArray(), UserRole.ADMIN)
        assertEquals(viewer, f.session())
        assertEquals(2, f.store.db!!.accounts.size)
        assertFalse(f.service.recordEndpointChangeRequest(viewer, null))
    }

    @Test fun lockoutSurvivesProcessRecreationOnSameBoot() = runTest {
        val f = Fixture(this)
        f.bootstrap()
        repeat(5) { f.login("wrong password") }
        val restarted = f.restart()
        restarted.signIn("admin", original.toCharArray())
        assertNull(restarted.state.value.session)
        advanceTimeBy(AccessLimits.LOCKOUT_MILLIS)
        restarted.signIn("admin", original.toCharArray())
        assertNotNull(restarted.state.value.session)
    }

    @Test fun longUsernameDoesNotAliasThirtyTwoCharacterAccount() = runTest {
        val f = Fixture(this)
        val username = "a".repeat(32)
        f.service.initializeAdministrator(username, original.toCharArray())
        f.service.signIn(username + "extra", original.toCharArray())
        assertNull(f.service.state.value.session)
        f.service.signIn(username, original.toCharArray())
        assertNotNull(f.service.state.value.session)
    }

    @Test fun corruptStorageDoesNotBecomeFreshAdminSetup() = runTest {
        val f = Fixture(this)
        f.store.failRead = true
        f.bootstrap()
        assertEquals(AccessSetupState.STORAGE_UNAVAILABLE, f.service.state.value.setup)
        assertEquals(0, f.store.writes)
        assertNull(f.service.state.value.session)
    }

    @Test fun cancelledHashingPropagatesCancellationAndClearsBothSecrets() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val actor = f.session()
        val hashing = CompletableDeferred<Unit>()
        f.passwords.onCreate = { hashing.complete(Unit); CompletableDeferred<Unit>().await() }
        val oldSecret = original.toCharArray()
        val newSecret = replacement.toCharArray()
        val job = launch { f.service.changePassword(actor, oldSecret, newSecret) }
        hashing.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(oldSecret.all { it == '\u0000' })
        assertTrue(newSecret.all { it == '\u0000' })
        assertNull(f.service.state.value.session)
        assertFalse(f.service.state.value.busy)
        assertFalse(f.store.db!!.audit.any { it.event == AuditEvent.PASSWORD_CHANGED })
    }

    @Test fun queuedCancelledRequestClearsPasswordWithoutEnteringTransaction() = runTest {
        val f = Fixture(this)
        f.bootstrapAndLogin()
        val actor = f.session()
        val hashing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.passwords.onCreate = { hashing.complete(Unit); release.await() }
        val first = launch { f.service.createAccount(actor, "first", original.toCharArray(), UserRole.VIEWER) }
        hashing.await()
        val secret = original.toCharArray()
        val second = launch { f.service.createAccount(actor, "second", secret, UserRole.VIEWER) }
        runCurrent()
        second.cancelAndJoin()
        assertTrue(secret.all { it == '\u0000' })
        release.complete(Unit)
        first.join()
        assertEquals(listOf("admin", "first"), f.store.db!!.accounts.map { it.username })
    }

    @Test fun cancelledLoginWriteNeverIssuesSessionEvenIfAuditCommitCompletes() = runTest {
        val f = Fixture(this)
        f.bootstrap()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.store.onWrite = { entered.complete(Unit); release.await() }
        val secret = original.toCharArray()
        val job = launch { f.service.signIn("admin", secret) }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertNull(f.service.state.value.session)
        assertTrue(secret.all { it == '\u0000' })
        assertEquals(AuditEvent.LOGIN_SUCCEEDED, f.store.db!!.audit.last().event)
    }

    private inner class Fixture(val test: TestScope) {
        val store = MemoryStorage()
        var elapsedOffset = 0L
        val clock = object : AccessClock {
            override fun elapsedMillis() = test.testScheduler.currentTime + elapsedOffset
            override fun epochMillis() = 1_000_000L + elapsedMillis()
            override fun bootCount() = 1
        }
        val passwords = FastPasswords()
        val service = restart()
        fun restart() = LocalAccountService(store, clock, passwords, test.backgroundScope)
        suspend fun bootstrap() = service.initializeAdministrator("admin", original.toCharArray())
        suspend fun login(password: String = original) = service.signIn("admin", password.toCharArray())
        suspend fun bootstrapAndLogin() { bootstrap(); login() }
        fun session() = requireNotNull(service.state.value.session) { service.state.value.toString() }
    }

    private class MemoryStorage : AccessStorage {
        var db: AccessDatabase? = null
        var failNextWrite = false
        var failRead = false
        var writes = 0
        var onWrite: (suspend () -> Unit)? = null
        override suspend fun read(): AccessDatabase? {
            if (failRead) throw IOException("simulated corrupt storage")
            return db?.let { AccessDatabaseCodec.decode(AccessDatabaseCodec.encode(it)) }
        }
        override suspend fun write(database: AccessDatabase) {
            if (failNextWrite) { failNextWrite = false; throw IOException("simulated disk failure") }
            onWrite?.invoke()
            db = AccessDatabaseCodec.decode(AccessDatabaseCodec.encode(database))
            writes++
        }
    }

    /** Fast deterministic verifier for service transaction tests; production KDF has a separate test. */
    private class FastPasswords : AccessPasswords {
        var counter = 0
        var onCreate: (suspend () -> Unit)? = null
        override suspend fun create(password: CharArray): PasswordVerifier {
            onCreate?.invoke()
            val salt = ByteArray(16) { (++counter).toByte() }
            return PasswordVerifier(salt, hash(password, salt))
        }
        override suspend fun verify(password: CharArray, verifier: PasswordVerifier) =
            MessageDigest.isEqual(hash(password, verifier.salt), verifier.hash)
        private fun hash(password: CharArray, salt: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(salt + String(password).toByteArray())
    }
}
