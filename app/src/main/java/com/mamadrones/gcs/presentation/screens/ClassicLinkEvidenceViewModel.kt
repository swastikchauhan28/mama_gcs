package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.core.diagnostics.ClassicLinkEvidenceReport
import com.mamadrones.gcs.domain.model.ClassicConnectionCheck
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
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

data class ClassicLinkEvidenceState(
    val snapshot: String? = null,
    val reviewing: Boolean = false,
    val choosingDestination: Boolean = false,
    val writing: Boolean = false,
    val message: String? = null,
)

/** Snapshot/export state only. This model has no transport, MAVLink or command API. */
@HiltViewModel
class ClassicLinkEvidenceViewModel(private val ioDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor() : this(Dispatchers.IO)

    private val mutableState = MutableStateFlow(ClassicLinkEvidenceState())
    val state = mutableState.asStateFlow()
    private val busy get() = state.value.choosingDestination || state.value.writing

    fun capture(linkActive: Boolean, competingLink: Boolean, connection: ConnectionState, vehicle: VehicleState, now: Long) {
        if (!canCapture(linkActive, competingLink, connection) || busy || state.value.reviewing) return
        val assessment = ClassicConnectionCheck.assess(linkActive, connection, vehicle, now)
        mutableState.value = ClassicLinkEvidenceState(
            snapshot = ClassicLinkEvidenceReport.capture(assessment, connection, vehicle.mavlinkDiagnostics, now),
            message = "Classic-link evidence captured in memory. Disconnect all telemetry links, then review and save it.",
        )
    }

    fun review(linkActive: Boolean, competingLink: Boolean) {
        if (!canExport(linkActive, competingLink) || busy || state.value.snapshot == null) return
        mutableState.value = state.value.copy(reviewing = true, message = null)
    }

    fun closePreview() { if (!busy) mutableState.value = state.value.copy(reviewing = false) }
    fun discard() { if (!busy) mutableState.value = ClassicLinkEvidenceState() }

    fun chooseDestination(linkActive: Boolean, competingLink: Boolean): Boolean {
        if (!canExport(linkActive, competingLink) || busy || !state.value.reviewing || state.value.snapshot == null) return false
        mutableState.value = state.value.copy(choosingDestination = true, message = null)
        return true
    }

    fun pickerCancelled() { if (!state.value.writing) mutableState.value = state.value.copy(choosingDestination = false, message = "Save cancelled. Snapshot retained.") }
    fun pickerFailed() { if (!state.value.writing) mutableState.value = state.value.copy(choosingDestination = false, message = "Could not open document picker. Snapshot retained.") }

    fun save(openStream: () -> OutputStream?) {
        val snapshot = state.value.snapshot
        if (snapshot == null) {
            mutableState.value = ClassicLinkEvidenceState(message = "Snapshot no longer available. Capture again; the selected document may be empty.")
            return
        }
        if (!state.value.choosingDestination || state.value.writing) return
        mutableState.value = state.value.copy(choosingDestination = false, writing = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { ClassicLinkEvidenceReport.write(snapshot, openStream) }
                mutableState.value = ClassicLinkEvidenceState(message = "Classic-link evidence saved. Review the file before sharing manually.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(writing = false,
                    message = "Save failed. A partial file may exist; check before sharing. Snapshot retained for retry.")
            }
        }
    }

    internal fun canCapture(linkActive: Boolean, competingLink: Boolean, connection: ConnectionState): Boolean =
        linkActive && !competingLink && connection.status == TransportStatus.OPEN

    internal fun canExport(linkActive: Boolean, competingLink: Boolean): Boolean = !linkActive && !competingLink
}
