package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.parameters.ParameterFileReader
import com.mamadrones.gcs.data.parameters.ParameterFileComparison
import com.mamadrones.gcs.data.parameters.ParameterComparison
import com.mamadrones.gcs.domain.model.ParameterSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import com.mamadrones.gcs.presentation.parameters.ParameterHardwareReport
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HardwareReportState(
    val preview: String? = null,
    val choosingDestination: Boolean = false,
    val writing: Boolean = false,
    val message: String? = null,
) {
    val busy: Boolean get() = choosingDestination || writing
}

data class ParameterReviewState(
    val snapshot: ParameterSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val reference: ParameterSnapshot? = null,
    val comparison: ParameterComparison? = null,
    val report: HardwareReportState = HardwareReportState(),
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
        if (state.value.report.busy) return
        val request = ++generation
        importJob?.cancel()
        val previous = state.value
        mutableState.value = state.value.copy(loading = true, error = null, report = HardwareReportState())
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
        if (state.value.loading || state.value.report.busy) return
        mutableState.value = state.value.copy(reference = null, comparison = null, error = null)
    }

    fun pickerFailed(reference: Boolean) {
        if (state.value.loading) return
        mutableState.value = state.value.copy(error =
            "Could not open the ${if (reference) "reference" else "current"} file picker. Existing review is unchanged.")
    }

    fun clear() {
        if (state.value.report.busy) return
        generation++
        importJob?.cancel()
        mutableState.value = ParameterReviewState()
    }

    fun prepareHardwareReport() {
        if (state.value.loading || state.value.report.busy) return
        val snapshot = state.value.snapshot ?: return
        mutableState.value = state.value.copy(report = HardwareReportState(preview = ParameterHardwareReport.capture(snapshot)))
    }

    fun dismissHardwareReport() {
        if (!state.value.report.busy) mutableState.value = state.value.copy(report = HardwareReportState())
    }

    fun chooseReportDestination(): Boolean {
        val report = state.value.report
        if (state.value.loading || report.preview == null || report.busy) return false
        mutableState.value = state.value.copy(report = report.copy(choosingDestination = true, message = null))
        return true
    }

    fun reportPickerFinished(failed: Boolean = false) {
        val report = state.value.report
        if (!report.choosingDestination || report.writing) return
        mutableState.value = state.value.copy(report = report.copy(choosingDestination = false,
            message = if (failed) "Could not open the document picker. Preview retained; try again."
                else "Save cancelled. No report was written by the app."))
    }

    fun saveHardwareReport(openStream: () -> OutputStream?) {
        val report = state.value.report
        val content = report.preview
        if (content == null) {
            mutableState.value = state.value.copy(report = HardwareReportState(message =
                "Snapshot no longer available. Prepare a new report; the selected document may be empty."))
            return
        }
        if (!report.choosingDestination || report.writing) return
        mutableState.value = state.value.copy(report = report.copy(choosingDestination = false, writing = true))
        viewModelScope.launch {
            try {
                withContext(importDispatcher) { ParameterHardwareReport.write(content, openStream) }
                mutableState.value = state.value.copy(report = HardwareReportState(message =
                    "Hardware report saved. Review it before sharing manually with your hardware team."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(report = state.value.report.copy(writing = false,
                    message = "Save failed. The destination may contain a partial file; delete it before sharing. Preview retained for retry."))
            }
        }
    }
}
