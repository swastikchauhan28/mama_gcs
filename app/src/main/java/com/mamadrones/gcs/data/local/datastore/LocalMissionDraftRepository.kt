package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.first

private val Context.missionDraftStore by preferencesDataStore(name = "mission_draft")

@Singleton
class LocalMissionDraftRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : MissionDraftRepository {
    private val draftKey = stringPreferencesKey("draft")
    private val recoveryKey = stringPreferencesKey("working_copy")
    private val libraryKey = stringPreferencesKey("library")
    private val libraryMutationMutex = Mutex()

    override suspend fun load(): MissionDraft = context.missionDraftStore.data.first()[draftKey]
        ?.let(MissionDraftCodec::decode) ?: MissionDraft()

    override suspend fun save(draft: MissionDraft) {
        val encoded = MissionDraftCodec.encode(draft)
        context.missionDraftStore.edit {
            it[draftKey] = encoded
            it.remove(recoveryKey)
        }
    }

    override suspend fun loadRecovery(): MissionDraft? = context.missionDraftStore.data.first()[recoveryKey]
        ?.let(MissionDraftCodec::decode)

    override suspend fun saveRecovery(draft: MissionDraft) {
        val encoded = MissionDraftCodec.encode(draft)
        context.missionDraftStore.edit { it[recoveryKey] = encoded }
    }

    override suspend fun clearRecovery() {
        context.missionDraftStore.edit { it.remove(recoveryKey) }
    }

    override suspend fun loadLibrary(): List<MissionLibraryEntry> = context.missionDraftStore.data.first()[libraryKey]
        ?.let(MissionLibraryCodec::decode) ?: emptyList()

    override suspend fun saveToLibrary(draft: MissionDraft): MissionLibraryEntry = libraryMutationMutex.withLock {
        val normalizedName = draft.name.trim()
        require(normalizedName.isNotBlank() && normalizedName.length <= MissionDraft.MAX_NAME_LENGTH) {
            "Enter a valid route name."
        }
        val namedDraft = draft.copy(name = normalizedName)
        var created: MissionLibraryEntry? = null
        context.missionDraftStore.edit { preferences ->
            val entries = preferences[libraryKey]?.let(MissionLibraryCodec::decode) ?: emptyList()
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
            preferences[libraryKey] = MissionLibraryCodec.encode(entries + entry)
            created = entry
        }
        checkNotNull(created)
    }

    override suspend fun deleteFromLibrary(id: String) {
        libraryMutationMutex.withLock {
            require(id.isNotBlank())
            context.missionDraftStore.edit { preferences ->
                val entries = preferences[libraryKey]?.let(MissionLibraryCodec::decode) ?: emptyList()
                preferences[libraryKey] = MissionLibraryCodec.encode(entries.filterNot { it.id == id })
            }
        }
    }
}
