package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates one transport and MAVLink parser. It is deliberately not started by the
 * app until a user-configured transport is available. Timeout denotes MAVLink liveness,
 * not merely an open UDP socket.
 */
class MavlinkSession(
    private val transport: VehicleTransport,
    private val parser: MavlinkParser,
    private val router: MavlinkMessageRouter,
    private val vehicleRepository: VehicleRepositoryImpl,
    private val heartbeatTimeoutMillis: Long = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS,
    private val heartbeatCheckIntervalMillis: Long = HEARTBEAT_CHECK_INTERVAL_MILLIS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : VehicleMavlinkSession {
    private val lifecycleMutex = Mutex()
    private var receiverJob: Job? = null
    private var timeoutJob: Job? = null
    @Volatile private var lastSessionHeartbeatAtEpochMillis: Long? = null
    private var selectedAutopilot: Pair<Int, Int>? = null
    @Volatile private var closed = false

    init {
        require(heartbeatTimeoutMillis > 0) { "Heartbeat timeout must be positive" }
        require(heartbeatCheckIntervalMillis > 0) { "Heartbeat check interval must be positive" }
    }

    override suspend fun start() {
        lifecycleMutex.withLock {
            check(!closed) { "MAVLink session is closed" }
            if (receiverJob != null) return
            parser.reset()
            router.reset()
            selectedAutopilot = null
            lastSessionHeartbeatAtEpochMillis = null
            val sessionStartedAt = System.currentTimeMillis()
            val diagnostics = MavlinkDiagnosticsAccumulator(transport.linkKind, sessionStartedAt)
            vehicleRepository.onSessionStarting(diagnostics.snapshot)
            try {
                // Subscribe first so the first datagram received during socket startup is retained.
                receiverJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    transport.receive().collect { bytes ->
                        val receivedAt = System.currentTimeMillis()
                        diagnostics.recordBytes(bytes.size, receivedAt)
                        parser.feed(bytes).forEach { result ->
                            val disposition = routeSelectedAutopilot(result, receivedAt)
                            diagnostics.recordResult(result, disposition, receivedAt)
                        }
                        vehicleRepository.onMavlinkDiagnostics(diagnostics.snapshot)
                    }
                }
                transport.connect()
                timeoutJob = scope.launch {
                    while (isActive) {
                        delay(heartbeatCheckIntervalMillis)
                        val lastHeartbeat = lastSessionHeartbeatAtEpochMillis ?: sessionStartedAt
                        if (System.currentTimeMillis() - lastHeartbeat > heartbeatTimeoutMillis) {
                            vehicleRepository.onHeartbeatTimeout()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                receiverJob?.cancel()
                receiverJob = null
                timeoutJob?.cancel()
                timeoutJob = null
                transport.disconnect()
                if (!closed) vehicleRepository.onSessionStopped()
                throw cancelled
            } catch (exception: Exception) {
                receiverJob?.cancel()
                receiverJob = null
                timeoutJob?.cancel()
                timeoutJob = null
                transport.disconnect()
                if (!closed) vehicleRepository.onSessionStopped()
                throw exception
            }
        }
    }

    override suspend fun stop() {
        val activeReceiver: Job?
        val activeTimeout: Job?
        lifecycleMutex.withLock {
            activeReceiver = receiverJob
            activeTimeout = timeoutJob
            receiverJob = null
            timeoutJob = null
        }
        activeReceiver?.cancel()
        activeTimeout?.cancel()
        transport.disconnect()
        if (!closed) vehicleRepository.onSessionStopped()
    }

    override fun close() {
        if (closed) return
        closed = true
        receiverJob?.cancel()
        timeoutJob?.cancel()
        receiverJob = null
        timeoutJob = null
        transport.closeIfPossible()
        vehicleRepository.onSessionStopped()
    }

    private fun routeSelectedAutopilot(result: MavlinkParseResult, receivedAtEpochMillis: Long): MessageDisposition? {
        val message = (result as? MavlinkParseResult.Message)?.message ?: return null
        val source = message.sourceIdentity() ?: return MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT
        val currentSelection = selectedAutopilot
        if (currentSelection == null) {
            val heartbeat = message as? MavlinkMessage.Heartbeat ?: return MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT
            // MAV_AUTOPILOT_INVALID identifies non-autopilot components (e.g. a camera or GCS).
            if (heartbeat.autopilotType == MavlinkMessage.MAV_AUTOPILOT_INVALID) return MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT
            if (source.first == 0 || source.second == 0) return MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT
            selectedAutopilot = heartbeat.systemId to heartbeat.componentId
        } else if (currentSelection != source) {
            return MessageDisposition.OTHER_SOURCE
        }
        if (message is MavlinkMessage.Heartbeat) lastSessionHeartbeatAtEpochMillis = receivedAtEpochMillis
        router.route(result, receivedAtEpochMillis)
        return MessageDisposition.ACCEPTED
    }

    private fun MavlinkMessage.sourceIdentity(): Pair<Int, Int>? = when (this) {
        is MavlinkMessage.Heartbeat -> systemId to componentId
        is MavlinkMessage.GpsRawInt -> systemId to componentId
        is MavlinkMessage.GlobalPositionInt -> systemId to componentId
        is MavlinkMessage.Attitude -> systemId to componentId
        is MavlinkMessage.VfrHud -> systemId to componentId
        is MavlinkMessage.SystemStatus -> systemId to componentId
        is MavlinkMessage.BatteryStatus -> systemId to componentId
        is MavlinkMessage.StatusText -> systemId to componentId
    }

    private fun VehicleTransport.closeIfPossible() {
        (this as? AutoCloseable)?.close()
    }

    private companion object {
        const val DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 5_000L
        const val HEARTBEAT_CHECK_INTERVAL_MILLIS = 500L
    }
}
