package com.mamadrones.gcs.data.transport.tcp

import com.mamadrones.gcs.data.transport.TransportException
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One receive-only TCP byte stream. Closing the socket aborts both connect and read. */
class TcpTransport(private val address: String, private val port: Int) : VehicleTransport, AutoCloseable {
    override val linkKind = TelemetryLinkKind.TCP
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableConnection = MutableStateFlow(ConnectionState())
    override val connectionState = mutableConnection.asStateFlow()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    private val ready = CompletableDeferred<Unit>()
    private var socket: Socket? = null
    private var started = false
    private var closed = false

    init { require(port in 1..65535); require(address.isNotBlank() && address.length <= 253) }

    override suspend fun connect() {
        val candidate = synchronized(lock) {
            check(!closed && !started) { "TCP session is closed or already started." }
            started = true
            val created = Socket()
            socket = created
            mutableConnection.value = ConnectionState(TransportStatus.CONNECTING, "Connecting to TCP server")
            created
        }
        scope.launch {
            try {
                candidate.connect(InetSocketAddress(address, port), 10_000)
                synchronized(lock) {
                    if (closed) return@launch
                    mutableConnection.value = ConnectionState(TransportStatus.OPEN, "TCP connected; waiting for MAVLink heartbeat",
                        System.currentTimeMillis(), connectedAtElapsedMillis = android.os.SystemClock.elapsedRealtime())
                    ready.complete(Unit)
                }
                val buffer = ByteArray(4096)
                val input = candidate.getInputStream()
                while (isActive) {
                    val count = input.read(buffer)
                    if (count < 0) throw IOException("TCP server closed the connection.")
                    if (count == 0) continue
                    synchronized(lock) {
                        if (closed) return@launch
                        check(incoming.subscriptionCount.value > 0 && incoming.tryEmit(buffer.copyOf(count))) {
                            "TCP receive buffer unavailable or full. Reconnect to retry."
                        }
                        mutableConnection.update { it.copy(packetStatistics = it.packetStatistics.copy(
                            receivedPackets = it.packetStatistics.receivedPackets + 1,
                            lastReceivedAtEpochMillis = System.currentTimeMillis())) }
                    }
                }
            } catch (_: CancellationException) { close() }
            catch (error: Exception) { finish(error.message ?: "TCP connection failed.") }
        }
        try { withTimeout(12_000) { ready.await() } }
        catch (timeout: TimeoutCancellationException) {
            finish("TCP connection timed out.")
            throw IOException("TCP connection timed out.", timeout)
        }
        catch (cancelled: CancellationException) { close(); throw cancelled }
        catch (error: Exception) { finish(error.message ?: "TCP connection failed."); throw error }
    }

    override fun receive(): Flow<ByteArray> = incoming.asSharedFlow()
    override suspend fun send(data: ByteArray): Nothing = throw TransportException.UnsupportedTransport("TCP command transmission")
    override suspend fun disconnect() = close()
    override fun close() = finish(null)

    private fun finish(error: String?) {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            mutableConnection.update { it.copy(status = if (error == null) TransportStatus.DISCONNECTED else TransportStatus.ERROR,
                detail = error ?: "TCP session closed.", connectedAtEpochMillis = null, connectedAtElapsedMillis = null) }
            ready.completeExceptionally(IOException(error ?: "TCP session closed."))
            socket.also { socket = null }
        }
        runCatching { toClose?.close() }
        scope.cancel()
    }
}
