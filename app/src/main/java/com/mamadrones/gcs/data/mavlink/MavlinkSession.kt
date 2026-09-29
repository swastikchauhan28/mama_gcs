package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val heartbeatTimeoutMillis: Long = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS
) {
    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var receiverJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        require(heartbeatTimeoutMillis > 0) { "Heartbeat timeout must be positive" }
    }

    suspend fun start() {
        lifecycleMutex.withLock {
            if (receiverJob != null) return
            vehicleRepository.onSessionStarting()
            try {
                transport.connect()
                receiverJob = scope.launch {
                    transport.receive().collect { bytes ->
                        parser.feed(bytes).forEach { router.route(it, System.currentTimeMillis()) }
                    }
                }
                val sessionStartedAt = System.currentTimeMillis()
                timeoutJob = scope.launch {
                    while (isActive) {
                        delay(HEARTBEAT_CHECK_INTERVAL_MILLIS)
                        val lastHeartbeat = vehicleRepository.vehicleState.value.lastHeartbeatAtEpochMillis ?: sessionStartedAt
                        if (System.currentTimeMillis() - lastHeartbeat > heartbeatTimeoutMillis) {
                            vehicleRepository.onHeartbeatTimeout()
                        }
                    }
                }
            } catch (exception: Exception) {
                vehicleRepository.onSessionStopped()
                throw exception
            }
        }
    }

    suspend fun stop() {
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

    private companion object {
        const val DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 5_000L
        const val HEARTBEAT_CHECK_INTERVAL_MILLIS = 500L
    }
}
