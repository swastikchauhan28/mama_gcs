package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.*
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.bluetooth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

data class ClassicBluetoothUiState(
    val peers: List<ClassicBluetoothPeer> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null,
    val link: ClassicLinkState = ClassicLinkState(),
)

@HiltViewModel
class ClassicBluetoothViewModel @Inject constructor(
    private val devices: ClassicBluetoothDevices,
    sessionFactory: MavlinkSessionFactory,
    gate: TelemetryLinkGate,
) : ViewModel(), DefaultLifecycleObserver {
    private val controller = ClassicTelemetryController(devices::createTransport, sessionFactory, viewModelScope, gate)
    private val discovery = MutableStateFlow(ClassicBluetoothUiState())
    val state = combine(discovery, controller.state) { discoveryState, link -> discoveryState.copy(link = link) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClassicBluetoothUiState())

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }

    fun refresh() {
        try {
            discovery.value = ClassicBluetoothUiState(peers = devices.pairedDevices(), loaded = true)
        } catch (error: Exception) {
            discovery.value = ClassicBluetoothUiState(loaded = true,
                error = error.message ?: "Could not list paired Bluetooth devices.")
        }
    }

    fun permissionDenied() {
        discovery.value = ClassicBluetoothUiState(error = "Nearby devices permission was denied. Allow it in Android app settings to use Bluetooth Classic.")
    }

    fun connect(peer: ClassicBluetoothPeer) {
        if (discovery.value.peers.none { it == peer }) return
        discovery.update { it.copy(error = null) }
        try { controller.connect(peer) }
        catch (error: Exception) { discovery.update { it.copy(error = error.message ?: "Cannot open Bluetooth link.") } }
    }

    fun disconnect() = controller.disconnect()
    override fun onStop(owner: LifecycleOwner) = disconnect()
    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        controller.close()
    }
}
