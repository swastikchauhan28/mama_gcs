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

    init {
        require(heartbeatTimeoutMillis > 0) { "Heartbeat timeout must be positive" }
        require(heartbeatCheckIntervalMillis > 0) { "Heartbeat check interval must be positive" }
    }

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (receiverJob != null) return
            parser.reset()
            router.reset()
            selectedAutopilot = null
            lastSessionHeartbeatAtEpochMillis = null
            vehicleRepository.onSessionStarting()
            val sessionStartedAt = System.currentTimeMillis()
            try {
                // Subscribe first so the first datagram received during socket startup is retained.
                receiverJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    transport.receive().collect { bytes ->
                        val receivedAt = System.currentTimeMillis()
                        parser.feed(bytes).forEach { result -> routeSelectedAutopilot(result, receivedAt) }
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
                vehicleRepository.onSessionStopped()
                throw cancelled
            } catch (exception: Exception) {
                receiverJob?.cancel()
                receiverJob = null
                timeoutJob?.cancel()
                timeoutJob = null
                transport.disconnect()
                vehicleRepository.onSessionStopped()
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
        vehicleRepository.onSessionStopped()
    }

    override fun close() {
        receiverJob?.cancel()
        timeoutJob?.cancel()
        receiverJob = null
        timeoutJob = null
        transport.closeIfPossible()
        vehicleRepository.onSessionStopped()
    }

    private fun routeSelectedAutopilot(result: MavlinkParseResult, receivedAtEpochMillis: Long) {
        val message = (result as? MavlinkParseResult.Message)?.message ?: return
        val source = message.sourceIdentity() ?: return
        val currentSelection = selectedAutopilot
        if (currentSelection == null) {
            val heartbeat = message as? MavlinkMessage.Heartbeat ?: return
            // MAV_AUTOPILOT_INVALID identifies non-autopilot components (e.g. a camera or GCS).
            if (heartbeat.autopilotType == MavlinkMessage.MAV_AUTOPILOT_INVALID) return
            if (source.first == 0 || source.second == 0) return
            selectedAutopilot = heartbeat.systemId to heartbeat.componentId
        } else if (currentSelection != source) {
            return
        }
        if (message is MavlinkMessage.Heartbeat) lastSessionHeartbeatAtEpochMillis = receivedAtEpochMillis
        router.route(result, receivedAtEpochMillis)
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
