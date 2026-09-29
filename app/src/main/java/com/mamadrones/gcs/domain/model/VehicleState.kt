package com.mamadrones.gcs.domain.model

/** Validated vehicle information only. All unavailable MAVLink telemetry remains null. */
data class VehicleState(
    val systemId: Int? = null,
    val componentId: Int? = null,
    val connectionStatus: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
    val lastHeartbeatAtEpochMillis: Long? = null,
    val vehicleType: Int? = null,
    val autopilotType: Int? = null,
    val armed: Boolean? = null,
    val mode: String? = null,
    // Application identity is provisioned separately from MAVLink system/component IDs.
    val vehicleId: String? = null,
    val displayName: String? = null,
    val direction: Direction = Direction.UNKNOWN,
    val speedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null,
    val gps: GpsState = GpsState(),
    val battery: BatteryState = BatteryState(),
    val motors: List<MotorState> = emptyList(),
    val spray: SprayState = SprayState(),
    val hydraulic: HydraulicState = HydraulicState(),
    val health: HealthState = HealthState(),
    val mission: MissionState = MissionState()
) {
    val connected: Boolean get() = connectionStatus == VehicleConnectionState.CONNECTED
}
