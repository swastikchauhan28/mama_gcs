package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.core.security.AuthorizationPolicy
import com.mamadrones.gcs.domain.model.AccessAccount
import com.mamadrones.gcs.domain.model.AccessSetupState
import com.mamadrones.gcs.domain.model.AccessState
import com.mamadrones.gcs.domain.model.AuditEvent
import com.mamadrones.gcs.domain.model.AuditRecord
import com.mamadrones.gcs.domain.model.Permission
import com.mamadrones.gcs.domain.model.UserRole
import com.mamadrones.gcs.domain.model.UserSession
import com.mamadrones.gcs.domain.repository.AccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.localAccessStore by preferencesDataStore(name = "local_access")

/**
 * Device-local accounts. Password verifiers use salted PBKDF2-HMAC-SHA256; the bounded account
 * and audit database is AES-GCM encrypted with a non-exportable Android Keystore key.
 * This does not authenticate a MAVLink peer or make a vehicle command safe.
 */
@Singleton
class LocalAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessRepository {
    private val blobKey = stringPreferencesKey("encrypted_v1")
    private val mutex = Mutex()
    private val policy = AuthorizationPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(AccessState())
    override val state = mutableState.asStateFlow()
    private var database = AccessDatabase()
    private var loaded = false
    private var activeSession: UserSession? = null

    init {
        scope.launch { mutex.withLock { loadDatabase() } }
    }

    override suspend fun initializeAdministrator(username: String, password: CharArray) = mutex.withLock {
        try {
            ensureLoaded()
            require(database.accounts.isEmpty()) { "The administrator account is already set up." }
            val canonical = validateUsername(username)
            validatePassword(password)
            val record = createCredential(canonical, password, UserRole.ADMIN)
            val updated = database.copy(accounts = listOf(record), failedAttempts = 0, lockoutUntilElapsed = 0L)
            commit(updated, AuditEvent.ADMIN_INITIALIZED, "SUCCESS", record.id, canonical)
            publish(AccessSetupState.READY, "Administrator created. Sign in to continue.")
        } catch (error: Exception) {
            publishError(error)
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun signIn(username: String, password: CharArray) = mutex.withLock {
        try {
            ensureLoaded()
            val nowElapsed = SystemClock.elapsedRealtime()
            val remaining = (database.lockoutUntilElapsed - nowElapsed).coerceAtLeast(0)
            if (remaining > 0) {
                password.fill('\u0000')
                mutableState.value = mutableState.value.copy(
                    lockedOutUntilMonotonicMillis = database.lockoutUntilElapsed,
                    error = "Sign-in is temporarily locked. Try again in ${((remaining + 999) / 1000)} seconds.",
                    message = null,
                )
                return@withLock
            }
            val canonical = username.trim().lowercase(Locale.ROOT).take(MAX_USERNAME_LENGTH)
            val account = database.accounts.firstOrNull { it.username == canonical }
            val candidate = withContext(Dispatchers.Default) {
                derive(password, account?.salt ?: UNKNOWN_USER_SALT)
            }
            val valid = account != null && account.enabled && MessageDigest.isEqual(candidate, account.passwordHash)
            candidate.fill(0)
            password.fill('\u0000')
            if (!valid) {
                val failures = database.failedAttempts + 1
                val lockout = if (failures >= MAX_FAILED_ATTEMPTS) nowElapsed + LOCKOUT_MILLIS else 0L
                val updated = database.copy(
                    failedAttempts = if (lockout > 0) 0 else failures,
                    lockoutUntilElapsed = lockout,
                    bootCount = currentBootCount(),
                )
                commit(updated, AuditEvent.LOGIN_FAILED, "DENIED", account?.id, canonical)
                publish(AccessSetupState.READY)
                mutableState.value = mutableState.value.copy(
                    error = if (lockout > 0) "Too many attempts. Sign-in is locked for one minute."
                    else "Username or password is incorrect.",
                    message = null,
                    lockedOutUntilMonotonicMillis = lockout.takeIf { it > 0 },
                )
                return@withLock
            }
            val verified = requireNotNull(account)
            val session = UserSession(
                userId = verified.id,
                role = verified.role,
                enabled = true,
                expiresAtMonotonicMillis = nowElapsed + SESSION_TTL_MILLIS,
            )
            activeSession = session
            val updated = database.copy(failedAttempts = 0, lockoutUntilElapsed = 0L, bootCount = currentBootCount())
            commit(updated, AuditEvent.LOGIN_SUCCEEDED, "SUCCESS", verified.id, canonical)
            publish(AccessSetupState.READY, message = "Signed in as ${verified.username}.")
        } catch (error: Exception) {
            password.fill('\u0000')
            publishError(error)
        }
    }

    override suspend fun signOut() = mutex.withLock {
        try {
            ensureLoaded()
            val session = authorizedSession(Permission.MONITOR)
            if (session != null) commit(
                database,
                AuditEvent.LOGOUT,
                "SUCCESS",
                session.userId,
                database.accounts.firstOrNull { it.id == session.userId }?.username,
            )
            activeSession = null
            publish(AccessSetupState.READY, message = "Signed out.")
        } catch (error: Exception) {
            publishError(error)
        }
    }

    override suspend fun createAccount(
        actor: UserSession,
        username: String,
        password: CharArray,
        role: UserRole,
    ) = mutex.withLock {
        try {
            ensureLoaded()
            require(authorizedSession(Permission.MANAGE_USERS, actor) != null) { "Administrator permission is required." }
            require(role == UserRole.OPERATOR || role == UserRole.VIEWER) {
                "Additional administrator accounts are not supported in this phase."
            }
            require(database.accounts.size < MAX_ACCOUNTS) { "The local account limit has been reached." }
            val canonical = validateUsername(username)
            validatePassword(password)
            require(database.accounts.none { it.username == canonical }) { "That username already exists." }
            val created = createCredential(canonical, password, role)
            commit(database.copy(accounts = database.accounts + created), AuditEvent.ACCOUNT_CREATED,
                "SUCCESS", requireNotNull(activeSession).userId, created.username)
            publish(AccessSetupState.READY, "${created.username} added as ${role.name.lowercase()}.")
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = safeMessage(error), message = null)
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun setAccountEnabled(actor: UserSession, accountId: String, enabled: Boolean) = mutex.withLock {
        try {
            ensureLoaded()
            require(authorizedSession(Permission.MANAGE_USERS, actor) != null) { "Administrator permission is required." }
            val account = database.accounts.firstOrNull { it.id == accountId }
                ?: error("The account no longer exists.")
            require(account.id != activeSession?.userId || enabled) { "Sign out before disabling this account." }
            require(enabled || account.role != UserRole.ADMIN || database.accounts.count {
                it.role == UserRole.ADMIN && it.enabled
            } > 1) { "At least one enabled administrator must remain." }
            if (account.enabled == enabled) return@withLock
            val updatedAccount = account.copy(enabled = enabled)
            val updated = database.copy(accounts = database.accounts.map { if (it.id == accountId) updatedAccount else it })
            commit(
                updated,
                if (enabled) AuditEvent.ACCOUNT_ENABLED else AuditEvent.ACCOUNT_DISABLED,
                "SUCCESS",
                requireNotNull(activeSession).userId,
                account.username,
            )
            publish(AccessSetupState.READY, "${account.username} ${if (enabled) "enabled" else "disabled"}.")
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = safeMessage(error), message = null)
        }
    }

    override suspend fun recordEndpointChangeRequest(actor: UserSession, endpoint: com.mamadrones.gcs.domain.model.UdpEndpoint?): Boolean = mutex.withLock {
        try {
            ensureLoaded()
            val session = authorizedSession(Permission.CONFIGURE, actor)
                ?: error("Administrator permission is required to change the UDP peer.")
            val description = endpoint?.let { "${it.remoteHost}:${it.remotePort} → UDP ${it.localPort}" } ?: "Cleared UDP peer"
            commit(
                database,
                AuditEvent.UDP_ENDPOINT_CHANGE_REQUESTED,
                "AUTHORIZED_REQUEST",
                session.userId,
                description,
            )
            publish(AccessSetupState.READY, "UDP endpoint change recorded in local audit.")
            true
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = safeMessage(error), message = null)
            false
        }
    }

    override fun clearMessage() { mutableState.value = mutableState.value.copy(message = null, error = null) }

    private suspend fun ensureLoaded() {
        if (!loaded) loadDatabase()
        check(mutableState.value.setup != AccessSetupState.STORAGE_UNAVAILABLE) {
            mutableState.value.error ?: "Secure local account storage is unavailable."
        }
    }

    private suspend fun loadDatabase() {
        try {
            val blob = context.localAccessStore.data.first()[blobKey]
            database = if (blob.isNullOrEmpty()) AccessDatabase() else decryptDatabase(blob)
            val currentBootCount = currentBootCount()
            if (currentBootCount >= 0 && database.bootCount != currentBootCount) {
                database = database.copy(failedAttempts = 0, lockoutUntilElapsed = 0L, bootCount = currentBootCount)
                persistDatabase(database)
            }
            loaded = true
            activeSession = null
            publish(if (database.accounts.isEmpty()) AccessSetupState.ADMIN_REQUIRED else AccessSetupState.READY)
        } catch (error: Exception) {
            loaded = true
            activeSession = null
            mutableState.value = AccessState(
                setup = AccessSetupState.STORAGE_UNAVAILABLE,
                error = "Secure account data could not be opened. App data was preserved; contact the app administrator.",
            )
        }
    }

    private suspend fun commit(
        next: AccessDatabase,
        event: AuditEvent,
        outcome: String,
        actorId: String?,
        subject: String?,
    ) {
        val record = AuditRecord(
            timestampEpochMillis = System.currentTimeMillis().coerceAtLeast(0),
            actorUserId = actorId,
            vehicleId = null,
            event = event,
            outcome = outcome,
            subject = subject?.take(MAX_AUDIT_TEXT),
        )
        val bounded = next.copy(audit = (next.audit + record).takeLast(MAX_AUDIT_RECORDS))
        persistDatabase(bounded)
        database = bounded
        loaded = true
    }

    private suspend fun persistDatabase(db: AccessDatabase) {
        val encrypted = withContext(Dispatchers.IO) { encryptDatabase(db) }
        context.localAccessStore.edit { it[blobKey] = encrypted }
    }

    private fun publish(setup: AccessSetupState, message: String? = null) {
        val session = activeSession?.takeIf { policy.allows(it, Permission.MONITOR, SystemClock.elapsedRealtime()) }
        if (activeSession != null && session == null) activeSession = null
        val current = mutableState.value
        mutableState.value = AccessState(
            setup = setup,
            accounts = database.accounts.map(UserCredential::publicAccount),
            session = session,
            audit = database.audit.asReversed().take(AUDIT_UI_LIMIT),
            message = message ?: current.message,
            lockedOutUntilMonotonicMillis = database.lockoutUntilElapsed.takeIf { it > SystemClock.elapsedRealtime() },
        )
    }

    private fun publishError(error: Exception) {
        if (!loaded || error is IOException || error is SecurityException || error is AEADBadTagException) {
            mutableState.value = AccessState(
                setup = AccessSetupState.STORAGE_UNAVAILABLE,
                error = safeMessage(error),
            )
        } else {
            mutableState.value = mutableState.value.copy(error = safeMessage(error), message = null)
        }
    }

    private fun authorizedSession(permission: Permission, candidate: UserSession? = activeSession): UserSession? {
        val current = activeSession ?: return null
        if (candidate?.userId != current.userId || !policy.allows(current, permission, SystemClock.elapsedRealtime())) {
            activeSession = null
            return null
        }
        if (database.accounts.none { it.id == current.userId && it.enabled }) {
            activeSession = null
            return null
        }
        return current
    }

    private suspend fun createCredential(username: String, password: CharArray, role: UserRole): UserCredential {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return UserCredential(UUID.randomUUID().toString(), username, role, true,
            System.currentTimeMillis().coerceAtLeast(0), salt,
            withContext(Dispatchers.Default) { derive(password, salt) })
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptDatabase(db: AccessDatabase): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(create = true))
        cipher.updateAAD(context.packageName.toByteArray(Charsets.UTF_8))
        val plain = encode(db)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val packed = ByteArray(1 + cipher.iv.size + encrypted.size)
        packed[0] = DB_VERSION.toByte()
        cipher.iv.copyInto(packed, 1)
        encrypted.copyInto(packed, 1 + cipher.iv.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    private fun decryptDatabase(blob: String): AccessDatabase {
        val packed = Base64.getDecoder().decode(blob)
        require(packed.size in (1 + GCM_IV_BYTES + GCM_TAG_BYTES)..MAX_ENCRYPTED_BYTES &&
            packed[0].toInt() == DB_VERSION) { "Unsupported or damaged local account data." }
        val iv = packed.copyOfRange(1, 1 + GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(create = false), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(context.packageName.toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(packed.copyOfRange(1 + GCM_IV_BYTES, packed.size))
        return try { decode(plain) } finally { plain.fill(0) }
    }

    private fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    private fun keystoreKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "The Android Keystore key for local accounts is missing." }
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encode(db: AccessDatabase): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(DB_VERSION)
            out.writeInt(db.accounts.size)
            db.accounts.forEach { account ->
                out.writeUTF(account.id)
                out.writeUTF(account.username)
                out.writeUTF(account.role.name)
                out.writeBoolean(account.enabled)
                out.writeLong(account.createdAtEpochMillis)
                out.writeInt(account.salt.size); out.write(account.salt)
                out.writeInt(account.passwordHash.size); out.write(account.passwordHash)
            }
            out.writeInt(db.audit.size)
            db.audit.forEach { record ->
                out.writeLong(record.timestampEpochMillis)
                out.writeBoolean(record.actorUserId != null); record.actorUserId?.let(out::writeUTF)
                out.writeBoolean(record.vehicleId != null); record.vehicleId?.let(out::writeUTF)
                out.writeUTF(record.event.name)
                out.writeUTF(record.outcome.take(MAX_AUDIT_TEXT))
                out.writeBoolean(record.subject != null); record.subject?.let { out.writeUTF(it.take(MAX_AUDIT_TEXT)) }
            }
            out.writeInt(db.failedAttempts)
            out.writeLong(db.lockoutUntilElapsed)
            out.writeInt(db.bootCount)
        }
        bytes.toByteArray()
    }

    private fun decode(plain: ByteArray): AccessDatabase = DataInputStream(ByteArrayInputStream(plain)).use { input ->
        require(input.readInt() == DB_VERSION) { "Unsupported local account data version." }
        val accountCount = input.readInt().also { require(it in 0..MAX_ACCOUNTS) }
        val accounts = List(accountCount) {
            val id = input.readUTF()
            val username = input.readUTF().also { require(it.length in 3..MAX_USERNAME_LENGTH) }
            val role = UserRole.valueOf(input.readUTF())
            val enabled = input.readBoolean()
            val created = input.readLong().also { require(it >= 0) }
            val salt = input.readBoundedBytes(SALT_BYTES)
            val hash = input.readBoundedBytes(HASH_BYTES)
            require(salt.size == SALT_BYTES && hash.size == HASH_BYTES)
            UserCredential(id, username, role, enabled, created, salt, hash)
        }
        require(accounts.map(UserCredential::username).distinct().size == accounts.size)
        val auditCount = input.readInt().also { require(it in 0..MAX_AUDIT_RECORDS) }
        val audit = List(auditCount) {
            val timestamp = input.readLong().also { require(it >= 0) }
            val actor = if (input.readBoolean()) input.readUTF() else null
            val vehicle = if (input.readBoolean()) input.readUTF() else null
            val event = AuditEvent.valueOf(input.readUTF())
            val outcome = input.readUTF().also { require(it.length <= MAX_AUDIT_TEXT) }
            val subject = if (input.readBoolean()) input.readUTF().also { require(it.length <= MAX_AUDIT_TEXT) } else null
            AuditRecord(timestamp, actor, vehicle, event, outcome, subject)
        }
        val failures = input.readInt().also { require(it in 0 until MAX_FAILED_ATTEMPTS) }
        val lockout = input.readLong().also { require(it >= 0) }
        val bootCount = input.readInt()
        require(input.available() == 0) { "Unexpected local account data." }
        AccessDatabase(accounts, audit, failures, lockout, bootCount)
    }

    private fun DataInputStream.readBoundedBytes(maximum: Int): ByteArray {
        val size = readInt().also { require(it in 0..maximum) }
        return ByteArray(size).also { readFully(it) }
    }

    private fun validateUsername(value: String): String {
        val normalized = value.trim().lowercase(Locale.ROOT)
        require(normalized.matches(USERNAME_PATTERN)) { "Username must be 3–32 letters, numbers, dots, dashes or underscores." }
        return normalized
    }

    private fun validatePassword(value: CharArray) {
        require(value.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Use a password with at least 12 characters and no more than 128."
        }
    }

    private fun safeMessage(error: Exception): String = when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "The account action could not be completed."
        is AEADBadTagException -> "Secure account data failed its integrity check. Existing app data was preserved."
        else -> "The secure account action failed. Check device security storage and try again."
    }

    private data class UserCredential(
        val id: String,
        val username: String,
        val role: UserRole,
        val enabled: Boolean,
        val createdAtEpochMillis: Long,
        val salt: ByteArray,
        val passwordHash: ByteArray,
    ) {
        fun publicAccount() = AccessAccount(id, username, role, enabled, createdAtEpochMillis)
    }

    private data class AccessDatabase(
        val accounts: List<UserCredential> = emptyList(),
        val audit: List<AuditRecord> = emptyList(),
        val failedAttempts: Int = 0,
        val lockoutUntilElapsed: Long = 0L,
        val bootCount: Int = -1,
    )

    private companion object {
        val USERNAME_PATTERN = Regex("[a-z0-9._-]{3,32}")
        val UNKNOWN_USER_SALT = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        const val KEY_ALIAS = "mama_gcs_local_access_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DB_VERSION = 1
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MAX_ENCRYPTED_BYTES = 256 * 1024
        const val MAX_ACCOUNTS = 50
        const val MAX_AUDIT_RECORDS = 500
        const val AUDIT_UI_LIMIT = 30
        const val MAX_AUDIT_TEXT = 128
        const val MAX_USERNAME_LENGTH = 32
        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 128
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 60_000L
        const val SESSION_TTL_MILLIS = 15 * 60_000L
        const val PBKDF2_ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
    }
}
