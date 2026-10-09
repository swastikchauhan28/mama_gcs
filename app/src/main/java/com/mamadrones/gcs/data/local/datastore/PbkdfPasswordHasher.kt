package com.mamadrones.gcs.data.local.datastore

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PasswordVerifier(val salt: ByteArray, val hash: ByteArray)

internal interface AccessPasswords {
    suspend fun create(password: CharArray): PasswordVerifier
    suspend fun verify(password: CharArray, verifier: PasswordVerifier): Boolean
}

internal class PbkdfPasswordHasher : AccessPasswords {
    override suspend fun create(password: CharArray): PasswordVerifier = withContext(Dispatchers.Default) {
        val salt = ByteArray(AccessLimits.SALT_BYTES).also(SecureRandom()::nextBytes)
        PasswordVerifier(salt, derive(password, salt))
    }

    override suspend fun verify(password: CharArray, verifier: PasswordVerifier): Boolean = withContext(Dispatchers.Default) {
        val candidate = derive(password, verifier.salt)
        try { MessageDigest.isEqual(candidate, verifier.hash) } finally { candidate.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, AccessLimits.PBKDF2_ITERATIONS, AccessLimits.HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
