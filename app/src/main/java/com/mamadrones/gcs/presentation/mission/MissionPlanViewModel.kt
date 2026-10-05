package com.mamadrones.gcs.presentation.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.repository.MissionDraftFileCodec
import com.mamadrones.gcs.domain.repository.MissionDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MissionPlanUiState(
    val draft: MissionDraft = MissionDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadFailed: Boolean = false,
    val dirty: Boolean = false,
    val recovered: Boolean = false,
    val recoverySaved: Boolean = false,
    val pendingImport: MissionDraft? = null,
    val exportContent: String? = null,
    val exportFileName: String? = null,
    val library: List<MissionLibraryEntry> = emptyList(),
    val libraryError: String? = null,
    val error: String? = null
) {
    val editable: Boolean get() = !loading && !saving && !loadFailed
}

sealed interface MissionPlanAction {
    data class Add(val latitude: Double, val longitude: Double) : MissionPlanAction
    data class Edit(val waypoint: DraftWaypoint) : MissionPlanAction
    data class Remove(val id: String) : MissionPlanAction
    data class Move(val id: String, val offset: Int) : MissionPlanAction
    data class Rename(val name: String) : MissionPlanAction
    data object New : MissionPlanAction
    data object Save : MissionPlanAction
    data class SaveToLibrary(val name: String) : MissionPlanAction
    data class OpenLibraryEntry(val id: String) : MissionPlanAction
    data class DeleteLibraryEntry(val id: String) : MissionPlanAction
    data object Export : MissionPlanAction
    data class ExportFinished(val error: String? = null) : MissionPlanAction
    data class ImportContent(val content: String) : MissionPlanAction
    data object ConfirmImport : MissionPlanAction
    data object CancelImport : MissionPlanAction
    data class ImportFailed(val message: String) : MissionPlanAction
    data object RetryLoad : MissionPlanAction
}

@HiltViewModel
class MissionPlanViewModel @Inject constructor(
    private val repository: MissionDraftRepository,
    private val fileCodec: MissionDraftFileCodec
) : ViewModel() {
    private val mutableState = MutableStateFlow(MissionPlanUiState())
    val state = mutableState.asStateFlow()
    private var savedDraft = MissionDraft()
    private var recoveryJob: Job? = null
    private val persistenceMutex = Mutex()

    init { load() }

    fun dispatch(action: MissionPlanAction) {
        if (action == MissionPlanAction.Export) {
            exportDraft()
            return
        }
        if (action is MissionPlanAction.ExportFinished) {
            mutableState.value = state.value.copy(exportContent = null, exportFileName = null, error = action.error)
            return
        }
        if (action is MissionPlanAction.ImportContent) {
            importDraft(action.content)
            return
        }
        if (action is MissionPlanAction.ImportFailed) {
            mutableState.value = state.value.copy(error = action.message)
            return
        }
        if (action == MissionPlanAction.CancelImport) {
            mutableState.value = state.value.copy(pendingImport = null)
            return
        }
        if (action == MissionPlanAction.RetryLoad) {
            if (state.value.loadFailed && !state.value.loading) load()
            return
        }
        val current = state.value
        if (!current.editable) return
        if (action == MissionPlanAction.Save) {
            save()
            return
        }
        if (action is MissionPlanAction.SaveToLibrary) {
            saveToLibrary(action.name)
            return
        }
        if (action is MissionPlanAction.OpenLibraryEntry) {
            val entry = current.library.firstOrNull { it.id == action.id } ?: return
            val opened = entry.draft
            recoveryJob?.cancel()
            mutableState.value = current.copy(
                draft = opened,
                dirty = opened != savedDraft,
                recovered = false,
                recoverySaved = false,
                pendingImport = null,
                error = null
            )
            if (opened != savedDraft) scheduleRecovery(opened)
            return
        }
        if (action is MissionPlanAction.DeleteLibraryEntry) {
            deleteLibraryEntry(action.id)
            return
        }
        if (action == MissionPlanAction.ConfirmImport) {
            val imported = current.pendingImport ?: return
            mutableState.value = current.copy(
                draft = imported,
                pendingImport = null,
                dirty = imported != savedDraft,
                recovered = false,
                recoverySaved = false,
                error = null
            )
            if (imported != savedDraft) scheduleRecovery(imported) else recoveryJob?.cancel()
            return
        }
        try {
            val next = when (action) {
                is MissionPlanAction.Add -> current.draft.add(DraftWaypoint(UUID.randomUUID().toString(), action.latitude, action.longitude))
                is MissionPlanAction.Edit -> current.draft.edit(action.waypoint)
                is MissionPlanAction.Remove -> current.draft.remove(action.id)
                is MissionPlanAction.Move -> current.draft.move(action.id, action.offset)
                is MissionPlanAction.Rename -> current.draft.copy(name = action.name.trim())
                MissionPlanAction.New -> MissionDraft()
                else -> current.draft
            }
            mutableState.value = current.copy(
                draft = next,
                dirty = next != savedDraft,
                recovered = false,
                recoverySaved = false,
                error = null
            )
            if (next != savedDraft) scheduleRecovery(next) else recoveryJob?.cancel()
        } catch (_: IllegalArgumentException) {
            mutableState.value = current.copy(error = "Invalid waypoint, name or draft size. The route was not changed.")
        }
    }

    private fun exportDraft() {
        val current = state.value
        if (!current.editable) return
        try {
            val content = fileCodec.encode(current.draft)
            val safeName = current.draft.name
                .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "route" }
            mutableState.value = current.copy(
                exportContent = content,
                exportFileName = "$safeName.geojson",
                error = null
            )
        } catch (_: Exception) {
            mutableState.value = current.copy(error = "Route could not be exported.")
        }
    }

    private fun importDraft(content: String) {
        val current = state.value
        if (!current.editable) return
        try {
            val imported = fileCodec.decode(content)
            mutableState.value = current.copy(pendingImport = imported, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = current.copy(
                error = error.message?.take(180) ?: "Route file could not be read. The current draft is unchanged."
            )
        }
    }

    private fun load() {
        mutableState.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                savedDraft = repository.load()
                var libraryError: String? = null
                val library = try {
                    repository.loadLibrary().sortedByDescending { it.savedAtEpochMillis }
                } catch (_: Exception) {
                    libraryError = "The local route library could not be read. The saved draft is still available."
                    emptyList()
                }
                val recovery = try {
                    repository.loadRecovery()
                } catch (_: Exception) {
                    mutableState.value = MissionPlanUiState(
                        draft = savedDraft,
                        loading = false,
                        library = library,
                        libraryError = libraryError,
                        error = "The recovery copy could not be read. The last saved draft is loaded; save any new edits explicitly."
                    )
                    return@launch
                }
                if (recovery == null || recovery == savedDraft) {
                    mutableState.value = MissionPlanUiState(draft = savedDraft, loading = false, library = library, libraryError = libraryError)
                } else {
                    mutableState.value = MissionPlanUiState(
                        draft = recovery,
                        loading = false,
                        dirty = true,
                        recovered = true,
                        recoverySaved = true,
                        library = library,
                        libraryError = libraryError
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(loading = false, loadFailed = true,
                    error = "Cannot read the saved draft. Stored data has not been replaced. Retry loading.")
            }
        }
    }

    private fun save() {
        recoveryJob?.cancel()
        recoveryJob = null
        val snapshot = state.value.draft
        mutableState.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                persistenceMutex.withLock { repository.save(snapshot) }
                savedDraft = snapshot
                mutableState.value = state.value.copy(saving = false, dirty = false, recovered = false, recoverySaved = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(saving = false,
                    error = "Draft could not be saved. Keep this screen open and try Save draft again.")
                scheduleRecovery(snapshot)
            }
        }
    }

    private fun saveToLibrary(name: String) {
        val current = state.value
        val safeName = name.trim().take(MissionDraft.MAX_NAME_LENGTH)
        if (safeName.isBlank()) {
            mutableState.value = current.copy(error = "Enter a route name before saving to the library.")
            return
        }
        val snapshot = try {
            current.draft.copy(name = safeName)
        } catch (_: IllegalArgumentException) {
            mutableState.value = current.copy(error = "The route name is invalid.")
            return
        }
        mutableState.value = current.copy(saving = true, error = null, libraryError = null)
        viewModelScope.launch {
            try {
                val entry = persistenceMutex.withLock { repository.saveToLibrary(snapshot) }
                mutableState.value = state.value.copy(
                    saving = false,
                    library = (state.value.library + entry).sortedByDescending { it.savedAtEpochMillis },
                    libraryError = null,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = state.value.copy(
                    saving = false,
                    error = failure.message?.take(180) ?: "Route could not be saved to the library."
                )
            }
        }
    }

    private fun deleteLibraryEntry(id: String) {
        val current = state.value
        mutableState.value = current.copy(saving = true, error = null, libraryError = null)
        viewModelScope.launch {
            try {
                persistenceMutex.withLock { repository.deleteFromLibrary(id) }
                mutableState.value = state.value.copy(
                    saving = false,
                    library = state.value.library.filterNot { it.id == id },
                    libraryError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(
                    saving = false,
                    libraryError = "That library route could not be deleted. It has been kept on this device."
                )
            }
        }
    }

    private fun scheduleRecovery(draft: MissionDraft) {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(350)
            try {
                persistenceMutex.withLock { repository.saveRecovery(draft) }
                if (state.value.draft == draft && state.value.dirty) {
                    mutableState.value = state.value.copy(recoverySaved = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (state.value.draft == draft && state.value.dirty) {
                    mutableState.value = state.value.copy(
                        recoverySaved = false,
                        error = "Automatic recovery could not be saved. Use Save draft before leaving this screen."
                    )
                }
            }
        }
    }
}
