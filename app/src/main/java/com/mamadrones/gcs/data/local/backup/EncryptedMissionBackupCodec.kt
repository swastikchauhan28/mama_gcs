package com.mamadrones.gcs.data.local.backup

import com.mamadrones.gcs.data.local.datastore.MissionDraftCodec
import com.mamadrones.gcs.data.local.datastore.MissionLibraryCodec
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionRouteBackup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable encrypted file codec. Caller owns and must clear the returned encrypted bytes. */
class EncryptedMissionBackupCodec {
    /** Consumes and clears [passphrase] on success or failure. Run this CPU work off the main thread. */
    fun encode(backup: MissionRouteBackup, passphrase: CharArray): ByteArray {
        var plaintext: ByteArray? = null
        var salt: ByteArray? = null
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var header: ByteArray? = null
        try {
            validatePassphrase(passphrase)
            plaintext = encodePayload(backup)
            salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val encryptedLength = plaintext.size + TAG_BYTES
            header = buildHeader(salt, iv, encryptedLength)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            ciphertext = cipher.doFinal(plaintext)
            require(ciphertext.size == encryptedLength) { "Unexpected encrypted backup size." }
            val output = ByteArrayOutputStream(header.size + ciphertext.size)
            output.write(header)
            output.write(ciphertext)
            return output.toByteArray().also {
                require(it.size <= MAX_BACKUP_BYTES) { "Encrypted backup exceeds the file size limit." }
            }
        } finally {
            plaintext?.fill(0)
            salt?.fill(0)
            iv?.fill(0)
            ciphertext?.fill(0)
            header?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    /** Consumes and clears [passphrase] on success or failure. */
    fun decode(file: ByteArray, passphrase: CharArray): MissionRouteBackup {
        var salt: ByteArray? = null
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var plaintext: ByteArray? = null
        var header: ByteArray? = null
        try {
            validatePassphrase(passphrase)
            require(file.size in MIN_BACKUP_BYTES..MAX_BACKUP_BYTES) { "Backup file size is invalid." }
            val input = DataInputStream(ByteArrayInputStream(file))
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            require(magic.contentEquals(MAGIC)) { "File is not a Mama GCS backup." }
            val version = input.readUnsignedByte()
            val kdfId = input.readUnsignedByte()
            val iterations = input.readInt()
            require(version == VERSION && kdfId == KDF_PBKDF2_HMAC_SHA256 && iterations == ITERATIONS) {
                "Unsupported backup format or key-derivation settings."
            }
            salt = ByteArray(SALT_BYTES).also(input::readFully)
            iv = ByteArray(IV_BYTES).also(input::readFully)
            val ciphertextLength = input.readInt()
            require(ciphertextLength in TAG_BYTES..(MAX_PLAINTEXT_BYTES + TAG_BYTES)) {
                "Backup payload size is invalid."
            }
            require(input.available() == ciphertextLength) { "Backup is truncated or contains trailing data." }
            ciphertext = ByteArray(ciphertextLength).also(input::readFully)
            header = file.copyOfRange(0, HEADER_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            // Wrong passwords and modified authenticated data fail here without returning partial content.
            plaintext = cipher.doFinal(ciphertext)
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Backup payload exceeds its size limit." }
            return decodePayload(plaintext)
        } finally {
            salt?.fill(0)
            iv?.fill(0)
            ciphertext?.fill(0)
            plaintext?.fill(0)
            header?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    private fun encodePayload(backup: MissionRouteBackup): ByteArray {
        val savedDraft = MissionDraftCodec.encode(backup.savedDraft)
        val recoveryDraft = backup.recoveryDraft?.let(MissionDraftCodec::encode)
        val library = MissionLibraryCodec.encode(backup.library)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(PAYLOAD_VERSION)
            writeBoundedString(data, savedDraft, MAX_DRAFT_CHARS)
            data.writeBoolean(recoveryDraft != null)
            recoveryDraft?.let { writeBoundedString(data, it, MAX_DRAFT_CHARS) }
            writeBoundedString(data, library, MAX_LIBRARY_CHARS)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_PLAINTEXT_BYTES) { "Route data exceeds the backup size limit." }
        }
    }

    private fun decodePayload(plaintext: ByteArray): MissionRouteBackup {
        val input = DataInputStream(ByteArrayInputStream(plaintext))
        require(input.readInt() == PAYLOAD_VERSION) { "Unsupported route backup payload." }
        val savedDraft = MissionDraftCodec.decode(readBoundedString(input, MAX_DRAFT_CHARS))
        val recoveryDraft = if (input.readBoolean()) {
            MissionDraftCodec.decode(readBoundedString(input, MAX_DRAFT_CHARS))
        } else null
        val library = MissionLibraryCodec.decode(readBoundedString(input, MAX_LIBRARY_CHARS))
        require(input.available() == 0) { "Unexpected route backup payload data." }
        return MissionRouteBackup(savedDraft, recoveryDraft, library)
    }

    private fun writeBoundedString(output: DataOutputStream, value: String, maxChars: Int) {
        require(value.length <= maxChars) { "Route data exceeds the backup size limit." }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(bytes.size <= maxChars) { "Route data exceeds the backup size limit." }
            output.writeInt(bytes.size)
            output.write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun readBoundedString(input: DataInputStream, maxBytes: Int): String {
        val size = input.readInt()
        require(size in 0..maxBytes) { "Backup route field exceeds its size limit." }
        val bytes = ByteArray(size).also(input::readFully)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun buildHeader(salt: ByteArray, iv: ByteArray, ciphertextLength: Int): ByteArray {
        val output = ByteArrayOutputStream(HEADER_BYTES)
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(VERSION)
            data.writeByte(KDF_PBKDF2_HMAC_SHA256)
            data.writeInt(ITERATIONS)
            data.write(salt)
            data.write(iv)
            data.writeInt(ciphertextLength)
        }
        return output.toByteArray().also { check(it.size == HEADER_BYTES) }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        var keyBytes: ByteArray? = null
        return try {
            keyBytes = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            keyBytes?.fill(0)
        }
    }

    private fun validatePassphrase(passphrase: CharArray) {
        require(passphrase.size in MIN_PASSPHRASE_CHARS..MAX_PASSPHRASE_CHARS) {
            "Use a passphrase between $MIN_PASSPHRASE_CHARS and $MAX_PASSPHRASE_CHARS characters."
        }
    }

    companion object {
        const val MIN_PASSPHRASE_CHARS = 12
        const val MAX_PASSPHRASE_CHARS = 256
        const val MAX_BACKUP_BYTES = 4_300_000 + 128
        const val FILE_NAME = "Mama-GCS-route-backup.mgcsbackup"
        private const val VERSION = 1
        private const val PAYLOAD_VERSION = 1
        private const val KDF_PBKDF2_HMAC_SHA256 = 1
        private const val ITERATIONS = 600_000
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val MAX_PLAINTEXT_BYTES = 4_300_000
        private const val MAX_DRAFT_CHARS = 128_000
        private const val MAX_LIBRARY_CHARS = 4_000_000
        private val MAGIC = "MGCSBKP1".toByteArray(StandardCharsets.US_ASCII)
        private const val HEADER_BYTES = 8 + 1 + 1 + 4 + SALT_BYTES + IV_BYTES + 4
        private const val MIN_BACKUP_BYTES = HEADER_BYTES + TAG_BYTES
        private val random = SecureRandom()
    }
}
