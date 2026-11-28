package com.mamadrones.gcs.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MissionStorageMigrationTest {
    @Test fun `legacy draft recovery and library are migrated and remain readable`() = runBlocking {
        val draft = MissionDraft("North field", listOf(DraftWaypoint("p1", -35.1, 149.2)))
        val recovery = MissionDraft("Unsaved outline", keepInFence = listOf(
            DraftWaypoint("v1", -35.2, 149.3), DraftWaypoint("v2", -35.21, 149.31)))
        val entry = MissionLibraryEntry("saved-1", draft, 1234)
        val draftKey = stringPreferencesKey("draft")
        val recoveryKey = stringPreferencesKey("working_copy")
        val libraryKey = stringPreferencesKey("library")
        val store = MemoryPreferencesDataStore(mutablePreferencesOf().apply {
            this[draftKey] = MissionDraftCodec.encode(draft)
            this[recoveryKey] = MissionDraftCodec.encode(recovery)
            this[libraryKey] = MissionLibraryCodec.encode(listOf(entry))
        })
        val cipher = TestMissionPayloadCipher()
        val repository = LocalMissionDraftRepository(store, cipher)

        assertEquals(draft, repository.load())
        assertEquals(recovery, repository.loadRecovery())
        assertEquals(listOf(entry), repository.loadLibrary())

        val migrated = store.data.first()
        listOf(
            Triple(draftKey, "draft", "149.2"),
            Triple(recoveryKey, "working_copy", "149.2"),
            Triple(libraryKey, "library", "149.2"),
        ).forEach { (key, slot, sensitiveCoordinate) ->
            val envelope = requireNotNull(migrated[key])
            assertTrue("$slot should be encrypted after its successful read", cipher.isEncrypted(envelope))
            assertFalse(envelope.contains(sensitiveCoordinate))
        }
        val reopened = LocalMissionDraftRepository(store, cipher)
        assertEquals(draft, reopened.load())
        assertEquals(recovery, reopened.loadRecovery())
        assertEquals(listOf(entry), reopened.loadLibrary())
    }

    @Test fun `new writes encrypt each slot and saving draft clears encrypted recovery`() = runBlocking {
        val store = MemoryPreferencesDataStore(mutablePreferencesOf())
        val cipher = TestMissionPayloadCipher()
        val repository = LocalMissionDraftRepository(store, cipher)
        val saved = MissionDraft("Saved", listOf(DraftWaypoint("p", 12.5, 77.6)))
        val recovery = MissionDraft("Recovery", keepInFence = listOf(DraftWaypoint("v", 12.6, 77.7)))

        repository.saveRecovery(recovery)
        val recoveryValue = requireNotNull(store.data.first()[stringPreferencesKey("working_copy")])
        assertTrue(cipher.isEncrypted(recoveryValue))
        repository.saveToLibrary(saved)
        val libraryValue = requireNotNull(store.data.first()[stringPreferencesKey("library")])
        assertTrue(cipher.isEncrypted(libraryValue))
        repository.save(saved)

        val values = store.data.first()
        assertTrue(cipher.isEncrypted(requireNotNull(values[stringPreferencesKey("draft")])) )
        assertTrue(cipher.isEncrypted(requireNotNull(values[stringPreferencesKey("library")])) )
        assertNull(values[stringPreferencesKey("working_copy")])
        assertEquals(saved, repository.load())
        assertEquals(listOf("Saved"), repository.loadLibrary().map { it.draft.name })
        assertNull(repository.loadRecovery())
    }

    @Test fun `failed encryption or authentication preserves the stored value`() = runBlocking {
        val key = stringPreferencesKey("draft")
        val legacy = MissionDraftCodec.encode(MissionDraft("Keep me", listOf(DraftWaypoint("p", 1.0, 2.0))))
        val store = MemoryPreferencesDataStore(mutablePreferencesOf().apply { this[key] = legacy })
        val failing = object : MissionPayloadCipher by TestMissionPayloadCipher() {
            override fun encrypt(slot: String, plaintext: String): String = error("simulated Keystore failure")
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { LocalMissionDraftRepository(store, failing).load() }
        }
        assertEquals(legacy, store.data.first()[key])

        val workingCipher = TestMissionPayloadCipher()
        val envelope = workingCipher.encrypt("draft", legacy)
        val corrupt = envelope.dropLast(1) + if (envelope.last() == 'A') "B" else "A"
        store.edit { it[key] = corrupt }
        assertThrows(Exception::class.java) {
            runBlocking { LocalMissionDraftRepository(store, workingCipher).load() }
        }
        assertEquals(corrupt, store.data.first()[key])
    }

    @Test fun `invalid legacy route is not replaced during migration`() = runBlocking {
        val key = stringPreferencesKey("draft")
        val invalidLegacy = "not a valid mission codec"
        val store = MemoryPreferencesDataStore(mutablePreferencesOf().apply { this[key] = invalidLegacy })
        assertThrows(Exception::class.java) {
            runBlocking { LocalMissionDraftRepository(store, TestMissionPayloadCipher()).load() }
        }
        assertEquals(invalidLegacy, store.data.first()[key])
    }
}

private class TestMissionPayloadCipher : MissionPayloadCipher {
    private val prefix = "!MAMA-GCS-ROUTE!1:"
    override fun isEncrypted(value: String): Boolean = value.startsWith("!MAMA-GCS-ROUTE!")
    override fun encrypt(slot: String, plaintext: String): String = prefix + Base64.getEncoder().encodeToString(
        "$slot\n$plaintext".toByteArray(Charsets.UTF_8))
    override fun decrypt(slot: String, envelope: String): String {
        require(envelope.startsWith(prefix))
        val decoded = Base64.getDecoder().decode(envelope.removePrefix(prefix)).toString(Charsets.UTF_8)
        val separator = decoded.indexOf('\n')
        require(separator > 0 && decoded.substring(0, separator) == slot)
        return decoded.substring(separator + 1)
    }
}

private class MemoryPreferencesDataStore(initial: Preferences) : DataStore<Preferences> {
    private val current = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: Flow<Preferences> = current

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(current.value).also { current.value = it }
    }
}
