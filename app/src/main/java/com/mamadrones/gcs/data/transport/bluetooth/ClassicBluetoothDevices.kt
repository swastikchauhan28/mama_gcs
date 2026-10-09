package com.mamadrones.gcs.data.transport.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

data class ClassicBluetoothPeer(val address: String, val name: String)

/** Only lists already paired devices; never scans, guesses a PIN, or auto-selects by name. */
class ClassicBluetoothDevices @Inject constructor(@ApplicationContext private val context: Context) {
    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: throw IllegalStateException("Bluetooth is not available on this device.")

    private fun requirePermission() {
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED) { "Allow Nearby devices access to list and connect paired devices." }
    }

    @SuppressLint("MissingPermission") // Checked above; revocation is surfaced to the UI.
    fun pairedDevices(): List<ClassicBluetoothPeer> {
        requirePermission()
        val adapter = adapter()
        check(adapter.isEnabled) { "Turn Bluetooth on in Android settings, then refresh paired devices." }
        return adapter.bondedDevices.orEmpty()
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
            .map { ClassicBluetoothPeer(it.address, it.name?.take(80) ?: "Unnamed paired device") }
            .sortedWith(compareBy({ it.name }, { it.address }))
    }

    @SuppressLint("MissingPermission") // Re-check pairing and permission at connection time.
    fun createTransport(peer: ClassicBluetoothPeer): BluetoothTransport = BluetoothTransport({
        requirePermission()
        val adapter = adapter()
        check(adapter.isEnabled) { "Bluetooth is off. Turn it on and reconnect." }
        val device = adapter.bondedDevices.orEmpty().firstOrNull { it.address == peer.address }
            ?: throw IllegalStateException("Device is no longer paired. Pair it in Android settings and refresh.")
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        object : ClassicSerialSocket {
            override fun connect() = socket.connect()
            override fun read(buffer: ByteArray): Int = socket.inputStream.read(buffer)
            override fun close() = socket.close()
        }
    })

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
