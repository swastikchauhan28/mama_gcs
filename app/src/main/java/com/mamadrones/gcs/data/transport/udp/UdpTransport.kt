package com.mamadrones.gcs.data.transport.udp

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.VehicleConnectionState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * DatagramSocket-backed MAVLink-agnostic transport. It releases its receiver job and
 * socket on disconnect. Socket state is not vehicle health; heartbeat comes in Phase 3.
 */
class UdpTransport(
    private val config: UdpTransportConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VehicleTransport, AutoCloseable {

    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _connectionState = MutableStateFlow(ConnectionState())
    private val incomingPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = INCOMING_BUFFER_CAPACITY)

    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null

    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        lifecycleMutex.withLock {
            if (socket != null) return
            _connectionState.value = ConnectionState(status = VehicleConnectionState.CONNECTING)
            try {
                val newSocket = withContext(ioDispatcher) {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        soTimeout = config.socketReadTimeoutMillis
                        bind(InetSocketAddress(config.localPort))
                    }
                }
                socket = newSocket
                _connectionState.value = ConnectionState(
                    status = VehicleConnectionState.CONNECTED,
                    connectedAtEpochMillis = System.currentTimeMillis()
                )
                receiverJob = scope.launch { receiveLoop(newSocket) }
            } catch (cause: Exception) {
                _connectionState.value = ConnectionState(
                    status = VehicleConnectionState.ERROR,
                    detail = cause.message ?: "Unable to open UDP socket"
                )
                throw TransportException.ConnectionFailed(cause)
            }
        }
    }

    override suspend fun disconnect() {
        val activeSocket: DatagramSocket?
        val activeReceiver: Job?
        lifecycleMutex.withLock {
            activeSocket = socket
            activeReceiver = receiverJob
            socket = null
            receiverJob = null
            _connectionState.value = ConnectionState(status = VehicleConnectionState.DISCONNECTED)
        }
        activeSocket?.close()
        activeReceiver?.cancel()
    }

    override suspend fun send(data: ByteArray) {
        require(data.isNotEmpty()) { "UDP packet must not be empty" }
        val activeSocket = lifecycleMutex.withLock { socket } ?: throw TransportException.NotConnected()
        try {
            withContext(ioDispatcher) {
                activeSocket.send(DatagramPacket(data, data.size, InetSocketAddress(config.remoteHost, config.remotePort)))
            }
            _connectionState.value = _connectionState.value.withTransmittedPacket(System.currentTimeMillis())
        } catch (cause: IOException) {
            _connectionState.value = _connectionState.value.copy(
                status = VehicleConnectionState.ERROR,
                detail = cause.message ?: "UDP send failed"
            )
            throw TransportException.ConnectionFailed(cause)
        }
    }

    override fun receive(): Flow<ByteArray> = incomingPackets.asSharedFlow()

    override fun close() {
        scope.cancel()
        socket?.close()
        socket = null
        receiverJob = null
        _connectionState.value = ConnectionState(status = VehicleConnectionState.DISCONNECTED)
    }

    private suspend fun receiveLoop(activeSocket: DatagramSocket) {
        val buffer = ByteArray(config.receiveBufferBytes)
        while (!activeSocket.isClosed) {
            try {
                val packet = withContext(ioDispatcher) {
                    DatagramPacket(buffer, buffer.size).also(activeSocket::receive)
                }
                incomingPackets.emit(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
                _connectionState.value = _connectionState.value.withReceivedPacket(System.currentTimeMillis())
            } catch (_: SocketTimeoutException) {
                // Expected; MAVLink heartbeat evaluation is deliberately deferred.
            } catch (_: SocketException) {
                if (!activeSocket.isClosed) markReceiveFailure("UDP socket closed unexpectedly")
                break
            } catch (cause: IOException) {
                markReceiveFailure(cause.message ?: "UDP receive failed")
                break
            }
        }
    }

    private fun markReceiveFailure(detail: String) {
        _connectionState.value = _connectionState.value.copy(status = VehicleConnectionState.ERROR, detail = detail)
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

    private companion object { const val INCOMING_BUFFER_CAPACITY = 64 }
}
