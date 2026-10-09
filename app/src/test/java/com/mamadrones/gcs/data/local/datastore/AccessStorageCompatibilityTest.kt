package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.AuditEvent
import com.mamadrones.gcs.domain.model.UserRole
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccessStorageCompatibilityTest {
    @Test fun readsAnIndependentlyEncodedVersionOneAccountAndAudit() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(1)
            it.writeInt(1)
            it.writeUTF("existing-id")
            it.writeUTF("admin")
            it.writeUTF("ADMIN")
            it.writeBoolean(true)
            it.writeLong(100)
            it.writeInt(16); it.write(ByteArray(16) { 7 })
            it.writeInt(32); it.write(ByteArray(32) { 8 })
            it.writeInt(1)
            it.writeLong(200)
            it.writeBoolean(true); it.writeUTF("existing-id")
            it.writeBoolean(false)
            it.writeUTF("LOGIN_SUCCEEDED")
            it.writeUTF("SUCCESS")
            it.writeBoolean(true); it.writeUTF("admin")
            it.writeInt(2)
            it.writeLong(0)
            it.writeInt(4)
        }
        val original = bytes.toByteArray()
        val decoded = AccessDatabaseCodec.decode(original)
        assertEquals("existing-id", decoded.accounts.single().id)
        assertEquals(UserRole.ADMIN, decoded.accounts.single().role)
        assertEquals(AuditEvent.LOGIN_SUCCEEDED, decoded.audit.single().event)
        assertEquals(2, decoded.failedAttempts)
        assertEquals(4, decoded.bootCount)
        assertArrayEquals(original, AccessDatabaseCodec.encode(decoded))
    }

    @Test fun rejectsInvalidCountsTrailingDataAndUnknownVersions() {
        val valid = AccessDatabaseCodec.encode(AccessDatabase())
        fun rejects(bytes: ByteArray) {
            assertThrows(IllegalArgumentException::class.java) { AccessDatabaseCodec.decode(bytes) }
        }
        rejects(valid + byteArrayOf(0))
        rejects(valid.copyOf().apply { this[3] = 2 })
        rejects(valid.copyOf().apply { this[4] = 127 })
    }

    @Test fun productionPbkdfVerifierUsesDistinctSaltAndRejectsWrongPasswords() = runBlocking {
        val hasher = PbkdfPasswordHasher()
        val password = "correct horse battery".toCharArray()
        try {
            val first = hasher.create(password)
            val second = hasher.create(password)
            assertEquals(16, first.salt.size)
            assertEquals(32, first.hash.size)
            assertFalse(first.salt.contentEquals(second.salt))
            assertFalse(first.hash.contentEquals(second.hash))
            assertTrue(hasher.verify(password, first))
            assertFalse(hasher.verify("incorrect password".toCharArray(), first))
        } finally { password.fill('\u0000') }
    }
}
