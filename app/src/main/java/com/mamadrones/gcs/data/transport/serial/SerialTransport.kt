package com.mamadrones.gcs.data.transport.serial

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Future extension point for wired serial vehicle links. */
class SerialTransport : VehicleTransport {
    private val _connectionState = MutableStateFlow(ConnectionState())
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override suspend fun connect(): Nothing = throw TransportException.UnsupportedTransport("Serial transport")
    override suspend fun disconnect() = Unit
    override suspend fun send(data: ByteArray): Nothing = throw TransportException.UnsupportedTransport("Serial transport")
    override fun receive(): Flow<ByteArray> = emptyFlow()
}
