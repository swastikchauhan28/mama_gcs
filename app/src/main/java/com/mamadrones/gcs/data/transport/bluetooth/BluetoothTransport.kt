package com.mamadrones.gcs.data.transport.bluetooth

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Blocking serial socket boundary. close() must unblock connect() and read(). No write API. */
interface ClassicSerialSocket : AutoCloseable {
    fun connect()
    fun read(buffer: ByteArray): Int
}

/** One foreground RFCOMM receive session; create a new instance to reconnect. */
class BluetoothTransport(
    private val socketFactory: () -> ClassicSerialSocket,
    private val connectTimeoutMillis: Long = 20_000L,
) : VehicleTransport, AutoCloseable {
    override val linkKind = TelemetryLinkKind.CLASSIC
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableConnection = MutableStateFlow(ConnectionState())
    override val connectionState = mutableConnection.asStateFlow()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    private val ready = CompletableDeferred<Unit>()
    private var socket: ClassicSerialSocket? = null
    private var started = false
    private var closed = false

    init { require(connectTimeoutMillis > 0) }

    override suspend fun connect() {
        synchronized(lock) {
            check(!closed) { "Bluetooth session is closed. Reconnect to retry." }
            check(!started) { "Bluetooth session has already started." }
            started = true
            mutableConnection.value = ConnectionState(TransportStatus.CONNECTING, "Connecting to paired serial device")
        }
        try {
            // Creation does not connect. Publishing under the lock lets cancellation close
            // even a socket whose blocking connection has not started yet.
            val candidate = socketFactory()
            synchronized(lock) {
                if (closed) {
                    candidate.close()
                    throw CancellationException("Bluetooth connection cancelled")
                }
                socket = candidate
            }
            scope.launch {
                try {
                    candidate.connect()
                    synchronized(lock) {
                        if (closed) return@launch
                        mutableConnection.value = ConnectionState(TransportStatus.OPEN,
                            "Serial link open; waiting for MAVLink heartbeat", System.currentTimeMillis())
                        ready.complete(Unit)
                    }
                    val buffer = ByteArray(4096)
                    while (isActive) {
                        val count = candidate.read(buffer)
                        if (count < 0) throw IOException("Bluetooth device disconnected. Reconnect to retry.")
                        if (count == 0) continue
                        synchronized(lock) {
                            if (closed) return@launch
                            // A missing serial fragment can corrupt framing. End the session
                            // instead of silently accepting a stream with dropped chunks.
                            check(incoming.subscriptionCount.value > 0 && incoming.tryEmit(buffer.copyOf(count))) {
                                "Bluetooth receive buffer unavailable or full. Reconnect to retry."
                            }
                            val now = System.currentTimeMillis()
                            mutableConnection.update { it.copy(packetStatistics = it.packetStatistics.copy(
                                receivedPackets = it.packetStatistics.receivedPackets + 1,
                                lastReceivedAtEpochMillis = now,
                            )) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    close()
                } catch (error: Exception) {
                    finish(error.message ?: "Bluetooth receive failed.")
                }
            }
            withTimeout(connectTimeoutMillis) { ready.await() }
        } catch (timeout: TimeoutCancellationException) {
            finish("Bluetooth connection timed out. Check power, range and other connected apps.")
            throw IOException("Bluetooth connection timed out.", timeout)
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        } catch (error: Exception) {
            finish(error.message ?: "Bluetooth connection failed.")
            throw error
        }
    }

    override fun receive(): Flow<ByteArray> = incoming.asSharedFlow()
    override suspend fun send(data: ByteArray): Nothing =
        throw TransportException.UnsupportedTransport("Bluetooth command transmission")
    override suspend fun disconnect() = close()
    override fun close() = finish(null)

    private fun finish(error: String?) {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            mutableConnection.update { it.copy(
                status = if (error == null) TransportStatus.DISCONNECTED else TransportStatus.ERROR,
                detail = error ?: "Bluetooth session closed",
                connectedAtEpochMillis = null,
            ) }
            ready.completeExceptionally(IOException(error ?: "Bluetooth session closed"))
            socket.also { socket = null }
        }
        // Android BluetoothSocket.close aborts both blocking connect and read operations.
        runCatching { toClose?.close() }
        scope.cancel()
    }
}
