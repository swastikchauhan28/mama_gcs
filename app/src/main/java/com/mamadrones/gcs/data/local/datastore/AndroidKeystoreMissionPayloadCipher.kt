package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts one bounded DataStore value, binding it to its logical slot to prevent value swapping. */
internal interface MissionPayloadCipher {
    fun isEncrypted(value: String): Boolean
    fun encrypt(slot: String, plaintext: String): String
    fun decrypt(slot: String, envelope: String): String
}

internal class AndroidKeystoreMissionPayloadCipher(
    context: Context,
    private val keyAlias: String = KEY_ALIAS,
) : MissionPayloadCipher {
    private val packageName = context.applicationContext.packageName

    override fun isEncrypted(value: String): Boolean = value.startsWith(ENVELOPE_PREFIX_ROOT)

    override fun encrypt(slot: String, plaintext: String): String {
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= MAX_PLAINTEXT_BYTES) { "Mission storage value exceeds its size limit." }
        val aad = associatedData(slot)
        var ciphertextToWipe: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(create = true))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plainBytes)
            ciphertextToWipe = ciphertext
            val packed = ByteArray(cipher.iv.size + ciphertext.size)
            cipher.iv.copyInto(packed)
            ciphertext.copyInto(packed, cipher.iv.size)
            try {
                ENVELOPE_PREFIX + Base64.getEncoder().encodeToString(packed)
            } finally {
                packed.fill(0)
            }
        } finally {
            plainBytes.fill(0)
            aad.fill(0)
            ciphertextToWipe?.fill(0)
        }
    }

    override fun decrypt(slot: String, envelope: String): String {
        require(envelope.startsWith(ENVELOPE_PREFIX)) { "Unsupported encrypted mission data version." }
        require(envelope.length <= MAX_ENVELOPE_CHARS) { "Encrypted mission data exceeds its size limit." }
        val packed = Base64.getDecoder().decode(envelope.removePrefix(ENVELOPE_PREFIX))
        require(packed.size in (GCM_IV_BYTES + GCM_TAG_BYTES)..MAX_PACKED_BYTES) {
            "Encrypted mission data is incomplete."
        }
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val aad = associatedData(slot)
        var plaintextToWipe: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(create = false), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(ciphertext)
            plaintextToWipe = plaintext
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Decrypted mission data exceeds its size limit." }
            String(plaintext, Charsets.UTF_8)
        } finally {
            packed.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
            aad.fill(0)
            plaintextToWipe?.fill(0)
        }
    }

    private fun associatedData(slot: String): ByteArray {
        require(slot in ALLOWED_SLOTS) { "Unknown mission storage slot." }
        return "$packageName|mission-storage|$slot|1".toByteArray(Charsets.UTF_8)
    }

    private fun keystoreKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        check(create) { "The Android Keystore key for saved routes is missing." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    companion object {
        const val KEY_ALIAS = "mama_gcs_mission_store_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val ENVELOPE_PREFIX_ROOT = "!MAMA-GCS-ROUTE!"
        private const val ENVELOPE_PREFIX = "${ENVELOPE_PREFIX_ROOT}1:"
        private const val MAX_PLAINTEXT_BYTES = 4_000_000
        private const val MAX_PACKED_BYTES = MAX_PLAINTEXT_BYTES + GCM_IV_BYTES + GCM_TAG_BYTES
        private const val MAX_ENVELOPE_CHARS = ENVELOPE_PREFIX.length + ((MAX_PACKED_BYTES + 2) / 3) * 4
        private val ALLOWED_SLOTS = setOf("draft", "working_copy", "library")
    }
}
