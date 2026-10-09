package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ClassicLinkState(
    val peer: ClassicBluetoothPeer? = null,
    val active: Boolean = false,
    val connection: ConnectionState = ConnectionState(),
)

/** UI-thread-owned controller. Cancelling always closes the socket before releasing ownership. */
class ClassicTelemetryController(
    private val transportFactory: (ClassicBluetoothPeer) -> BluetoothTransport,
    private val sessionFactory: MavlinkSessionFactory,
    private val scope: CoroutineScope,
    private val gate: TelemetryLinkGate,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(ClassicLinkState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var operation: Job? = null
    private var observer: Job? = null
    private var transport: BluetoothTransport? = null
    private var session: VehicleMavlinkSession? = null
    private var lease: TelemetryLinkGate.Lease? = null

    fun connect(peer: ClassicBluetoothPeer) {
        if (state.value.active) return
        val acquired = gate.acquire("Bluetooth Classic")
        lease = acquired
        val token = ++generation
        mutableState.value = ClassicLinkState(peer, true, ConnectionState(TransportStatus.CONNECTING))
        try {
            val link = transportFactory(peer)
            transport = link
            val parser = sessionFactory.create(link, scope)
            session = parser
            operation = scope.launch {
                try {
                    parser.start()
                    if (token != generation) return@launch
                    observer = scope.launch {
                        link.connectionState.collect { connection ->
                            if (token == generation) {
                                mutableState.value = ClassicLinkState(peer, true, connection)
                                if (connection.status == TransportStatus.ERROR || connection.status == TransportStatus.DISCONNECTED) {
                                    disconnect()
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (token == generation) disconnect()
                    throw cancelled
                } catch (error: Exception) {
                    if (token == generation) {
                        mutableState.value = ClassicLinkState(peer, true,
                            ConnectionState(TransportStatus.ERROR, error.message ?: "Bluetooth connection failed."))
                        disconnect()
                    }
                }
            }
        } catch (error: Exception) {
            mutableState.value = ClassicLinkState(peer, true,
                ConnectionState(TransportStatus.ERROR, error.message ?: "Bluetooth connection failed."))
            disconnect()
        }
    }

    fun disconnect() {
        ++generation
        operation?.cancel()
        observer?.cancel()
        operation = null
        observer = null
        // Only close the session this controller owns; another link may own the repository.
        runCatching { session?.close() }
        session = null
        transport?.close()
        transport = null
        lease?.let(gate::release)
        lease = null
        mutableState.update { current -> current.copy(active = false,
            connection = if (current.connection.status == TransportStatus.ERROR) current.connection
            else current.connection.copy(status = TransportStatus.DISCONNECTED,
                detail = "Bluetooth session closed. Connect manually to resume.", connectedAtEpochMillis = null)) }
    }

    override fun close() = disconnect()
}
