package com.mamadrones.gcs.data.local.backup

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.MissionRouteBackup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedMissionBackupCodecTest {
    private val codec = EncryptedMissionBackupCodec()
    private val password = "correct horse battery staple"
    private val fixture = MissionRouteBackup(
        savedDraft = MissionDraft("Synthetic saved", listOf(DraftWaypoint("s", -35.1, 149.1)),
            listOf(DraftWaypoint("f1", -35.0, 149.0), DraftWaypoint("f2", -35.0, 149.2), DraftWaypoint("f3", -35.2, 149.1))),
        recoveryDraft = MissionDraft("Synthetic unsaved", listOf(DraftWaypoint("r", 1.25, 2.5))),
        library = listOf(MissionLibraryEntry("library-1", MissionDraft("Synthetic library"), 123L)),
    )

    @Test fun `encrypted file roundtrips all route stores without exposing route text`() {
        val encrypted = codec.encode(fixture, password.toCharArray())
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("Synthetic saved"))
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("149.1"))

        val restored = codec.decode(encrypted, password.toCharArray())

        assertEquals(fixture, restored)
    }

    @Test fun `wrong passphrase and ciphertext tampering are rejected`() {
        val encrypted = codec.encode(fixture, password.toCharArray())
        assertThrows(Exception::class.java) { codec.decode(encrypted, "incorrect password".toCharArray()) }

        val tampered = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { codec.decode(tampered, password.toCharArray()) }
    }

    @Test fun `header tampering truncation oversized input and trailing data are rejected`() {
        val encrypted = codec.encode(fixture, password.toCharArray())
        val changedIterationHeader = encrypted.copyOf().apply { this[10] = (this[10].toInt() xor 1).toByte() }
        val truncated = encrypted.copyOf(encrypted.size - 1)
        val trailing = encrypted + byteArrayOf(0)
        assertThrows(Exception::class.java) { codec.decode(changedIterationHeader, password.toCharArray()) }
        assertThrows(Exception::class.java) { codec.decode(truncated, password.toCharArray()) }
        assertThrows(Exception::class.java) { codec.decode(trailing, password.toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(ByteArray(EncryptedMissionBackupCodec.MAX_BACKUP_BYTES + 1), password.toCharArray())
        }
    }

    @Test fun `passphrase must meet bounds and is cleared by codec`() {
        val tooShort = "short".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { codec.encode(fixture, tooShort) }
        assertArrayEquals(CharArray(tooShort.size), tooShort)
    }

    @Test fun `backup without unsaved recovery remains distinguishable`() {
        val snapshot = MissionRouteBackup(MissionDraft("Only saved"), null, emptyList())
        val restored = codec.decode(codec.encode(snapshot, password.toCharArray()), password.toCharArray())
        assertEquals(snapshot, restored)
        assertTrue(restored.recoveryDraft == null)
    }
}
