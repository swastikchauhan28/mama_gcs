package com.mamadrones.gcs.presentation.screens

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import com.mamadrones.gcs.data.local.datastore.BleStabilityHistoryRepository
import com.mamadrones.gcs.data.local.datastore.BleStabilityRun
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
import kotlinx.coroutines.delay
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

enum class BleStabilityTrialStatus { IDLE, RUNNING, PASSED, FAILED, STOPPED }

data class BleStabilityTrialUiState(
    val status: BleStabilityTrialStatus = BleStabilityTrialStatus.IDLE,
    val startedAtEpochMillis: Long? = null,
    val elapsedMillis: Long = 0,
    val initialUnexpectedDisconnects: Long = 0,
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
    val successfulConnections: Long = 0,
    val unexpectedDisconnects: Long = 0,
    val connectedAtEpochMillis: Long? = null,
    val connectedAtElapsedMillis: Long? = null,
    val lastSessionDurationMillis: Long? = null,
    val lastDisconnectReason: String? = null,
    val stabilityTrial: BleStabilityTrialUiState = BleStabilityTrialUiState(),
    val savedStabilityRuns: List<BleStabilityRun> = emptyList(),
    val stabilityHistoryReady: Boolean = false,
    val stabilityHistorySaving: Boolean = false,
    val stabilityHistoryError: String? = null,
    val notifyCharacteristics: List<BleNotifyCharacteristic> = emptyList(),
    val receivingFrom: BleNotifyCharacteristic? = null,
    val gattError: String? = null,
)

private data class StabilityHistoryState(
    val ready: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class VescBluetoothDiscoveryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val mavlinkSessionFactory: MavlinkSessionFactory,
    private val linkGate: TelemetryLinkGate,
    private val stabilityHistory: BleStabilityHistoryRepository,
) : ViewModel() {
    private val scanner = BleDeviceScanner(context)
    private val transport = BleMavlinkTransport(context)
    private var mavlinkSession: VehicleMavlinkSession? = null
    private var operation: Job? = null
    private var lease: TelemetryLinkGate.Lease? = null
    private val operationError = MutableStateFlow<String?>(null)
    private val closing = MutableStateFlow(false)
    private val stabilityTrial = MutableStateFlow(BleStabilityTrialUiState())
    private val stabilityHistoryState = MutableStateFlow(StabilityHistoryState())
    private var stabilityTrialJob: Job? = null
    private var trialStartedAtElapsedMillis: Long = 0
    private var recordedTrialStartedAt: Long? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            scanner.stop()
            disconnectGatt("App moved to background")
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        viewModelScope.launch {
            try {
                stabilityHistory.recoverInterruptedRun()
                stabilityHistoryState.value = StabilityHistoryState(ready = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stabilityHistoryState.value = StabilityHistoryState(error = "Cannot load stability history. Reopen the app to retry.")
            }
        }
        viewModelScope.launch {
            transport.state.collect { link ->
                if (!link.connected && !link.connecting && lease != null && operation?.isActive != true) {
                    disconnectGatt()
                }
            }
        }
    }

    val state: StateFlow<VescDiscoveryUiState> = combine(scanner.state, transport.state, operationError, closing, stabilityTrial) { scan, link, error, isClosing, trial ->
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
                successfulConnections = link.successfulConnections,
                unexpectedDisconnects = link.unexpectedDisconnects,
                connectedAtEpochMillis = link.connectedAtEpochMillis,
                connectedAtElapsedMillis = link.connectedAtElapsedMillis,
                lastSessionDurationMillis = link.lastSessionDurationMillis,
                lastDisconnectReason = link.lastDisconnectReason,
                stabilityTrial = trial,
                notifyCharacteristics = link.characteristics,
                receivingFrom = link.receivingFrom,
                gattError = error ?: link.error,
            )
        }
        .combine(stabilityHistory.runs) { ui, savedRuns -> ui.copy(savedStabilityRuns = savedRuns) }
        .combine(stabilityHistoryState) { ui, history -> ui.copy(stabilityHistoryReady = history.ready,
            stabilityHistorySaving = history.saving, stabilityHistoryError = history.error) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VescDiscoveryUiState(bleSupported = scanner.supportsBle()))

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()
    fun permissionDenied() = scanner.permissionDenied()

    fun startStabilityTrial() {
        if (!stabilityHistoryState.value.ready || stabilityHistoryState.value.saving || closing.value ||
            !transport.state.value.connected ||
            stabilityTrial.value.status == BleStabilityTrialStatus.RUNNING) return
        val startedAt = System.currentTimeMillis()
        val initialDrops = transport.state.value.unexpectedDisconnects
        recordedTrialStartedAt = null
        trialStartedAtElapsedMillis = SystemClock.elapsedRealtime()
        stabilityHistoryState.value = stabilityHistoryState.value.copy(saving = true, error = null)
        stabilityTrialJob?.cancel()
        stabilityTrialJob = viewModelScope.launch {
            try {
                stabilityHistory.beginRun(startedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stabilityHistoryState.value = stabilityHistoryState.value.copy(saving = false,
                    error = "Cannot save the test start. The stability test was not started.")
                return@launch
            }
            stabilityHistoryState.value = stabilityHistoryState.value.copy(saving = false)
            stabilityTrial.value = BleStabilityTrialUiState(
                status = BleStabilityTrialStatus.RUNNING,
                startedAtEpochMillis = startedAt,
                initialUnexpectedDisconnects = initialDrops,
            )
            while (stabilityTrial.value.status == BleStabilityTrialStatus.RUNNING) {
                delay(1_000L)
                val elapsed = (SystemClock.elapsedRealtime() - trialStartedAtElapsedMillis).coerceAtLeast(0L)
                val current = transport.state.value
                val result = when {
                    current.unexpectedDisconnects > initialDrops -> BleStabilityTrialStatus.FAILED
                    !current.connected -> BleStabilityTrialStatus.STOPPED
                    elapsed >= BLE_STABILITY_TARGET_MILLIS -> BleStabilityTrialStatus.PASSED
                    else -> BleStabilityTrialStatus.RUNNING
                }
                stabilityTrial.value = stabilityTrial.value.copy(status = result, elapsedMillis = elapsed)
                if (result != BleStabilityTrialStatus.RUNNING) {
                    recordTrialOnce(startedAt, result, elapsed, current.lastDisconnectReason)
                }
            }
        }
    }

    fun stopStabilityTrial() {
        finishStabilityTrial(BleStabilityTrialStatus.STOPPED, "Stopped manually")
    }

    private fun finishStabilityTrial(outcome: BleStabilityTrialStatus, reason: String?) {
        if (stabilityTrial.value.status != BleStabilityTrialStatus.RUNNING) return
        val startedAt = stabilityTrial.value.startedAtEpochMillis ?: return
        stabilityTrialJob?.cancel()
        stabilityTrial.value = stabilityTrial.value.copy(status = outcome,
            elapsedMillis = (SystemClock.elapsedRealtime() - trialStartedAtElapsedMillis).coerceAtLeast(0L))
        recordTrialOnce(startedAt, outcome, stabilityTrial.value.elapsedMillis, reason)
    }

    fun resetStabilityTrial() {
        if (stabilityTrial.value.status == BleStabilityTrialStatus.RUNNING) return
        stabilityTrial.value = BleStabilityTrialUiState()
    }

    private fun recordTrialOnce(
        startedAt: Long,
        outcome: BleStabilityTrialStatus,
        durationMillis: Long,
        reason: String?,
    ) {
        if (recordedTrialStartedAt == startedAt) return
        recordedTrialStartedAt = startedAt
        stabilityHistoryState.value = stabilityHistoryState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                stabilityHistory.finishRun(BleStabilityRun(
                    startedAtEpochMillis = startedAt,
                    finishedAtEpochMillis = System.currentTimeMillis(),
                    outcome = outcome.name,
                    durationMillis = durationMillis,
                    disconnectReason = reason,
                ))
                stabilityHistoryState.value = stabilityHistoryState.value.copy(saving = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stabilityHistoryState.value = StabilityHistoryState(ready = false,
                    error = "Test result could not be saved. Reopen the app to recover the interrupted record.")
            }
        }
    }

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
        disconnectGatt("Disconnected manually")
    }

    private fun disconnectGatt(reason: String) {
        if (closing.value) return
        closing.value = true
        val pending = operation
        viewModelScope.launch {
            try {
                pending?.cancelAndJoin()
                closeSession(reason)
            } finally {
                closing.value = false
            }
        }
    }

    private fun closeSession(reason: String = "BLE connection attempt ended") {
        mavlinkSession?.close()
        mavlinkSession = null
        transport.close(reason)
        val link = transport.state.value
        finishStabilityTrial(
            if (link.unexpectedDisconnects > stabilityTrial.value.initialUnexpectedDisconnects)
                BleStabilityTrialStatus.FAILED else BleStabilityTrialStatus.STOPPED,
            link.lastDisconnectReason ?: reason,
        )
        lease?.let(linkGate::release)
        lease = null
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        scanner.stop()
        operation?.cancel()
        stabilityTrialJob?.cancel()
        closeSession()
    }
}

private const val BLE_STABILITY_TARGET_MILLIS = 10 * 60 * 1_000L
