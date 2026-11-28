package com.mamadrones.gcs.domain.model

/** Local restore unit. A null recovery draft means the working state equals the saved draft. */
data class MissionRouteBackup(
    val savedDraft: MissionDraft,
    val recoveryDraft: MissionDraft?,
    val library: List<MissionLibraryEntry>,
) {
    init {
        require(library.size <= MissionLibraryEntry.MAX_ENTRIES)
        require(library.map { it.id }.distinct().size == library.size)
    }
}
