package com.mamadrones.gcs.data.transport.udp

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A fixed-peer UDP socket for raw vehicle-link bytes. Opening the socket only proves that
 * Android created a local socket; it does not prove a vehicle is reachable or authenticated.
 */
class UdpTransport(
    private val config: UdpTransportConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VehicleTransport, AutoCloseable {
    override val linkKind = com.mamadrones.gcs.domain.model.TelemetryLinkKind.UDP
    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _connectionState = MutableStateFlow(ConnectionState())
    private val incomingPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = INCOMING_BUFFER_CAPACITY)

    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null
    private var generation = 0L
    @Volatile private var closed = false

    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        lifecycleMutex.withLock {
            if (closed) throw TransportException.Closed()
            if (socket != null) return
            _connectionState.value = ConnectionState(status = TransportStatus.CONNECTING)
            try {
                val newSocket = withContext(ioDispatcher) {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        soTimeout = config.socketReadTimeoutMillis
                        bind(InetSocketAddress(config.localPort))
                        // A connected DatagramSocket directs sends and rejects other peers' datagrams.
                        connect(InetSocketAddress(config.remoteHost, config.remotePort))
                    }
                }
                val activeGeneration = ++generation
                socket = newSocket
                _connectionState.value = ConnectionState(
                    status = TransportStatus.OPEN,
                    connectedAtEpochMillis = System.currentTimeMillis()
                )
                receiverJob = scope.launch { receiveLoop(newSocket, activeGeneration) }
            } catch (cause: Exception) {
                _connectionState.value = ConnectionState(
                    status = TransportStatus.ERROR,
                    detail = cause.message ?: "Unable to open UDP socket"
                )
                throw TransportException.ConnectionFailed(cause)
            }
        }
    }

    override suspend fun disconnect() {
        val active = lifecycleMutex.withLock {
            generation++
            val current = ActiveSocket(socket, receiverJob)
            socket = null
            receiverJob = null
            _connectionState.value = ConnectionState(status = TransportStatus.DISCONNECTED)
            current
        }
        active.socket?.close()
        active.receiver?.cancel()
    }

    override suspend fun send(data: ByteArray) {
        require(data.isNotEmpty()) { "UDP packet must not be empty" }
        if (data.size > UdpTransportConfig.MAX_UDP_PAYLOAD_BYTES) throw TransportException.PacketTooLarge(data.size)
        val activeSocket = lifecycleMutex.withLock {
            if (closed) throw TransportException.Closed()
            socket
        } ?: throw TransportException.NotConnected()
        try {
            withContext(ioDispatcher) { activeSocket.send(DatagramPacket(data, data.size)) }
            _connectionState.update { state ->
                if (state.status == TransportStatus.OPEN) state.withTransmittedPacket(System.currentTimeMillis()) else state
            }
        } catch (cause: IOException) {
            markFailure(activeSocket, null, cause.message ?: "UDP send failed")
            throw TransportException.ConnectionFailed(cause)
        }
    }

    override fun receive(): Flow<ByteArray> = incomingPackets.asSharedFlow()

    override fun close() {
        if (closed) return
        closed = true
        generation++
        val activeSocket = socket
        socket = null
        receiverJob?.cancel()
        receiverJob = null
        activeSocket?.close()
        scope.cancel()
        _connectionState.value = ConnectionState(status = TransportStatus.DISCONNECTED)
    }

    private suspend fun receiveLoop(activeSocket: DatagramSocket, activeGeneration: Long) {
        val buffer = ByteArray(config.receiveBufferBytes)
        while (!activeSocket.isClosed) {
            try {
                val packet = withContext(ioDispatcher) {
                    DatagramPacket(buffer, buffer.size).also(activeSocket::receive)
                }
                incomingPackets.emit(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
                updateReceived(activeSocket, activeGeneration)
            } catch (_: SocketTimeoutException) {
                // A quiet link is transport-idle. MAVLink heartbeat evaluation is handled separately.
            } catch (_: SocketException) {
                if (!activeSocket.isClosed) markFailure(activeSocket, activeGeneration, "UDP socket closed unexpectedly")
                break
            } catch (cause: IOException) {
                markFailure(activeSocket, activeGeneration, cause.message ?: "UDP receive failed")
                break
            }
        }
    }

    private suspend fun updateReceived(activeSocket: DatagramSocket, activeGeneration: Long) {
        lifecycleMutex.withLock {
            if (socket !== activeSocket || generation != activeGeneration) return
            _connectionState.update { state -> state.withReceivedPacket(System.currentTimeMillis()) }
        }
    }

    private suspend fun markFailure(activeSocket: DatagramSocket, expectedGeneration: Long?, detail: String) {
        lifecycleMutex.withLock {
            if (socket !== activeSocket || (expectedGeneration != null && generation != expectedGeneration)) return
            _connectionState.update { state -> state.copy(status = TransportStatus.ERROR, detail = detail) }
        }
    }

    private fun ConnectionState.withReceivedPacket(now: Long): ConnectionState = copy(
        packetStatistics = packetStatistics.copy(
            receivedPackets = packetStatistics.receivedPackets + 1,
            lastReceivedAtEpochMillis = now
        )
    )

    private fun ConnectionState.withTransmittedPacket(now: Long): ConnectionState = copy(
        packetStatistics = packetStatistics.copy(
            transmittedPackets = packetStatistics.transmittedPackets + 1,
            lastTransmittedAtEpochMillis = now
        )
    )

    private data class ActiveSocket(val socket: DatagramSocket?, val receiver: Job?)
    private companion object { const val INCOMING_BUFFER_CAPACITY = 64 }
}
