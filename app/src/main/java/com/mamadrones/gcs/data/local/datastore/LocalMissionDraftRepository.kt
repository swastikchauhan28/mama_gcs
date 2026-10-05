package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.repository.MissionDraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.missionDraftStore by preferencesDataStore(name = "mission_draft")

@Singleton
class LocalMissionDraftRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : MissionDraftRepository {
    private val draftKey = stringPreferencesKey("draft")
    private val recoveryKey = stringPreferencesKey("working_copy")

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
}
