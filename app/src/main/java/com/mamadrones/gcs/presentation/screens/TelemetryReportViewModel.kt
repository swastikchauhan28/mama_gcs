package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.core.diagnostics.TelemetryEvidenceReport
import com.mamadrones.gcs.domain.model.VehicleState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TelemetryReportState(
    val preview: String? = null,
    val choosingDestination: Boolean = false,
    val writing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class TelemetryReportViewModel(private val ioDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)
    private val mutableState = MutableStateFlow(TelemetryReportState())
    val state = mutableState.asStateFlow()

    fun prepare(vehicle: VehicleState, now: Long) {
        if (state.value.writing || state.value.choosingDestination) return
        mutableState.value = TelemetryReportState(preview = TelemetryEvidenceReport.capture(vehicle, now))
    }

    fun dismiss() {
        if (!state.value.writing && !state.value.choosingDestination) mutableState.value = TelemetryReportState()
    }

    fun chooseDestination(): Boolean {
        if (state.value.preview == null || state.value.writing || state.value.choosingDestination) return false
        mutableState.value = state.value.copy(choosingDestination = true, message = null)
        return true
    }

    fun pickerCancelled() {
        if (state.value.writing) return
        mutableState.value = state.value.copy(choosingDestination = false, message = "Save cancelled. No report was written by the app.")
    }

    fun pickerFailed() {
        mutableState.value = state.value.copy(choosingDestination = false,
            message = "Could not open the document picker. Preview retained; try again.")
    }

    fun save(openStream: () -> OutputStream?) {
        val content = state.value.preview
        if (content == null) {
            mutableState.value = TelemetryReportState(message = "Snapshot no longer available. Prepare a new report; the selected document may be empty.")
            return
        }
        if (!state.value.choosingDestination || state.value.writing) return
        mutableState.value = state.value.copy(choosingDestination = false, writing = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { TelemetryEvidenceReport.write(content, openStream) }
                mutableState.value = TelemetryReportState(message = "Telemetry snapshot saved. Share it manually from your file manager if needed.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(writing = false,
                    message = "Save failed. The destination may contain a partial file; delete it before sharing. Preview retained for retry.")
            }
        }
    }
}
