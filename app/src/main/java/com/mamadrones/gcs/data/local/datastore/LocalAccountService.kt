package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.core.security.AuthorizationPolicy
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.domain.repository.AccessRepository
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local account transactions, independent of Android so failure and expiry paths can be tested. */
internal class LocalAccountService(
    private val storage: AccessStorage,
    private val clock: AccessClock,
    private val passwords: AccessPasswords,
    private val scope: CoroutineScope,
) : AccessRepository {
    private val mutex = Mutex()
    private val policy = AuthorizationPolicy()
    private val mutableState = MutableStateFlow(AccessState())
    override val state = mutableState.asStateFlow()
    private var database = AccessDatabase()
    private var loaded = false
    private var storageFailed = false
    private var activeSession: UserSession? = null
    private var expiryJob: Job? = null
    private val unknownVerifier = PasswordVerifier(
        ByteArray(AccessLimits.SALT_BYTES).also(SecureRandom()::nextBytes),
        ByteArray(AccessLimits.HASH_BYTES).also(SecureRandom()::nextBytes),
    )

    init { scope.launch { action(Unit) {} } }

    override suspend fun initializeAdministrator(username: String, password: CharArray) =
        action(Unit, password) {
            requireInput(database.accounts.isEmpty(), "The administrator account is already set up.")
            val canonical = validateUsername(username)
            validatePassword(password)
            val credential = newCredential(canonical, password, UserRole.ADMIN)
            commit(database.copy(accounts = listOf(credential)), AuditEvent.ADMIN_INITIALIZED,
                "SUCCESS", credential.id, credential.username)
            publish(message = "Administrator created. Sign in to continue.")
        }

    override suspend fun signIn(username: String, password: CharArray) = action(Unit, password) {
        requireInput(database.accounts.isNotEmpty(), "Create the administrator account first.")
        requireInput(activeSession == null, "Sign out before changing accounts.")
        checkLockout()
        val canonical = username.trim().lowercase(Locale.ROOT)
        // Do not truncate a long username into an existing account's valid name.
        val account = database.accounts.firstOrNull { it.username == canonical }
        val valid = if (password.size <= AccessLimits.MAX_PASSWORD_LENGTH) {
            passwords.verify(password, account?.verifier() ?: unknownVerifier)
        } else false
        if (!valid || account == null || !account.enabled) {
            failedCredential(AuditEvent.LOGIN_FAILED, account?.id, canonical)
            return@action
        }
        // A durable success record is required before any session is issued.
        commit(database.copy(failedAttempts = 0, lockoutUntilElapsed = 0),
            AuditEvent.LOGIN_SUCCEEDED, "SUCCESS", account.id, account.username)
        activeSession = UserSession(account.id, account.role, true,
            clock.elapsedMillis() + AccessLimits.SESSION_TTL_MILLIS, UUID.randomUUID().toString())
        scheduleExpiry(requireNotNull(activeSession))
        publish(message = "Signed in as ${account.username}.")
    }

    override suspend fun signOut() = action(Unit) {
        val previous = activeSession
        // Revocation cannot depend on a successful audit or storage write.
        revokeSession()
        publish(message = "Signed out.")
        if (previous != null) commit(database, AuditEvent.LOGOUT, "SUCCESS", previous.userId,
            database.accounts.firstOrNull { it.id == previous.userId }?.username)
        publish(message = "Signed out.")
    }

    override suspend fun refreshSession() = action(Unit) {
        publish(message = mutableState.value.message, error = mutableState.value.error)
    }

    override suspend fun changePassword(actor: UserSession, currentPassword: CharArray, newPassword: CharArray) =
        action(Unit, currentPassword, newPassword) {
            val session = requireSession(Permission.MONITOR, actor)
            checkLockout()
            val account = database.accounts.single { it.id == session.userId }
            val valid = currentPassword.size <= AccessLimits.MAX_PASSWORD_LENGTH &&
                passwords.verify(currentPassword, account.verifier())
            if (!valid) {
                failedCredential(AuditEvent.PASSWORD_CHANGE_FAILED, account.id, account.username)
                return@action
            }
            validatePassword(newPassword)
            requireInput(!currentPassword.contentEquals(newPassword), "Choose a different new password.")
            val verifier = passwords.create(newPassword)
            // Hashing can take time; re-check the same session before accepting the mutation.
            requireSession(Permission.MONITOR, actor)
            val updated = account.copy(salt = verifier.salt, passwordHash = verifier.hash)
            revokeSession()
            publish(message = "Updating password…")
            commit(database.copy(
                accounts = database.accounts.map { if (it.id == updated.id) updated else it },
                failedAttempts = 0, lockoutUntilElapsed = 0,
            ), AuditEvent.PASSWORD_CHANGED, "SUCCESS", account.id, account.username)
            publish(message = "Password changed. Sign in with your new password.")
        }

    override suspend fun createAccount(actor: UserSession, username: String, password: CharArray, role: UserRole) =
        action(Unit, password) {
            requireSession(Permission.MANAGE_USERS, actor)
            requireInput(role != UserRole.ADMIN, "Additional administrator accounts are not supported yet.")
            requireInput(database.accounts.size < AccessLimits.MAX_ACCOUNTS, "The local account limit has been reached.")
            val canonical = validateUsername(username)
            validatePassword(password)
            requireInput(database.accounts.none { it.username == canonical }, "That username already exists.")
            val credential = newCredential(canonical, password, role)
            val session = requireSession(Permission.MANAGE_USERS, actor)
            commit(database.copy(accounts = database.accounts + credential), AuditEvent.ACCOUNT_CREATED,
                "SUCCESS", session.userId, canonical)
            publish(message = "$canonical added as ${role.name.lowercase(Locale.ROOT)}.")
        }

    override suspend fun setAccountEnabled(actor: UserSession, accountId: String, enabled: Boolean) = action(Unit) {
        val session = requireSession(Permission.MANAGE_USERS, actor)
        val account = database.accounts.firstOrNull { it.id == accountId }
            ?: reject("The account no longer exists.")
        requireInput(account.id != session.userId || enabled, "You cannot disable your current account.")
        requireInput(enabled || account.role != UserRole.ADMIN || database.accounts.count {
            it.role == UserRole.ADMIN && it.enabled
        } > 1, "At least one enabled administrator must remain.")
        if (account.enabled != enabled) {
            commit(database.copy(accounts = database.accounts.map {
                if (it.id == accountId) it.copy(enabled = enabled) else it
            }), if (enabled) AuditEvent.ACCOUNT_ENABLED else AuditEvent.ACCOUNT_DISABLED,
                "SUCCESS", session.userId, account.username)
        }
        publish(message = "${account.username} ${if (enabled) "enabled" else "disabled"}.")
    }

    override suspend fun recordEndpointChangeRequest(actor: UserSession, endpoint: UdpEndpoint?): Boolean =
        action(false) {
            val session = requireSession(Permission.CONFIGURE, actor)
            val description = endpoint?.let { "${it.remoteHost}:${it.remotePort} → UDP ${it.localPort}" }
                ?: "Clear UDP peer"
            commit(database, AuditEvent.UDP_ENDPOINT_CHANGE_REQUESTED, "AUTHORIZED_REQUEST", session.userId, description)
            publish(message = "UDP endpoint change recorded in local audit.")
            true
        }

    override fun clearMessage() { mutableState.update { it.copy(message = null, error = null) } }

    private suspend fun <T> action(fallback: T, vararg secrets: CharArray, block: suspend () -> T): T {
        // Includes cancellation while waiting for another transaction's mutex.
        try { return mutex.withLock { actionLocked(fallback, block) } }
        finally { secrets.forEach { it.fill('\u0000') } }
    }

    private suspend fun <T> actionLocked(fallback: T, block: suspend () -> T): T {
        mutableState.update { it.copy(busy = true) }
        return try {
            ensureLoaded()
            expireIfNeeded()
            block()
        } catch (cancelled: CancellationException) {
            revokeSession()
            publish(message = "Account action interrupted. Sign in again.")
            throw cancelled
        } catch (rejected: AccessRejected) {
            publish(error = rejected.message)
            fallback
        } catch (_: Exception) {
            revokeSession()
            storageFailed = true
            publish(error = "Secure account storage is unavailable. You are signed out. Existing data was preserved; restart the app to try again.")
            fallback
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    private suspend fun ensureLoaded() {
        if (storageFailed) reject("Secure account storage is unavailable. Restart the app to try again.")
        if (loaded) return
        database = storage.read() ?: AccessDatabase()
        val boot = clock.bootCount()
        if (boot >= 0 && boot != database.bootCount) {
            database = database.copy(failedAttempts = 0, lockoutUntilElapsed = 0, bootCount = boot)
        } else if (boot < 0) {
            // With no boot identifier, a prior uptime must not create an unbounded lockout.
            database = database.copy(lockoutUntilElapsed = database.lockoutUntilElapsed.coerceAtMost(
                clock.elapsedMillis() + AccessLimits.LOCKOUT_MILLIS))
        }
        loaded = true
        publish()
    }

    private suspend fun expireIfNeeded() {
        val previous = activeSession ?: return
        if (!policy.allows(previous, Permission.MONITOR, clock.elapsedMillis())) {
            revokeSession()
            publish(message = "Session expired. Sign in again.")
            commit(database, AuditEvent.SESSION_EXPIRED, "SUCCESS", previous.userId,
                database.accounts.firstOrNull { it.id == previous.userId }?.username)
            publish(message = "Session expired. Sign in again.")
        }
    }

    private fun requireSession(permission: Permission, candidate: UserSession): UserSession {
        val current = activeSession
        requireInput(current != null && current == candidate &&
            policy.allows(current, permission, clock.elapsedMillis()) &&
            database.accounts.any { it.id == current.userId && it.enabled },
            "Your session does not allow this action. Sign in with the required account.")
        return requireNotNull(current)
    }

    private fun scheduleExpiry(session: UserSession) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay((session.expiresAtMonotonicMillis - clock.elapsedMillis()).coerceAtLeast(1))
            mutex.withLock {
                if (activeSession != session) return@withLock
                expiryJob = null
                actionLocked(Unit) {}
                if (activeSession == session) scheduleExpiry(session)
            }
        }
    }

    private fun revokeSession() {
        activeSession = null
        expiryJob?.cancel()
        expiryJob = null
    }

    private fun checkLockout() {
        val remaining = database.lockoutUntilElapsed - clock.elapsedMillis()
        requireInput(remaining <= 0, "Sign-in is temporarily locked. Try again in ${(remaining + 999) / 1000} seconds.")
    }

    private suspend fun failedCredential(event: AuditEvent, actorId: String?, subject: String) {
        val failures = database.failedAttempts + 1
        val locked = failures >= AccessLimits.MAX_FAILED_ATTEMPTS
        if (locked) revokeSession()
        commit(database.copy(
            failedAttempts = if (locked) 0 else failures,
            lockoutUntilElapsed = if (locked) clock.elapsedMillis() + AccessLimits.LOCKOUT_MILLIS else 0,
            bootCount = clock.bootCount(),
        ), event, "DENIED", actorId, subject)
        publish(error = if (locked) "Too many attempts. Sign-in is locked for one minute."
            else if (event == AuditEvent.PASSWORD_CHANGE_FAILED) "Current password is incorrect."
            else "Username or password is incorrect.")
    }

    private suspend fun commit(next: AccessDatabase, event: AuditEvent, outcome: String, actorId: String?, subject: String?) {
        val record = AuditRecord(clock.epochMillis().coerceAtLeast(0), actorId, null, event, outcome,
            subject?.take(AccessLimits.MAX_AUDIT_TEXT))
        val updated = next.copy(audit = (next.audit + record).takeLast(AccessLimits.MAX_AUDIT_RECORDS),
            bootCount = clock.bootCount())
        // DataStore commits atomically. Finish updating the memory snapshot even when navigation
        // cancels the caller at the same moment that the durable write completes.
        withContext(NonCancellable) {
            storage.write(updated)
            database = updated
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun newCredential(username: String, password: CharArray, role: UserRole): UserCredential {
        val verifier = passwords.create(password)
        return UserCredential(UUID.randomUUID().toString(), username, role, true,
            clock.epochMillis().coerceAtLeast(0), verifier.salt, verifier.hash)
    }

    private fun publish(message: String? = null, error: String? = null) {
        val session = activeSession
        val admin = session?.role == UserRole.ADMIN
        mutableState.value = AccessState(
            setup = if (storageFailed) AccessSetupState.STORAGE_UNAVAILABLE
                else if (!loaded) AccessSetupState.LOADING
                else if (database.accounts.isEmpty()) AccessSetupState.ADMIN_REQUIRED else AccessSetupState.READY,
            accounts = if (storageFailed) emptyList() else database.accounts
                .filter { admin || it.id == session?.userId }.map(UserCredential::publicAccount),
            session = session,
            audit = if (admin && !storageFailed) database.audit.asReversed().take(AccessLimits.AUDIT_UI_LIMIT) else emptyList(),
            message = message,
            error = error,
            lockedOutUntilMonotonicMillis = database.lockoutUntilElapsed.takeIf { it > clock.elapsedMillis() },
            busy = mutableState.value.busy,
        )
    }

    private fun validateUsername(value: String): String {
        val canonical = value.trim().lowercase(Locale.ROOT)
        requireInput(canonical.matches(Regex("[a-z0-9._-]{3,32}")),
            "Username must be 3–32 letters, numbers, dots, dashes or underscores.")
        return canonical
    }

    private fun validatePassword(value: CharArray) = requireInput(
        value.size in AccessLimits.MIN_PASSWORD_LENGTH..AccessLimits.MAX_PASSWORD_LENGTH,
        "Use a password with at least 12 characters and no more than 128.",
    )

    private fun UserCredential.verifier() = PasswordVerifier(salt, passwordHash)
    private fun requireInput(allowed: Boolean, message: String) { if (!allowed) reject(message) }
    private fun reject(message: String): Nothing = throw AccessRejected(message)
    private class AccessRejected(message: String) : Exception(message)
}
