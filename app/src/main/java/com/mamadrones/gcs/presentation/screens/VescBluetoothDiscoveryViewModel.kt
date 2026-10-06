package com.mamadrones.gcs.presentation.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.transport.bluetooth.BleDeviceScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class VescBleDeviceUi(
    val key: String,
    val name: String,
    val rssiDbm: Int,
    val serviceUuids: List<String>,
)

data class VescDiscoveryUiState(
    val bleSupported: Boolean = false,
    val scanning: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
    val advertisements: List<VescBleDeviceUi> = emptyList(),
)

@HiltViewModel
class VescBluetoothDiscoveryViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val scanner = BleDeviceScanner(context)

    val state: StateFlow<VescDiscoveryUiState> = scanner.state
        .map { scan ->
            VescDiscoveryUiState(
                bleSupported = scanner.supportsBle(),
                scanning = scan.scanning,
                finished = scan.finished,
                error = scan.error,
                advertisements = scan.advertisements.map { result ->
                    VescBleDeviceUi(result.key, result.name, result.rssiDbm, result.serviceUuids)
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VescDiscoveryUiState(bleSupported = scanner.supportsBle()))

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun permissionDenied() = scanner.permissionDenied()

    override fun onCleared() {
        scanner.stop()
    }
}
