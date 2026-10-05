package com.mamadrones.gcs.domain.model

/** No telemetry is represented as null/UNKNOWN, never as an inferred safe state. */
enum class SubsystemConnection { NOT_CONNECTED, CONNECTED, STALE, ERROR }
enum class SwitchState { UNKNOWN, ON, OFF }
enum class Direction { UNKNOWN, FORWARD, REVERSE, STOPPED }
enum class GpsFix { UNKNOWN, NO_FIX, FIX_2D, FIX_3D, DGPS, RTK_FLOAT, RTK_FIXED }
enum class HealthLevel { UNKNOWN, GOOD, WARNING, CRITICAL }

data class GpsState(
    val fix: GpsFix = GpsFix.UNKNOWN,
    val satellites: Int? = null,
    val hdop: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

data class BatteryState(
    val percentage: Int? = null,
    val voltage: Double? = null,
    val currentAmps: Double? = null,
    val temperatureCelsius: Double? = null,
    val chargeState: Int? = null,
    val batteryId: Int? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

data class GlobalPositionState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMetersMsl: Double? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

/** Geographic point retained for the current telemetry session's displayed trail. */
data class GeoTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMillis: Long
)

/** Attitude and angular rates use the MAVLink-native radian units. */
data class AttitudeState(
    val rollRadians: Double? = null,
    val pitchRadians: Double? = null,
    val yawRadians: Double? = null,
    val rollRateRadiansPerSecond: Double? = null,
    val pitchRateRadiansPerSecond: Double? = null,
    val yawRateRadiansPerSecond: Double? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

data class AutopilotSystemStatus(
    val sensorsPresent: Long? = null,
    val sensorsEnabled: Long? = null,
    val sensorsHealthy: Long? = null,
    val cpuLoadPercent: Double? = null,
    val communicationDropPercent: Double? = null,
    val communicationErrors: Int? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

data class VehicleStatusText(
    val severity: Int,
    val text: String,
    val receivedAtEpochMillis: Long
)

/** RPM and electrical RPM are deliberately distinct; conversion requires motor pole count. */
data class MotorState(
    val id: String,
    val connection: SubsystemConnection = SubsystemConnection.NOT_CONNECTED,
    val temperatureCelsius: Double? = null,
    val controllerTemperatureCelsius: Double? = null,
    val rpm: Double? = null,
    val electricalRpm: Double? = null,
    val motorCurrentAmps: Double? = null,
    val inputCurrentAmps: Double? = null,
    val voltage: Double? = null,
    val dutyCycle: Double? = null,
    val faultCode: String? = null,
    val lastUpdatedAtEpochMillis: Long? = null
)

data class SprayPumpState(val power: SwitchState = SwitchState.UNKNOWN, val fault: String? = null)
data class NozzleState(val id: String, val power: SwitchState = SwitchState.UNKNOWN)
data class SprayState(
    val connection: SubsystemConnection = SubsystemConnection.NOT_CONNECTED,
    val pump: SprayPumpState = SprayPumpState(),
    // null means the inventory is unknown; empty means a confirmed empty inventory.
    val nozzles: List<NozzleState>? = null,
    val pressureBar: Double? = null,
    val flowLitersPerMinute: Double? = null,
    val spraying: SwitchState = SwitchState.UNKNOWN,
    val fault: String? = null
)

data class HydraulicState(
    val connection: SubsystemConnection = SubsystemConnection.NOT_CONNECTED,
    val enabled: SwitchState = SwitchState.UNKNOWN,
    val pump: SwitchState = SwitchState.UNKNOWN,
    val valve: SwitchState = SwitchState.UNKNOWN,
    val pressureBar: Double? = null,
    val temperatureCelsius: Double? = null,
    val fault: String? = null
)

data class HealthState(
    val overall: HealthLevel = HealthLevel.UNKNOWN,
    val communication: HealthLevel = HealthLevel.UNKNOWN,
    val gps: HealthLevel = HealthLevel.UNKNOWN,
    val battery: HealthLevel = HealthLevel.UNKNOWN,
    val motors: HealthLevel = HealthLevel.UNKNOWN,
    val spray: HealthLevel = HealthLevel.UNKNOWN,
    val hydraulic: HealthLevel = HealthLevel.UNKNOWN
)

enum class MissionStatus { UNKNOWN, IDLE, UPLOADING, DOWNLOADING, READY, RUNNING, PAUSED, COMPLETED, FAILED }
data class MissionState(
    val status: MissionStatus = MissionStatus.UNKNOWN,
    val waypointCount: Int? = null,
    val currentWaypoint: Int? = null,
    val progress: Double? = null,
    val error: String? = null
)

data class DiagnosticsState(
    val receivedPackets: Long? = null,
    val transmittedPackets: Long? = null,
    val parseErrors: Long? = null,
    val lastPacketAtEpochMillis: Long? = null
)
