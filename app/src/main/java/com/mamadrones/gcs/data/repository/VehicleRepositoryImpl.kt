package com.mamadrones.gcs.data.repository

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.repository.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VehicleRepositoryImpl @Inject constructor() : VehicleRepository {
    private val _vehicleState = MutableStateFlow(VehicleState())
    override val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    fun onHeartbeat(heartbeat: MavlinkMessage.Heartbeat, receivedAtEpochMillis: Long) {
        _vehicleState.value = _vehicleState.value.copy(
            systemId = heartbeat.systemId,
            componentId = heartbeat.componentId,
            connectionStatus = VehicleConnectionState.CONNECTED,
            lastHeartbeatAtEpochMillis = receivedAtEpochMillis,
            vehicleType = heartbeat.vehicleType,
            autopilotType = heartbeat.autopilotType,
            armed = heartbeat.isArmed,
            mode = ArduRoverMode.fromCustomMode(heartbeat.customMode)
        )
    }

    fun onHeartbeatTimeout() {
        val current = _vehicleState.value
        if (
            current.connectionStatus == VehicleConnectionState.CONNECTED ||
                current.connectionStatus == VehicleConnectionState.CONNECTING
        ) {
            _vehicleState.value = current.copy(connectionStatus = VehicleConnectionState.DEGRADED)
        }
    }

    fun onSessionStarting() {
        _vehicleState.value = _vehicleState.value.copy(connectionStatus = VehicleConnectionState.CONNECTING)
    }

    fun onSessionStopped() {
        _vehicleState.value = _vehicleState.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED)
    }
}

/** ArduPilot Rover custom-mode values. Unknown values are never presented as a guessed mode. */
private object ArduRoverMode {
    private val names = mapOf(
        0L to "MANUAL", 1L to "ACRO", 3L to "STEERING", 4L to "HOLD", 5L to "LOITER",
        6L to "FOLLOW", 7L to "SIMPLE", 10L to "AUTO", 11L to "RTL", 12L to "SMART RTL",
        15L to "GUIDED", 16L to "INITIALISING"
    )

    fun fromCustomMode(customMode: Long): String? = names[customMode]
}
