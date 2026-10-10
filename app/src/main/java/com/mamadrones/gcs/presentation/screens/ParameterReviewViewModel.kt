package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.parameters.ParameterFileReader
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
)

@HiltViewModel
class ParameterReviewViewModel(private val importDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)
    private val mutableState = MutableStateFlow(ParameterReviewState())
    val state = mutableState.asStateFlow()
    private var importJob: Job? = null
    private var generation = 0

    fun importFile(openStream: () -> InputStream?) {
        val request = ++generation
        importJob?.cancel()
        mutableState.value = state.value.copy(loading = true, error = null)
        importJob = viewModelScope.launch {
            try {
                val snapshot = withContext(importDispatcher) {
                    ParameterFileReader.read(openStream() ?: throw java.io.IOException())
                }
                if (request == generation) mutableState.value = ParameterReviewState(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == generation) mutableState.value = state.value.copy(loading = false,
                    error = if (failure is IllegalArgumentException) failure.message
                        else "Could not read this document. Select an accessible local text file.")
            }
        }
    }

    fun clear() {
        generation++
        importJob?.cancel()
        mutableState.value = ParameterReviewState()
    }
}
