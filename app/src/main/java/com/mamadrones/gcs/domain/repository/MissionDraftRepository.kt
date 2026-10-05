package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.MissionDraft

/** One device-local draft. Deliberately has no connection to vehicle mission transfer. */
interface MissionDraftRepository {
    suspend fun load(): MissionDraft
    suspend fun save(draft: MissionDraft)
    suspend fun loadRecovery(): MissionDraft?
    suspend fun saveRecovery(draft: MissionDraft)
}
