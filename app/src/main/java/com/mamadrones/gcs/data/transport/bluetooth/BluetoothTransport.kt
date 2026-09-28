package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Boundary only; Bluetooth Classic lifecycle and permissions are implemented in Phase 9. */
class BluetoothTransport : VehicleTransport {
    private val _connectionState = MutableStateFlow(ConnectionState())
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override suspend fun connect(): Nothing = throw TransportException.UnsupportedTransport("Bluetooth Classic transport")
    override suspend fun disconnect() = Unit
    override suspend fun send(data: ByteArray): Nothing = throw TransportException.UnsupportedTransport("Bluetooth Classic transport")
    override fun receive(): Flow<ByteArray> = emptyFlow()
}
