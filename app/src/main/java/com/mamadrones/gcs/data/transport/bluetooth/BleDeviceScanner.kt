package com.mamadrones.gcs.data.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BleAdvertisement(
    /** Internal per-scan key. The device MAC address is deliberately never exposed or persisted. */
    val key: String,
    val name: String,
    val rssiDbm: Int,
    val serviceUuids: List<String>,
)

data class BleScanState(
    val scanning: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
    val advertisements: List<BleAdvertisement> = emptyList(),
)

/** Short BLE advertisement scan only. It never pairs, connects, reads GATT, or writes controller data. */
class BleDeviceScanner(context: Context) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(BleScanState())
    val state = mutableState.asStateFlow()

    private var activeScanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private val devicesByKey = mutableMapOf<String, BluetoothDevice>()

    @SuppressLint("MissingPermission") // Caller requests the API-appropriate runtime scan permission.
    fun start() {
        stop()
        synchronized(devicesByKey) { devicesByKey.clear() }
        val bleScanner = try {
            appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        } catch (_: SecurityException) {
            mutableState.value = BleScanState(error = "Nearby-device permission is required to scan.")
            return
        }
        if (bleScanner == null) {
            mutableState.value = BleScanState(error = "Bluetooth is off or BLE scanning is unavailable on this device.")
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val advertisedName = record?.deviceName
                    ?.filter { !it.isISOControl() }
                    ?.take(48)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: "Unlabeled BLE device"
                val entry = BleAdvertisement(
                    key = result.device.hashCode().toString(),
                    name = advertisedName,
                    rssiDbm = result.rssi,
                    serviceUuids = record?.serviceUuids.orEmpty().map(ParcelUuid::toString).distinct(),
                )
                synchronized(devicesByKey) { devicesByKey[entry.key] = result.device }
                mutableState.value = mutableState.value.copy(
                    advertisements = (mutableState.value.advertisements.filterNot { it.key == entry.key } + entry)
                        .sortedByDescending(BleAdvertisement::rssiDbm)
                        .take(MAX_RESULTS),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                stop()
                mutableState.value = mutableState.value.copy(
                    scanning = false,
                    finished = true,
                    error = "BLE scan failed (code $errorCode). Check Bluetooth state and retry.",
                )
            }
        }

        activeScanner = bleScanner
        activeCallback = callback
        mutableState.value = BleScanState(scanning = true)
        try {
            bleScanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                callback,
            )
        } catch (_: SecurityException) {
            activeScanner = null
            activeCallback = null
            mutableState.value = BleScanState(error = "Nearby-device permission was not granted.")
        } catch (_: RuntimeException) {
            activeScanner = null
            activeCallback = null
            mutableState.value = BleScanState(error = "BLE scanning could not start. Check Bluetooth state and retry.")
        }
    }

    @SuppressLint("MissingPermission") // Scanner callback can only be stopped after an explicit permission-gated start.
    fun stop() {
        val scanner = activeScanner
        val callback = activeCallback
        activeScanner = null
        activeCallback = null
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission may be revoked while the app is open; no scan data is retained beyond this state.
            } catch (_: RuntimeException) {
                // Adapter may have powered off while a bounded scan was active.
            }
        }
        val current = mutableState.value
        if (current.scanning) mutableState.value = current.copy(scanning = false, finished = true)
    }

    fun permissionDenied() {
        stop()
        mutableState.value = BleScanState(error = "Nearby-device scan permission was denied. No Bluetooth scan was started.")
    }

    fun supportsBle(): Boolean = appContext.packageManager.hasSystemFeature("android.hardware.bluetooth_le")

    /** Returns the transient device handle for this scan result; no address is persisted or exposed. */
    fun deviceForKey(key: String): BluetoothDevice? = synchronized(devicesByKey) { devicesByKey[key] }

    private companion object {
        const val MAX_RESULTS = 80
    }
}
