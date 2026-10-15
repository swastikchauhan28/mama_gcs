package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.core.diagnostics.BleStabilityReport
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

/** Keep export separate from Bluetooth ownership: no connections, subscriptions or writes to hardware. */
@HiltViewModel
class BleStabilityReportViewModel(private val ioDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)
    private val mutableState = MutableStateFlow(TelemetryReportState())
    val state = mutableState.asStateFlow()

    fun prepare(source: VescDiscoveryUiState, now: Long) {
        if (!source.canExportStabilityHistory || state.value.writing || state.value.choosingDestination) return
        mutableState.value = TelemetryReportState(preview = BleStabilityReport.capture(source.savedStabilityRuns, now))
    }

    fun dismiss() {
        if (!state.value.writing && !state.value.choosingDestination) mutableState.value = TelemetryReportState()
    }

    fun chooseDestination(source: VescDiscoveryUiState): Boolean {
        if (!source.canExportStabilityHistory || state.value.preview == null ||
            state.value.writing || state.value.choosingDestination) return false
        mutableState.value = state.value.copy(choosingDestination = true, message = null)
        return true
    }

    fun pickerCancelled() {
        if (state.value.writing) return
        mutableState.value = state.value.copy(choosingDestination = false, message = "Save cancelled. Preview retained.")
    }

    fun pickerFailed() {
        if (state.value.writing) return
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
                withContext(ioDispatcher) { BleStabilityReport.write(content, openStream) }
                mutableState.value = TelemetryReportState(message = "BLE stability report saved. Share manually after reviewing the file.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(writing = false,
                    message = "Save failed. A partial file may exist; check it before sharing. Preview retained for retry.")
            }
        }
    }
}

/** Saving opens another activity. Require an idle BLE screen so it cannot stop an active test/link. */
internal val VescDiscoveryUiState.canExportStabilityHistory: Boolean
    get() = stabilityHistoryReady && !stabilityHistorySaving && stabilityHistoryError == null &&
        savedStabilityRuns.isNotEmpty() && stabilityTrial.status != BleStabilityTrialStatus.RUNNING &&
        !scanning && !gattConnected && !gattConnecting && !subscribing && !closing
