package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.AccessAccount
import com.mamadrones.gcs.domain.model.AuditEvent
import com.mamadrones.gcs.domain.model.AuditRecord
import com.mamadrones.gcs.domain.model.UserRole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import com.mamadrones.gcs.data.local.datastore.AccessLimits.DB_VERSION
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_ACCOUNTS
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_AUDIT_RECORDS
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_AUDIT_TEXT
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_USERNAME_LENGTH
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_FAILED_ATTEMPTS
import com.mamadrones.gcs.data.local.datastore.AccessLimits.SALT_BYTES
import com.mamadrones.gcs.data.local.datastore.AccessLimits.HASH_BYTES

internal interface AccessStorage {
    suspend fun read(): AccessDatabase?
    suspend fun write(database: AccessDatabase)
}

internal interface AccessClock {
    fun elapsedMillis(): Long
    fun epochMillis(): Long
    fun bootCount(): Int
}

internal data class UserCredential(
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

internal data class AccessDatabase(
    val accounts: List<UserCredential> = emptyList(),
    val audit: List<AuditRecord> = emptyList(),
    val failedAttempts: Int = 0,
    val lockoutUntilElapsed: Long = 0L,
    val bootCount: Int = -1,
)


internal object AccessLimits {
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

/** Version 1 layout is retained so the previously shipped encrypted accounts remain readable. */
internal object AccessDatabaseCodec {
    fun encode(db: AccessDatabase): ByteArray = ByteArrayOutputStream().use { bytes ->
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

    fun decode(plain: ByteArray): AccessDatabase = DataInputStream(ByteArrayInputStream(plain)).use { input ->
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


}
