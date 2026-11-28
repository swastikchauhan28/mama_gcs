package com.mamadrones.gcs

import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.AndroidKeystoreMissionPayloadCipher
import com.mamadrones.gcs.data.local.datastore.MissionDraftCodec
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.security.KeyStore
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses synthetic values and an isolated Android Keystore alias; it never touches app route storage. */
class MissionStorageKeystoreTest {
    @Test
    fun encryptedDraftRoundTripsAndRejectsTamperingOrSlotSwaps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "mama_gcs_test_${System.currentTimeMillis()}"
        val cipher = AndroidKeystoreMissionPayloadCipher(context, alias)
        val draft = MissionDraft(
            "Synthetic test route",
            listOf(DraftWaypoint("test-point", 12.3456789, 77.1234567)),
        )
        val plaintext = MissionDraftCodec.encode(draft)
        try {
            val envelope = cipher.encrypt("draft", plaintext)
            assertTrue(cipher.isEncrypted(envelope))
            assertFalse(envelope.contains("Synthetic test route"))
            assertFalse(envelope.contains("12.3456789"))
            assertEquals(plaintext, AndroidKeystoreMissionPayloadCipher(context, alias).decrypt("draft", envelope))

            assertThrows(Exception::class.java) { cipher.decrypt("library", envelope) }

            val prefix = "!MAMA-GCS-ROUTE!1:"
            val packed = Base64.getDecoder().decode(envelope.removePrefix(prefix))
            packed[packed.lastIndex] = (packed.last().toInt() xor 0x01).toByte()
            val tampered = prefix + Base64.getEncoder().encodeToString(packed)
            assertThrows(Exception::class.java) { cipher.decrypt("draft", tampered) }
        } finally {
            val keyStore = KeyStore.getInstance(AndroidKeystoreMissionPayloadCipher.ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }
}
