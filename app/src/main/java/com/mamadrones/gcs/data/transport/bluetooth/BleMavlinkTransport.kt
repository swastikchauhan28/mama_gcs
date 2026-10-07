package com.mamadrones.gcs.data.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BleNotifyCharacteristic(
    val serviceUuid: String,
    val characteristicUuid: String,
    val supportsIndication: Boolean,
)

data class BleMavlinkState(
    val deviceName: String? = null,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val characteristics: List<BleNotifyCharacteristic> = emptyList(),
    val receivingFrom: BleNotifyCharacteristic? = null,
    val error: String? = null,
)

/** BLE GATT byte receiver for a user-selected MAVLink notification characteristic. Sending is disabled. */
class BleMavlinkTransport(context: Context) : VehicleTransport, AutoCloseable {
    private val appContext = context.applicationContext
    private val _connectionState = MutableStateFlow(ConnectionState())
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _state = MutableStateFlow(BleMavlinkState())
    val state: StateFlow<BleMavlinkState> = _state.asStateFlow()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    private val callbackGatt = AtomicReference<BluetoothGatt?>(null)
    private var connectWaiter: CompletableDeferred<Unit>? = null
    private var notifyWaiter: CompletableDeferred<Unit>? = null
    private var subscribedCharacteristic: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission") // Connection permission was checked before connectGatt; still handle revocation.
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect(IllegalStateException("BLE connection failed (GATT status $status)."))
                updateError("BLE connection failed (GATT status $status).")
                closeGatt(gatt)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState(TransportStatus.CONNECTING, "Discovering BLE services")
                    val discoveryStarted = try {
                        gatt.discoverServices()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!discoveryStarted) {
                        failConnect(IllegalStateException("Could not start BLE service discovery."))
                        updateError("Could not start BLE service discovery.")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState(TransportStatus.DISCONNECTED)
                    _state.value = _state.value.copy(connected = false, receivingFrom = null)
                    failConnect(IllegalStateException("BLE device disconnected."))
                    notifyWaiter?.completeExceptionally(IllegalStateException("BLE device disconnected."))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect(IllegalStateException("BLE service discovery failed (GATT status $status)."))
                updateError("BLE service discovery failed (GATT status $status).")
                return
            }
            val options = gatt.services.orEmpty().flatMap { service ->
                service.characteristics.mapNotNull { characteristic ->
                    val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val supportsIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (!supportsNotify && !supportsIndicate) null else BleNotifyCharacteristic(
                        serviceUuid = service.uuid.toString(),
                        characteristicUuid = characteristic.uuid.toString(),
                        supportsIndication = !supportsNotify && supportsIndicate,
                    )
                }
            }
            _state.value = _state.value.copy(connected = true, connecting = false, characteristics = options, error = null)
            _connectionState.value = ConnectionState(
                status = TransportStatus.OPEN,
                detail = "BLE GATT connected; awaiting telemetry characteristic selection",
                connectedAtEpochMillis = System.currentTimeMillis(),
            )
            connectWaiter?.complete(Unit)
            connectWaiter = null
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val waiter = notifyWaiter ?: return
            notifyWaiter = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState(
                    status = TransportStatus.OPEN,
                    detail = "Receiving MAVLink notifications",
                    connectedAtEpochMillis = _connectionState.value.connectedAtEpochMillis,
                )
                waiter.complete(Unit)
            } else {
                waiter.completeExceptionally(IllegalStateException("BLE notification setup failed (GATT status $status)."))
            }
        }

        @Deprecated("Android 13 callback retained for older devices")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { incoming.tryEmit(it.copyOf()) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            incoming.tryEmit(value.copyOf())
        }
    }

    @SuppressLint("MissingPermission") // UI requests BLUETOOTH_CONNECT before a user-selected GATT connection.
    suspend fun connect(device: BluetoothDevice, advertisedName: String) {
        disconnect()
        _state.value = BleMavlinkState(deviceName = advertisedName, connecting = true)
        _connectionState.value = ConnectionState(TransportStatus.CONNECTING, "Connecting to BLE GATT device")
        val waiter = CompletableDeferred<Unit>()
        connectWaiter = waiter
        try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else device.connectGatt(appContext, false, callback)
            if (gatt == null) throw IllegalStateException("Android could not open a BLE GATT connection.")
            callbackGatt.set(gatt)
            withTimeout(GATT_CONNECT_TIMEOUT_MILLIS) { waiter.await() }
        } catch (timeout: TimeoutCancellationException) {
            disconnect()
            updateError("BLE connection or service discovery timed out. Retry near the rover.")
            throw IllegalStateException("BLE connection timed out.", timeout)
        } catch (error: Exception) {
            disconnect()
            updateError(error.message ?: "BLE connection failed.")
            throw error
        } finally {
            if (connectWaiter === waiter) connectWaiter = null
        }
    }

    @SuppressLint("MissingPermission") // UI requests BLUETOOTH_CONNECT before GATT operations.
    suspend fun subscribe(option: BleNotifyCharacteristic) {
        val gatt = callbackGatt.get() ?: throw IllegalStateException("Connect to a BLE device first.")
        val characteristic = gatt.getService(UUID.fromString(option.serviceUuid))
            ?.getCharacteristic(UUID.fromString(option.characteristicUuid))
            ?: throw IllegalStateException("The selected BLE characteristic is no longer available.")
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
            ?: throw IllegalStateException("This characteristic has no notification configuration descriptor.")
        val localEnabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!localEnabled) throw IllegalStateException("Android could not enable local BLE notifications.")
        subscribedCharacteristic = characteristic
        val waiter = CompletableDeferred<Unit>()
        notifyWaiter = waiter
        val value = if (option.supportsIndication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!started) {
            notifyWaiter = null
            gatt.setCharacteristicNotification(characteristic, false)
            subscribedCharacteristic = null
            throw IllegalStateException("Android could not start BLE notification setup.")
        }
        try {
            withTimeout(GATT_OPERATION_TIMEOUT_MILLIS) { waiter.await() }
            _state.value = _state.value.copy(receivingFrom = option, error = null)
        } catch (error: Exception) {
            notifyWaiter = null
            gatt.setCharacteristicNotification(characteristic, false)
            subscribedCharacteristic = null
            val reported = if (error is TimeoutCancellationException) IllegalStateException("BLE notification setup timed out.", error) else error
            updateError(reported.message ?: "Could not subscribe to the selected BLE characteristic.")
            throw reported
        }
    }

    override suspend fun connect() {
        if (callbackGatt.get() == null || !_state.value.connected) {
            throw TransportException.UnsupportedTransport("BLE GATT is not connected to a selected device")
        }
    }

    override suspend fun disconnect() {
        val active = callbackGatt.getAndSet(null)
        connectWaiter?.cancel()
        connectWaiter = null
        notifyWaiter?.cancel()
        notifyWaiter = null
        val selected = subscribedCharacteristic
        subscribedCharacteristic = null
        if (active != null) {
            try {
                selected?.let { active.setCharacteristicNotification(it, false) }
                active.disconnect()
                active.close()
            } catch (_: SecurityException) {
                active.close()
            } catch (_: RuntimeException) {
                active.close()
            }
        }
        _state.value = _state.value.copy(connected = false, connecting = false, receivingFrom = null)
        _connectionState.value = ConnectionState(TransportStatus.DISCONNECTED)
    }

    override suspend fun send(data: ByteArray): Nothing =
        throw TransportException.UnsupportedTransport("BLE MAVLink transmission is disabled in this phase")

    override fun receive(): Flow<ByteArray> = incoming.asSharedFlow()

    @SuppressLint("MissingPermission") // close() is best-effort after explicit GATT use; revocation is caught.
    override fun close() {
        val active = callbackGatt.getAndSet(null)
        try { active?.close() } catch (_: SecurityException) { }
        _state.value = _state.value.copy(connected = false, connecting = false, receivingFrom = null)
        _connectionState.value = ConnectionState(TransportStatus.DISCONNECTED)
    }

    @SuppressLint("MissingPermission") // Best-effort cleanup after connection permission may have been revoked.
    private fun closeGatt(gatt: BluetoothGatt) {
        if (callbackGatt.compareAndSet(gatt, null)) {
            try { gatt.close() } catch (_: RuntimeException) { }
        }
    }

    private fun failConnect(error: Exception) {
        connectWaiter?.completeExceptionally(error)
        connectWaiter = null
    }

    private fun updateError(message: String) {
        _state.value = _state.value.copy(connecting = false, error = message)
        _connectionState.value = ConnectionState(TransportStatus.ERROR, message)
    }

    private companion object {
        val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val GATT_CONNECT_TIMEOUT_MILLIS = 20_000L
        const val GATT_OPERATION_TIMEOUT_MILLIS = 8_000L
    }
}
