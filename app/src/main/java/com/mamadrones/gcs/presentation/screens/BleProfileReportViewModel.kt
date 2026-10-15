package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.core.diagnostics.BleProfileReport
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

data class BleProfileReportState(
    val snapshot: String? = null,
    val reviewing: Boolean = false,
    val choosingDestination: Boolean = false,
    val writing: Boolean = false,
    val message: String? = null,
)

/** Snapshot/export only; this model has no transport or subscription API. */
@HiltViewModel
class BleProfileReportViewModel(private val ioDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)
    private val mutableState = MutableStateFlow(BleProfileReportState())
    val state = mutableState.asStateFlow()
    private val busy get() = state.value.writing || state.value.choosingDestination

    fun capture(source: VescDiscoveryUiState, competingLink: Boolean, now: Long) {
        if (!source.canCaptureBleProfile(competingLink) || busy || state.value.reviewing) return
        mutableState.value = BleProfileReportState(
            snapshot = BleProfileReport.capture(source.notifyCharacteristics, source.successfulConnections, now),
            message = "Profile captured in memory. Disconnect BLE, then review and save. A new capture replaces this snapshot.")
    }

    fun review(source: VescDiscoveryUiState, competingLink: Boolean) {
        if (!source.canSaveBleProfile(competingLink) || busy || state.value.snapshot == null) return
        mutableState.value = state.value.copy(reviewing = true, message = null)
    }

    fun closePreview() { if (!busy) mutableState.value = state.value.copy(reviewing = false) }
    fun discard() { if (!busy) mutableState.value = BleProfileReportState() }

    fun chooseDestination(source: VescDiscoveryUiState, competingLink: Boolean): Boolean {
        if (!source.canSaveBleProfile(competingLink) || busy || !state.value.reviewing || state.value.snapshot == null) return false
        mutableState.value = state.value.copy(choosingDestination = true, message = null)
        return true
    }

    fun pickerCancelled() { if (!state.value.writing) mutableState.value = state.value.copy(choosingDestination = false, message = "Save cancelled. Snapshot retained.") }
    fun pickerFailed() { if (!state.value.writing) mutableState.value = state.value.copy(choosingDestination = false, message = "Could not open document picker. Snapshot retained.") }

    fun save(openStream: () -> OutputStream?) {
        val snapshot = state.value.snapshot
        if (snapshot == null) {
            mutableState.value = BleProfileReportState(message = "Snapshot no longer available. Capture again; the selected document may be empty.")
            return
        }
        if (!state.value.choosingDestination || state.value.writing) return
        mutableState.value = state.value.copy(choosingDestination = false, writing = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { BleProfileReport.write(snapshot, openStream) }
                mutableState.value = BleProfileReportState(message = "BLE profile saved. Review the file before sharing manually.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = state.value.copy(writing = false,
                    message = "Save failed. A partial file may exist; check before sharing. Snapshot retained for retry.")
            }
        }
    }
}

internal fun VescDiscoveryUiState.canCaptureBleProfile(competingLink: Boolean): Boolean =
    !competingLink && gattConnected && successfulConnections > 0 && !gattConnecting && !closing &&
        !scanning && !subscribing && stabilityTrial.status != BleStabilityTrialStatus.RUNNING

internal fun VescDiscoveryUiState.canSaveBleProfile(competingLink: Boolean): Boolean =
    !competingLink && !gattConnected && !gattConnecting && !closing && !scanning && !subscribing &&
        receivingFrom == null && stabilityTrial.status != BleStabilityTrialStatus.RUNNING
