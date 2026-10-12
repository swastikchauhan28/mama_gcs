package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.parameters.ParameterFileReader
import com.mamadrones.gcs.data.parameters.ParameterFileComparison
import com.mamadrones.gcs.data.parameters.ParameterComparison
import com.mamadrones.gcs.domain.model.ParameterSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParameterReviewState(
    val snapshot: ParameterSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val reference: ParameterSnapshot? = null,
    val comparison: ParameterComparison? = null,
)

@HiltViewModel
class ParameterReviewViewModel(private val importDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)
    private val mutableState = MutableStateFlow(ParameterReviewState())
    val state = mutableState.asStateFlow()
    private var importJob: Job? = null
    private var generation = 0

    fun importFile(openStream: () -> InputStream?) = importSnapshot(openStream, reference = false)

    fun importReference(openStream: () -> InputStream?) {
        if (state.value.snapshot == null) {
            mutableState.value = state.value.copy(error = "Import a current parameter file before selecting a reference. The previous review may have been cleared.")
            return
        }
        importSnapshot(openStream, reference = true)
    }

    private fun importSnapshot(openStream: () -> InputStream?, reference: Boolean) {
        val request = ++generation
        importJob?.cancel()
        val previous = state.value
        mutableState.value = state.value.copy(loading = true, error = null)
        importJob = viewModelScope.launch {
            try {
                val next = withContext(importDispatcher) {
                    val imported = ParameterFileReader.read(openStream() ?: throw java.io.IOException())
                    val current = if (reference) previous.snapshot else imported
                    val baseline = if (reference) imported else previous.reference
                    ParameterReviewState(snapshot = current, reference = baseline,
                        comparison = if (current != null && baseline != null)
                            ParameterFileComparison.compare(baseline, current) else null)
                }
                if (request == generation) mutableState.value = next
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == generation) mutableState.value = state.value.copy(loading = false,
                    error = (if (reference) "Reference file: " else "") +
                        (if (failure is IllegalArgumentException) failure.message ?: "Invalid parameter file."
                        else "Could not read this document. Select an accessible local text file."))
            }
        }
    }

    fun clearReference() {
        if (state.value.loading) return
        mutableState.value = state.value.copy(reference = null, comparison = null, error = null)
    }

    fun pickerFailed(reference: Boolean) {
        if (state.value.loading) return
        mutableState.value = state.value.copy(error =
            "Could not open the ${if (reference) "reference" else "current"} file picker. Existing review is unchanged.")
    }

    fun clear() {
        generation++
        importJob?.cancel()
        mutableState.value = ParameterReviewState()
    }
}
