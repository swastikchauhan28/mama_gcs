package com.mamadrones.gcs.presentation.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import com.mamadrones.gcs.data.transport.bluetooth.BleDeviceScanner
import com.mamadrones.gcs.data.transport.bluetooth.BleMavlinkTransport
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
    val selectedDeviceName: String? = null,
    val gattConnecting: Boolean = false,
    val gattConnected: Boolean = false,
    val notifyCharacteristics: List<BleNotifyCharacteristic> = emptyList(),
    val receivingFrom: BleNotifyCharacteristic? = null,
    val gattError: String? = null,
)

@HiltViewModel
class VescBluetoothDiscoveryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val mavlinkSessionFactory: MavlinkSessionFactory,
) : ViewModel() {
    private val scanner = BleDeviceScanner(context)
    private val transport = BleMavlinkTransport(context)
    private var mavlinkSession: VehicleMavlinkSession? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            scanner.stop()
            disconnectGatt()
        }
    }

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver) }

    val state: StateFlow<VescDiscoveryUiState> = combine(scanner.state, transport.state) { scan, link ->
            VescDiscoveryUiState(
                bleSupported = scanner.supportsBle(),
                scanning = scan.scanning,
                finished = scan.finished,
                error = scan.error,
                advertisements = scan.advertisements.map { result ->
                    VescBleDeviceUi(result.key, result.name, result.rssiDbm, result.serviceUuids)
                },
                selectedDeviceName = link.deviceName,
                gattConnecting = link.connecting,
                gattConnected = link.connected,
                notifyCharacteristics = link.characteristics,
                receivingFrom = link.receivingFrom,
                gattError = link.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VescDiscoveryUiState(bleSupported = scanner.supportsBle()))

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun permissionDenied() = scanner.permissionDenied()

    fun connectGatt(deviceKey: String, name: String) {
        scanner.stop()
        val device = scanner.deviceForKey(deviceKey) ?: run {
            return
        }
        viewModelScope.launch { runCatching { transport.connect(device, name) } }
    }

    fun startMavlinkReceive(characteristic: BleNotifyCharacteristic) {
        viewModelScope.launch {
            if (mavlinkSession != null) return@launch
            runCatching {
                transport.subscribe(characteristic)
                mavlinkSession = mavlinkSessionFactory.create(transport, viewModelScope).also { it.start() }
            }.onFailure {
                // The transport publishes BLE/GATT errors into its state for the screen.
            }
        }
    }

    fun disconnectGatt() {
        viewModelScope.launch {
            val active = mavlinkSession
            mavlinkSession = null
            if (active == null) transport.disconnect()
            else runCatching { active.stop() }.also { active.close() }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        scanner.stop()
        mavlinkSession?.close()
        transport.close()
    }
}
