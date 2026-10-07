package com.mamadrones.gcs.data.transport

import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.UdpEndpoint
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

data class TransportSessionState(
    val endpoint: UdpEndpoint? = null,
    val connection: ConnectionState = ConnectionState()
)

/**
 * Owns at most one manually opened UDP transport and its receive-only MAVLink session
 * for one UI session. It never transmits vehicle commands or reconnects in the background.
 */
class TransportConnectionManager(
    private val factory: UdpTransportFactory,
    private val sessionFactory: MavlinkSessionFactory,
    private val scope: CoroutineScope,
    private val linkGate: TelemetryLinkGate = TelemetryLinkGate()
) : AutoCloseable {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow(TransportSessionState())
    private var activeTransport: VehicleTransport? = null
    private var activeMavlinkSession: VehicleMavlinkSession? = null
    private var activeEndpoint: UdpEndpoint? = null
    private var observer: Job? = null
    private var closed = false
    private var lease: TelemetryLinkGate.Lease? = null

    val state: StateFlow<TransportSessionState> = mutableState.asStateFlow()

    suspend fun connect(endpoint: UdpEndpoint) {
        lifecycleMutex.withLock {
            check(!closed) { "Transport connection manager is closed" }
            if (activeTransport != null) {
                if (activeEndpoint == endpoint) return
                throw TransportException.AlreadyConnected()
            }
            val acquired = linkGate.acquire("UDP")
            lease = acquired
            val transport: VehicleTransport
            val mavlinkSession: VehicleMavlinkSession
            try {
                transport = factory.create(endpoint)
                try {
                    mavlinkSession = sessionFactory.create(transport, scope)
                } catch (error: Throwable) {
                    closeTransport(transport)
                    throw error
                }
            } catch (error: Throwable) {
                linkGate.release(acquired)
                lease = null
                throw error
            }
            activeTransport = transport
            activeMavlinkSession = mavlinkSession
            activeEndpoint = endpoint
            mutableState.value = TransportSessionState(endpoint, ConnectionState(status = TransportStatus.CONNECTING))
            try {
                mavlinkSession.start()
                mutableState.value = TransportSessionState(endpoint, transport.connectionState.value)
                observer = scope.launch {
                    transport.connectionState.collectLatest { connection ->
                        if (activeTransport === transport) mutableState.value = TransportSessionState(endpoint, connection)
                    }
                }
            } catch (error: Throwable) {
                activeTransport = null
                activeMavlinkSession = null
                activeEndpoint = null
                observer?.cancel()
                observer = null
                runCatching { mavlinkSession.close() }
                closeTransport(transport)
                linkGate.release(acquired)
                lease = null
                mutableState.value = TransportSessionState(
                    endpoint = null,
                    connection = ConnectionState(status = TransportStatus.ERROR, detail = error.message ?: "Unable to open transport")
                )
                throw error
            }
        }
    }

    suspend fun disconnect() {
        lifecycleMutex.withLock {
            val transport = activeTransport
            val mavlinkSession = activeMavlinkSession
            activeTransport = null
            activeMavlinkSession = null
            activeEndpoint = null
            observer?.cancel()
            observer = null
            try {
                mavlinkSession?.stop()
            } finally {
                try {
                    if (mavlinkSession != null) mavlinkSession.close() else closeTransport(transport)
                } finally {
                    lease?.let(linkGate::release)
                    lease = null
                    mutableState.value = TransportSessionState()
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val transport = activeTransport
        val mavlinkSession = activeMavlinkSession
        activeTransport = null
        activeMavlinkSession = null
        activeEndpoint = null
        observer?.cancel()
        observer = null
        mutableState.value = TransportSessionState()
        runCatching { mavlinkSession?.close() }
        if (mavlinkSession == null) closeTransport(transport)
        lease?.let(linkGate::release)
        lease = null
    }

    private fun closeTransport(transport: VehicleTransport?) {
        (transport as? AutoCloseable)?.close()
    }
}
