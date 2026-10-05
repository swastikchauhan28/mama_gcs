package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry

/** Device-local drafts and route copies. Deliberately has no connection to vehicle mission transfer. */
interface MissionDraftRepository {
    suspend fun load(): MissionDraft
    suspend fun save(draft: MissionDraft)
    suspend fun loadRecovery(): MissionDraft?
    suspend fun saveRecovery(draft: MissionDraft)
    suspend fun loadLibrary(): List<MissionLibraryEntry>
    suspend fun saveToLibrary(draft: MissionDraft): MissionLibraryEntry
    suspend fun deleteFromLibrary(id: String)
}
