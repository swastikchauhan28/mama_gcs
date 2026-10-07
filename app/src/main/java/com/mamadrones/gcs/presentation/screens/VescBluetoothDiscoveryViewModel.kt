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
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.bluetooth.BleMavlinkTransport
import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    val subscribing: Boolean = false,
    val closing: Boolean = false,
    val receivedNotifications: Long = 0,
    val receivedBytes: Long = 0,
    val lastReceivedAtEpochMillis: Long? = null,
    val notifyCharacteristics: List<BleNotifyCharacteristic> = emptyList(),
    val receivingFrom: BleNotifyCharacteristic? = null,
    val gattError: String? = null,
)

@HiltViewModel
class VescBluetoothDiscoveryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val mavlinkSessionFactory: MavlinkSessionFactory,
    private val linkGate: TelemetryLinkGate,
) : ViewModel() {
    private val scanner = BleDeviceScanner(context)
    private val transport = BleMavlinkTransport(context)
    private var mavlinkSession: VehicleMavlinkSession? = null
    private var operation: Job? = null
    private var lease: TelemetryLinkGate.Lease? = null
    private val operationError = MutableStateFlow<String?>(null)
    private val closing = MutableStateFlow(false)

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            scanner.stop()
            disconnectGatt()
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        viewModelScope.launch {
            transport.state.collect { link ->
                if (!link.connected && !link.connecting && lease != null && operation?.isActive != true) {
                    disconnectGatt()
                }
            }
        }
    }

    val state: StateFlow<VescDiscoveryUiState> = combine(scanner.state, transport.state, operationError, closing) { scan, link, error, isClosing ->
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
                subscribing = link.subscribing,
                closing = isClosing,
                receivedNotifications = link.receivedNotifications,
                receivedBytes = link.receivedBytes,
                lastReceivedAtEpochMillis = link.lastReceivedAtEpochMillis,
                notifyCharacteristics = link.characteristics,
                receivingFrom = link.receivingFrom,
                gattError = error ?: link.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VescDiscoveryUiState(bleSupported = scanner.supportsBle()))

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun permissionDenied() = scanner.permissionDenied()

    fun connectGatt(deviceKey: String, name: String) {
        if (lease != null || operation?.isActive == true || closing.value) return
        scanner.stop()
        val device = scanner.deviceForKey(deviceKey) ?: run {
            operationError.value = "This scan result expired. Scan again before connecting."
            return
        }
        operationError.value = null
        try {
            lease = linkGate.acquire("BLE")
        } catch (error: IllegalStateException) {
            operationError.value = error.message
            return
        }
        operation = viewModelScope.launch {
            try {
                transport.connect(device, name)
            } catch (cancelled: CancellationException) {
                closeSession()
                throw cancelled
            } catch (error: Exception) {
                operationError.value = error.message ?: "BLE connection failed."
                closeSession()
            }
        }
    }

    fun startMavlinkReceive(characteristic: BleNotifyCharacteristic) {
        if (lease == null || operation?.isActive == true || closing.value ||
            !transport.state.value.connected || mavlinkSession != null) return
        operationError.value = null
        operation = viewModelScope.launch {
            try {
                // Attach the stream collector before enabling remote notifications.
                mavlinkSession = mavlinkSessionFactory.create(transport, viewModelScope)
                mavlinkSession?.start()
                transport.subscribe(characteristic)
            } catch (cancelled: CancellationException) {
                closeSession()
                throw cancelled
            } catch (error: Exception) {
                operationError.value = error.message ?: "BLE receive could not start. Reconnect to retry."
                closeSession()
            }
        }
    }

    fun disconnectGatt() {
        if (closing.value) return
        closing.value = true
        val pending = operation
        viewModelScope.launch {
            try {
                pending?.cancelAndJoin()
                closeSession()
            } finally {
                closing.value = false
            }
        }
    }

    private fun closeSession() {
        mavlinkSession?.close()
        mavlinkSession = null
        transport.close()
        lease?.let(linkGate::release)
        lease = null
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        scanner.stop()
        operation?.cancel()
        closeSession()
    }
}
