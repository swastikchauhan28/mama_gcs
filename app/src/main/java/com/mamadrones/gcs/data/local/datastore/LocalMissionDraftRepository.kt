package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.repository.MissionDraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.missionDraftStore by preferencesDataStore(name = "mission_draft")

@Singleton
class LocalMissionDraftRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val cipher: MissionPayloadCipher,
) : MissionDraftRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.missionDraftStore,
        AndroidKeystoreMissionPayloadCipher(context),
    )

    private val draftKey = stringPreferencesKey("draft")
    private val recoveryKey = stringPreferencesKey("working_copy")
    private val libraryKey = stringPreferencesKey("library")
    private val libraryMutationMutex = Mutex()

    override suspend fun load(): MissionDraft = readAndMigrate(draftKey, SLOT_DRAFT, MissionDraftCodec::decode)
        ?: MissionDraft()

    override suspend fun save(draft: MissionDraft) {
        val encoded = MissionDraftCodec.encode(draft)
        val encrypted = cipher.encrypt(SLOT_DRAFT, encoded)
        store.edit {
            it[draftKey] = encrypted
            it.remove(recoveryKey)
        }
    }

    override suspend fun loadRecovery(): MissionDraft? = readAndMigrate(recoveryKey, SLOT_RECOVERY, MissionDraftCodec::decode)

    override suspend fun saveRecovery(draft: MissionDraft) {
        val encrypted = cipher.encrypt(SLOT_RECOVERY, MissionDraftCodec.encode(draft))
        store.edit { it[recoveryKey] = encrypted }
    }

    override suspend fun clearRecovery() {
        store.edit { it.remove(recoveryKey) }
    }

    override suspend fun loadLibrary(): List<MissionLibraryEntry> =
        readAndMigrate(libraryKey, SLOT_LIBRARY, MissionLibraryCodec::decode) ?: emptyList()

    override suspend fun saveToLibrary(draft: MissionDraft): MissionLibraryEntry = libraryMutationMutex.withLock {
        val normalizedName = draft.name.trim()
        require(normalizedName.isNotBlank() && normalizedName.length <= MissionDraft.MAX_NAME_LENGTH) {
            "Enter a valid route name."
        }
        val namedDraft = draft.copy(name = normalizedName)
        var created: MissionLibraryEntry? = null
        store.edit { preferences ->
            val entries = readStored(preferences, libraryKey, SLOT_LIBRARY, MissionLibraryCodec::decode) ?: emptyList()
            require(entries.none { it.draft.name.equals(normalizedName, ignoreCase = true) }) {
                "A route with this name is already in the library. Rename it or choose another name."
            }
            require(entries.size < MissionLibraryEntry.MAX_ENTRIES) {
                "The route library is full (${MissionLibraryEntry.MAX_ENTRIES} routes). Delete one before saving another."
            }
            val entry = MissionLibraryEntry(
                id = UUID.randomUUID().toString(),
                draft = namedDraft,
                savedAtEpochMillis = System.currentTimeMillis().coerceAtLeast(0)
            )
            preferences[libraryKey] = cipher.encrypt(SLOT_LIBRARY, MissionLibraryCodec.encode(entries + entry))
            created = entry
        }
        checkNotNull(created)
    }

    override suspend fun deleteFromLibrary(id: String) {
        libraryMutationMutex.withLock {
            require(id.isNotBlank())
            store.edit { preferences ->
                val entries = readStored(preferences, libraryKey, SLOT_LIBRARY, MissionLibraryCodec::decode) ?: emptyList()
                preferences[libraryKey] = cipher.encrypt(SLOT_LIBRARY,
                    MissionLibraryCodec.encode(entries.filterNot { it.id == id }))
            }
        }
    }

    private suspend fun <T> readAndMigrate(
        key: Preferences.Key<String>,
        slot: String,
        decode: (String) -> T,
    ): T? {
        var result: T? = null
        store.edit { preferences -> result = readStored(preferences, key, slot, decode) }
        return result
    }

    /** Validate legacy text before replacing it; DataStore aborts the transaction on any failure. */
    private fun <T> readStored(
        preferences: MutablePreferences,
        key: Preferences.Key<String>,
        slot: String,
        decode: (String) -> T,
    ): T? {
        val stored = preferences[key] ?: return null
        val plaintext = if (cipher.isEncrypted(stored)) {
            cipher.decrypt(slot, stored)
        } else {
            stored
        }
        val decoded = decode(plaintext)
        if (!cipher.isEncrypted(stored)) preferences[key] = cipher.encrypt(slot, stored)
        return decoded
    }

    private companion object {
        const val SLOT_DRAFT = "draft"
        const val SLOT_RECOVERY = "working_copy"
        const val SLOT_LIBRARY = "library"
    }
}
