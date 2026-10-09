package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.data.local.datastore.AccessLimits.KEY_ALIAS
import com.mamadrones.gcs.data.local.datastore.AccessLimits.ANDROID_KEYSTORE
import com.mamadrones.gcs.data.local.datastore.AccessLimits.TRANSFORMATION
import com.mamadrones.gcs.data.local.datastore.AccessLimits.DB_VERSION
import com.mamadrones.gcs.data.local.datastore.AccessLimits.GCM_IV_BYTES
import com.mamadrones.gcs.data.local.datastore.AccessLimits.GCM_TAG_BITS
import com.mamadrones.gcs.data.local.datastore.AccessLimits.GCM_TAG_BYTES
import com.mamadrones.gcs.data.local.datastore.AccessLimits.MAX_ENCRYPTED_BYTES
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.localAccessStore by preferencesDataStore(name = "local_access")

internal class EncryptedAccessStorage(private val context: Context) : AccessStorage {
    private val blobKey = stringPreferencesKey("encrypted_v1")

    override suspend fun read(): AccessDatabase? = withContext(Dispatchers.IO) {
        context.localAccessStore.data.first()[blobKey]?.let(::decryptDatabase)
    }

    override suspend fun write(database: AccessDatabase) = withContext(Dispatchers.IO) {
        val encrypted = encryptDatabase(database)
        context.localAccessStore.edit { it[blobKey] = encrypted }
        Unit
    }

    private fun encryptDatabase(db: AccessDatabase): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(create = true))
        cipher.updateAAD(context.packageName.toByteArray(Charsets.UTF_8))
        val plain = AccessDatabaseCodec.encode(db)
        val encrypted = try {
            require(1 + cipher.iv.size + GCM_TAG_BYTES + plain.size <= MAX_ENCRYPTED_BYTES) {
                "Local account data exceeds the supported size."
            }
            cipher.doFinal(plain)
        } finally { plain.fill(0) }
        val packed = ByteArray(1 + cipher.iv.size + encrypted.size)
        packed[0] = DB_VERSION.toByte()
        cipher.iv.copyInto(packed, 1)
        encrypted.copyInto(packed, 1 + cipher.iv.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    private fun decryptDatabase(blob: String): AccessDatabase {
        require(blob.length <= MAX_ENCRYPTED_BYTES * 2) { "Local account data exceeds the supported size." }
        val packed = Base64.getDecoder().decode(blob)
        require(packed.size in (1 + GCM_IV_BYTES + GCM_TAG_BYTES)..MAX_ENCRYPTED_BYTES &&
            packed[0].toInt() == DB_VERSION) { "Unsupported or damaged local account data." }
        val iv = packed.copyOfRange(1, 1 + GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(create = false), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(context.packageName.toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(packed.copyOfRange(1 + GCM_IV_BYTES, packed.size))
        return try { AccessDatabaseCodec.decode(plain) } finally { plain.fill(0) }
    }

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


}
