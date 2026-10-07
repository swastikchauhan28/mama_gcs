package com.mamadrones.gcs.presentation.settings

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.transport.TransportConnectionManager
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.TransportSessionState
import com.mamadrones.gcs.data.transport.UdpTransportFactory
import com.mamadrones.gcs.domain.model.UdpEndpoint
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.domain.usecase.UpdateUdpEndpointUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val savedEndpoint: UdpEndpoint? = null,
    val remoteHostDraft: String = "",
    val remotePortDraft: String = UdpEndpoint.DEFAULT_PORT.toString(),
    val localPortDraft: String = UdpEndpoint.DEFAULT_PORT.toString(),
    val draftChanged: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val session: TransportSessionState = TransportSessionState()
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateUdpEndpoint: UpdateUdpEndpointUseCase,
    factory: UdpTransportFactory,
    mavlinkSessionFactory: MavlinkSessionFactory,
    linkGate: TelemetryLinkGate,
) : ViewModel(), DefaultLifecycleObserver {
    private val manager = TransportConnectionManager(factory, mavlinkSessionFactory, viewModelScope, linkGate)
    private val mutableState = MutableStateFlow(ConnectionUiState())
    val state = mutableState.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch {
            settingsRepository.preferences.collect { preferences ->
                mutableState.update { current ->
                    val endpoint = preferences.udpEndpoint
                    // A delayed initial DataStore emission must not erase an endpoint the user is
                    // editing or one whose local write is still in flight.
                    if (current.draftChanged || current.saving) current
                    else current.copy(
                        savedEndpoint = endpoint,
                        remoteHostDraft = endpoint?.remoteHost.orEmpty(),
                        remotePortDraft = (endpoint?.remotePort ?: UdpEndpoint.DEFAULT_PORT).toString(),
                        localPortDraft = (endpoint?.localPort ?: UdpEndpoint.DEFAULT_PORT).toString()
                    )
                }
            }
        }
        viewModelScope.launch {
            manager.state.collect { session -> mutableState.update { it.copy(session = session) } }
        }
    }

    fun updateRemoteHost(value: String) = updateDraft { it.copy(remoteHostDraft = value, draftChanged = true, error = null) }
    fun updateRemotePort(value: String) = updateDraft { it.copy(remotePortDraft = value, draftChanged = true, error = null) }
    fun updateLocalPort(value: String) = updateDraft { it.copy(localPortDraft = value, draftChanged = true, error = null) }

    fun saveEndpoint() {
        val endpoint = draftEndpoint() ?: return
        val previousEndpoint = mutableState.value.savedEndpoint
        // Keep the console responsive while the small local preference write completes. A failed
        // write restores the previous confirmed endpoint and keeps socket opening explicit.
        mutableState.update {
            it.copy(savedEndpoint = endpoint, draftChanged = false, saving = true, error = null)
        }
        viewModelScope.launch {
            try {
                updateUdpEndpoint(endpoint)
            } catch (_: IOException) {
                mutableState.update {
                    it.copy(savedEndpoint = previousEndpoint, error = "The UDP endpoint could not be saved.")
                }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun openSocket() {
        val endpoint = mutableState.value.savedEndpoint ?: run {
            mutableState.update { it.copy(error = "Save a UDP endpoint before opening a socket.") }
            return
        }
        viewModelScope.launch {
            try {
                mutableState.update { it.copy(error = null) }
                manager.connect(endpoint)
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "The UDP socket could not be opened.") }
            }
        }
    }

    fun closeSocket() = viewModelScope.launch { manager.disconnect() }

    fun clearEndpoint() = viewModelScope.launch {
        manager.disconnect()
        try {
            updateUdpEndpoint(null)
            mutableState.update {
                it.copy(
                    savedEndpoint = null,
                    remoteHostDraft = "",
                    remotePortDraft = UdpEndpoint.DEFAULT_PORT.toString(),
                    localPortDraft = UdpEndpoint.DEFAULT_PORT.toString(),
                    draftChanged = false,
                    error = null
                )
            }
        } catch (_: IOException) {
            mutableState.update { it.copy(error = "The UDP endpoint could not be cleared.") }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // No background link or automatic reconnect in this phase.
        closeSocket()
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        manager.close()
        super.onCleared()
    }

    private fun draftEndpoint(): UdpEndpoint? = try {
        UdpEndpoint(
            remoteHost = mutableState.value.remoteHostDraft.trim(),
            remotePort = mutableState.value.remotePortDraft.toInt(),
            localPort = mutableState.value.localPortDraft.toInt()
        )
    } catch (_: IllegalArgumentException) {
        mutableState.update { it.copy(error = "Enter a host, a remote port from 1 to 65535, and a local port from 0 to 65535.") }
        null
    } catch (_: NumberFormatException) {
        mutableState.update { it.copy(error = "Ports must be whole numbers.") }
        null
    }

    private fun updateDraft(transform: (ConnectionUiState) -> ConnectionUiState) {
        mutableState.update(transform)
    }
}
