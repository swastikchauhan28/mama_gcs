package com.mamadrones.gcs.data.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout

data class BleNotifyCharacteristic(
    val serviceUuid: String,
    val characteristicUuid: String,
    val supportsIndication: Boolean,
    val serviceInstanceId: Int = 0,
    val characteristicInstanceId: Int = 0,
)

data class BleMavlinkState(
    val deviceName: String? = null,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val subscribing: Boolean = false,
    val characteristics: List<BleNotifyCharacteristic> = emptyList(),
    val receivingFrom: BleNotifyCharacteristic? = null,
    val receivedNotifications: Long = 0,
    val receivedBytes: Long = 0,
    val lastReceivedAtEpochMillis: Long? = null,
    val error: String? = null,
)

/** Foreground, main-thread-owned GATT receiver. All callbacks use the same main handler.
 * Notification bytes are not evidence of MAVLink liveness. Sending is deliberately disabled.
 */
class BleMavlinkTransport(context: Context) : VehicleTransport, AutoCloseable {
    override val linkKind = com.mamadrones.gcs.domain.model.TelemetryLinkKind.BLE
    private val appContext = context.applicationContext
    private val _connectionState = MutableStateFlow(ConnectionState())
    override val connectionState = _connectionState.asStateFlow()
    private val _state = MutableStateFlow(BleMavlinkState())
    val state = _state.asStateFlow()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    private var activeGatt: BluetoothGatt? = null
    private var connectWaiter: CompletableDeferred<Unit>? = null
    private var notifyWaiter: CompletableDeferred<Unit>? = null
    private var subscribedCharacteristic: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission") // Permission may be revoked; failures close the link.
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== activeGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("BLE connection failed (GATT status $status). Reconnect to retry.")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState(TransportStatus.CONNECTING, "Discovering BLE services")
                    try {
                        if (!gatt.discoverServices()) fail("Could not start BLE service discovery.")
                    } catch (_: RuntimeException) {
                        fail("BLE service discovery unavailable. Check Bluetooth and Nearby devices permission.")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> fail("BLE device disconnected. Reconnect to retry.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== activeGatt || connectWaiter == null) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("BLE service discovery failed (GATT status $status).")
                return
            }
            val options = gatt.services.orEmpty().flatMap { service ->
                service.characteristics.mapNotNull { characteristic ->
                    val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if ((!notify && !indicate) || characteristic.getDescriptor(CCCD_UUID) == null) null
                    else BleNotifyCharacteristic(service.uuid.toString(), characteristic.uuid.toString(),
                        !notify && indicate, service.instanceId, characteristic.instanceId)
                }
            }
            _state.value = _state.value.copy(connected = true, connecting = false, characteristics = options, error = null)
            _connectionState.value = ConnectionState(TransportStatus.OPEN,
                "BLE GATT connected; telemetry not started", System.currentTimeMillis())
            connectWaiter?.complete(Unit)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== activeGatt || descriptor.uuid != CCCD_UUID ||
                descriptor.characteristic !== subscribedCharacteristic) return
            val waiter = notifyWaiter ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyWaiter = null
                waiter.complete(Unit)
            }
            else fail("BLE notification setup failed (GATT status $status). Reconnect to retry.")
        }

        @Deprecated("Callback for Android 12 and earlier")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { receiveNotification(gatt, characteristic, it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receiveNotification(gatt, characteristic, value)
        }
    }

    private fun receiveNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (gatt !== activeGatt || characteristic !== subscribedCharacteristic || value.isEmpty()) return
        // Losing a fragment corrupts the serial stream: close instead of silently continuing.
        if (incoming.subscriptionCount.value == 0 || !incoming.tryEmit(value.copyOf())) {
            fail("BLE receive buffer unavailable or full. Reconnect to restart telemetry.")
            return
        }
        val now = System.currentTimeMillis()
        _state.update { it.copy(receivedNotifications = it.receivedNotifications + 1,
            receivedBytes = it.receivedBytes + value.size, lastReceivedAtEpochMillis = now) }
        _connectionState.update { it.copy(packetStatistics = it.packetStatistics.copy(
            receivedPackets = it.packetStatistics.receivedPackets + 1, lastReceivedAtEpochMillis = now)) }
    }

    @SuppressLint("MissingPermission") // Explicit user action after runtime permission request.
    suspend fun connect(device: BluetoothDevice, advertisedName: String) {
        check(activeGatt == null) { "Disconnect BLE before reconnecting." }
        _state.value = BleMavlinkState(deviceName = advertisedName, connecting = true)
        _connectionState.value = ConnectionState(TransportStatus.CONNECTING, "Connecting to BLE GATT device")
        val waiter = CompletableDeferred<Unit>()
        connectWaiter = waiter
        try {
            // Handler overload is available at minSdk 26. It prevents callbacks racing assignment.
            activeGatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK, Handler(Looper.getMainLooper()))
                ?: throw IllegalStateException("Android could not open a BLE GATT connection.")
            withTimeout(20_000L) { waiter.await() }
            check(activeGatt != null && _state.value.connected) { "BLE disconnected during service discovery." }
        } catch (timeout: TimeoutCancellationException) {
            fail("BLE connection or service discovery timed out. Reconnect near the rover.")
            throw IllegalStateException(_state.value.error, timeout)
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        } catch (error: Exception) {
            fail(error.message ?: "BLE connection failed.")
            throw error
        } finally {
            if (connectWaiter === waiter) connectWaiter = null
        }
    }

    @SuppressLint("MissingPermission") // Runtime revocation is handled by closing the connection.
    suspend fun subscribe(option: BleNotifyCharacteristic) {
        check(!_state.value.subscribing && _state.value.receivingFrom == null) { "BLE receive is already starting or active." }
        _state.update { it.copy(subscribing = true, error = null) }
        val waiter = CompletableDeferred<Unit>()
        notifyWaiter = waiter
        try {
            val gatt = activeGatt ?: error("Connect to a BLE device first.")
            val characteristic = gatt.services.firstOrNull {
                it.uuid.toString() == option.serviceUuid && it.instanceId == option.serviceInstanceId
            }?.characteristics?.firstOrNull {
                it.uuid.toString() == option.characteristicUuid && it.instanceId == option.characteristicInstanceId
            } ?: error("The selected BLE characteristic is no longer available.")
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
                ?: error("This characteristic has no notification configuration descriptor.")
            subscribedCharacteristic = characteristic
            check(gatt.setCharacteristicNotification(characteristic, true)) { "Android could not enable local BLE notifications." }
            val value = if (option.supportsIndication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            check(started) { "Android could not start BLE notification setup." }
            withTimeout(8_000L) { waiter.await() }
            check(activeGatt === gatt) { "BLE disconnected during notification setup." }
            _state.update { it.copy(subscribing = false, receivingFrom = option, error = null) }
            _connectionState.update { it.copy(detail = "BLE subscribed; waiting for valid MAVLink heartbeat") }
        } catch (timeout: TimeoutCancellationException) {
            fail("BLE notification setup timed out. Reconnect to retry.")
            throw IllegalStateException(_state.value.error, timeout)
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        } catch (error: Exception) {
            fail(error.message ?: "Could not subscribe to the selected BLE characteristic.")
            throw error
        } finally {
            if (notifyWaiter === waiter) notifyWaiter = null
        }
    }

    override suspend fun connect() {
        check(activeGatt != null && _state.value.connected) { "BLE GATT is not connected to a selected device." }
    }

    override suspend fun disconnect() = close()

    override suspend fun send(data: ByteArray): Nothing =
        throw TransportException.UnsupportedTransport("BLE MAVLink transmission is disabled in this phase")

    override fun receive(): Flow<ByteArray> = incoming.asSharedFlow()

    override fun close() {
        releaseGatt()
        connectWaiter?.cancel()
        notifyWaiter?.cancel()
        connectWaiter = null
        notifyWaiter = null
        _state.update { it.copy(connected = false, connecting = false, subscribing = false, receivingFrom = null) }
        _connectionState.update { it.copy(status = TransportStatus.DISCONNECTED) }
    }

    private fun fail(message: String) {
        val error = IllegalStateException(message)
        connectWaiter?.completeExceptionally(error)
        notifyWaiter?.completeExceptionally(error)
        releaseGatt()
        _state.update { it.copy(connected = false, connecting = false, subscribing = false,
            receivingFrom = null, error = message) }
        _connectionState.update { it.copy(status = TransportStatus.ERROR, detail = message) }
    }

    @SuppressLint("MissingPermission") // Each cleanup attempt tolerates permission revocation or adapter loss.
    private fun releaseGatt() {
        val gatt = activeGatt
        activeGatt = null // Ignore callbacks from this connection from now on.
        subscribedCharacteristic = null
        if (gatt != null) {
            try { gatt.disconnect() } catch (_: RuntimeException) { }
            try { gatt.close() } catch (_: RuntimeException) { }
        }
    }

    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
