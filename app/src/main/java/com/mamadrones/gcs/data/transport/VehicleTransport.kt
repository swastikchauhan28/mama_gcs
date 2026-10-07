package com.mamadrones.gcs.data.transport

import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A byte-oriented vehicle link, independent of the underlying connection technology. */
interface VehicleTransport {
    val linkKind: TelemetryLinkKind get() = TelemetryLinkKind.OTHER
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()

    suspend fun disconnect()

    suspend fun send(data: ByteArray)

    fun receive(): Flow<ByteArray>
}
