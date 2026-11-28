package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.MissionRouteBackup

/** Device-local drafts and route copies. Deliberately has no connection to vehicle mission transfer. */
interface MissionDraftRepository {
    suspend fun load(): MissionDraft
    suspend fun save(draft: MissionDraft)
    suspend fun loadRecovery(): MissionDraft?
    suspend fun saveRecovery(draft: MissionDraft)
    /** Discard only the working copy; leave the committed draft and library unchanged. */
    suspend fun clearRecovery()
    suspend fun loadLibrary(): List<MissionLibraryEntry>
    suspend fun saveToLibrary(draft: MissionDraft): MissionLibraryEntry
    suspend fun deleteFromLibrary(id: String)

    /** Snapshot the persisted draft/library plus the editor's current unsaved work for an export. */
    suspend fun createBackupSnapshot(currentWorkingDraft: MissionDraft): MissionRouteBackup =
        error("Portable route backup is not supported by this repository.")

    /** Replace saved draft, recovery and library together; implementations must preserve all on failure. */
    suspend fun restoreBackup(backup: MissionRouteBackup) {
        error("Portable route backup restore is not supported by this repository.")
    }
}
