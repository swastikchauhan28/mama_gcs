package com.mamadrones.gcs.data.repository

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.domain.repository.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun onGpsRawInt(message: MavlinkMessage.GpsRawInt, receivedAtEpochMillis: Long) {
        val validFix = message.fixType in GPS_FIX_2D..GPS_FIX_TYPE_MAX_KNOWN
        val latitude = message.latitudeE7 / DEGREES_E7
        val longitude = message.longitudeE7 / DEGREES_E7
        _vehicleState.value = _vehicleState.value.copy(
            gps = GpsState(
                fix = message.fixType.toGpsFix(),
                satellites = message.satellitesVisible.takeUnless { it == UINT8_UNKNOWN },
                hdop = message.eph.takeUnless { it == UINT16_UNKNOWN }?.div(100.0),
                latitude = latitude.takeIf { validFix && it in -90.0..90.0 && longitude in -180.0..180.0 },
                longitude = longitude.takeIf { validFix && latitude in -90.0..90.0 && it in -180.0..180.0 },
                altitudeMeters = (message.altitudeMillimetersMsl / MILLIMETERS_PER_METER).takeIf { validFix },
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            )
        )
    }

    fun onGlobalPositionInt(message: MavlinkMessage.GlobalPositionInt, receivedAtEpochMillis: Long) {
        val latitude = message.latitudeE7 / DEGREES_E7
        val longitude = message.longitudeE7 / DEGREES_E7
        val validCoordinates = latitude in -90.0..90.0 && longitude in -180.0..180.0
        val speed = kotlin.math.hypot(
            message.vxCentimetersPerSecond.toDouble(),
            message.vyCentimetersPerSecond.toDouble()
        ) / CENTIMETERS_PER_METER
        val track = _vehicleState.value.positionTrack
        val nextTrack = if (validCoordinates) {
            val point = GeoTrackPoint(latitude, longitude, receivedAtEpochMillis)
            val last = track.lastOrNull()
            if (last == null || distanceMeters(last.latitude, last.longitude, latitude, longitude) >= MIN_TRACK_STEP_METERS) {
                (track + point).takeLast(MAX_TRACK_POINTS)
            } else track
        } else track
        _vehicleState.value = _vehicleState.value.copy(
            position = GlobalPositionState(
                latitude = latitude.takeIf { validCoordinates },
                longitude = longitude.takeIf { validCoordinates },
                altitudeMetersMsl = message.altitudeMillimetersMsl / MILLIMETERS_PER_METER,
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            ),
            positionTrack = nextTrack,
            speedMetersPerSecond = speed.takeIf(Double::isFinite),
            headingDegrees = message.headingCentidegrees
                .takeUnless { it == UINT16_UNKNOWN || it > MAX_HEADING_CENTIDEGREES }
                ?.div(CENTIDEGREES_PER_DEGREE),
            kinematicsLastUpdatedAtEpochMillis = receivedAtEpochMillis
        )
    }

    fun onAttitude(message: MavlinkMessage.Attitude, receivedAtEpochMillis: Long) {
        _vehicleState.value = _vehicleState.value.copy(
            attitude = AttitudeState(
                rollRadians = message.rollRadians.toDouble().validAngle(),
                pitchRadians = message.pitchRadians.toDouble().validAngle(),
                yawRadians = message.yawRadians.toDouble().validAngle(),
                rollRateRadiansPerSecond = message.rollRateRadiansPerSecond.toDouble().takeIf(Double::isFinite),
                pitchRateRadiansPerSecond = message.pitchRateRadiansPerSecond.toDouble().takeIf(Double::isFinite),
                yawRateRadiansPerSecond = message.yawRateRadiansPerSecond.toDouble().takeIf(Double::isFinite),
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            )
        )
    }

    fun onVfrHud(message: MavlinkMessage.VfrHud, receivedAtEpochMillis: Long) {
        _vehicleState.value = _vehicleState.value.copy(
            roverHud = RoverHudState(
                groundSpeedMetersPerSecond = message.groundSpeedMetersPerSecond
                    .toDouble().takeIf { it.isFinite() && it >= 0.0 },
                headingDegrees = message.headingDegrees.takeIf { it in 0..360 }?.let { (it % 360).toDouble() },
                throttlePercent = message.throttlePercent.takeIf { it in 0..100 },
                altitudeMetersMsl = message.altitudeMetersMsl.toDouble().takeIf(Double::isFinite),
                climbRateMetersPerSecond = message.climbRateMetersPerSecond.toDouble().takeIf(Double::isFinite),
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            )
        )
    }

    fun onSystemStatus(message: MavlinkMessage.SystemStatus, receivedAtEpochMillis: Long) {
        _vehicleState.value = _vehicleState.value.copy(
            battery = BatteryState(
                percentage = message.batteryRemainingPercent.takeIf { it in 0..100 },
                voltage = message.voltageMillivolts.takeUnless { it == UINT16_UNKNOWN }
                    ?.div(MILLIVOLTS_PER_VOLT),
                currentAmps = message.currentCentiamps.takeUnless { it == CURRENT_UNKNOWN }
                    ?.div(CENTIAMPS_PER_AMP),
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            ),
            systemStatus = AutopilotSystemStatus(
                sensorsPresent = message.sensorsPresent,
                sensorsEnabled = message.sensorsEnabled,
                sensorsHealthy = message.sensorsHealthy,
                cpuLoadPercent = message.loadDecipercent.takeIf { it in 0..MAX_LOAD_DECIPERCENT }
                    ?.div(DECIPERCENT_PER_PERCENT),
                communicationDropPercent = message.communicationDropCentipercent
                    .takeIf { it in 0..MAX_DROP_CENTIPERCENT }
                    ?.div(CENTIPERCENT_PER_PERCENT),
                communicationErrors = message.communicationErrors,
                lastUpdatedAtEpochMillis = receivedAtEpochMillis
            )
        )
    }

    fun onBatteryStatus(message: MavlinkMessage.BatteryStatus, receivedAtEpochMillis: Long) {
        val currentState = _vehicleState.value
        if (currentState.batteries.none { it.batteryId == message.batteryId } &&
            currentState.batteries.size >= MAX_BATTERY_INSTANCES
        ) return

        val battery = BatteryState(
            percentage = message.remainingPercent.takeIf { it in 0..100 },
            voltage = message.totalVoltageVolts(),
            currentAmps = message.currentCentiamps.takeUnless { it == CURRENT_UNKNOWN }
                ?.div(CENTIAMPS_PER_AMP),
            temperatureCelsius = message.temperatureCentidegreesCelsius
                .takeUnless { it == INT16_UNKNOWN }?.div(CENTIDEGREES_PER_DEGREE),
            chargeState = message.chargeState,
            batteryId = message.batteryId,
            lastUpdatedAtEpochMillis = receivedAtEpochMillis
        )
        _vehicleState.value = currentState.copy(
            batteries = (currentState.batteries.filterNot { it.batteryId == message.batteryId } + battery)
                .sortedBy { it.batteryId }
        )
    }

    fun onStatusText(severity: Int, text: String, receivedAtEpochMillis: Long) {
        if (text.isBlank()) return
        _vehicleState.value = _vehicleState.value.copy(
            statusTexts = (listOf(VehicleStatusText(severity, text, receivedAtEpochMillis)) +
                _vehicleState.value.statusTexts).take(MAX_STATUS_TEXTS)
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

    fun onSessionStarting(diagnostics: MavlinkDiagnostics = MavlinkDiagnostics()) {
        val current = _vehicleState.value
        _vehicleState.value = VehicleState(
            vehicleId = current.vehicleId,
            displayName = current.displayName,
            connectionStatus = VehicleConnectionState.CONNECTING,
            mavlinkDiagnostics = diagnostics,
        )
    }

    fun onSessionStopped() {
        _vehicleState.update { it.copy(connectionStatus = VehicleConnectionState.DISCONNECTED,
            mavlinkDiagnostics = it.mavlinkDiagnostics.copy(active = false)) }
    }

    fun onMavlinkDiagnostics(diagnostics: MavlinkDiagnostics) {
        _vehicleState.update { it.copy(mavlinkDiagnostics = diagnostics) }
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val meanLatitude = Math.toRadians((lat1 + lat2) / 2.0)
    val northMeters = (lat2 - lat1) * METERS_PER_DEGREE_LATITUDE
    val eastMeters = (lon2 - lon1) * METERS_PER_DEGREE_LATITUDE * kotlin.math.cos(meanLatitude)
    return kotlin.math.hypot(northMeters, eastMeters)
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

private fun Int.toGpsFix() = when (this) {
    0, 1 -> GpsFix.NO_FIX
    2 -> GpsFix.FIX_2D
    3 -> GpsFix.FIX_3D
    4 -> GpsFix.DGPS
    5 -> GpsFix.RTK_FLOAT
    6 -> GpsFix.RTK_FIXED
    else -> GpsFix.UNKNOWN
}

private fun Double.validAngle(): Double? = takeIf { isFinite() && it in -Math.PI..Math.PI }

private fun MavlinkMessage.BatteryStatus.totalVoltageVolts(): Double? {
    val knownCells = mutableListOf<Int>()
    var unknownSeen = false
    cellVoltagesMillivolts.forEach { voltage ->
        if (voltage == UINT16_UNKNOWN) {
            unknownSeen = true
        } else {
            if (unknownSeen) return null
            knownCells += voltage
        }
    }
    return knownCells.takeIf { it.isNotEmpty() }?.sumOf(Int::toLong)?.div(MILLIVOLTS_PER_VOLT)
}

private const val GPS_FIX_2D = 2
private const val GPS_FIX_TYPE_MAX_KNOWN = 6
private const val UINT8_UNKNOWN = 255
private const val UINT16_UNKNOWN = 65_535
private const val INT16_UNKNOWN = 32_767
private const val CURRENT_UNKNOWN = -1
private const val MAX_HEADING_CENTIDEGREES = 35_999
private const val MAX_LOAD_DECIPERCENT = 1_000
private const val MAX_DROP_CENTIPERCENT = 10_000
private const val MAX_BATTERY_INSTANCES = 16
private const val MAX_STATUS_TEXTS = 50
private const val MAX_TRACK_POINTS = 2_000
private const val MIN_TRACK_STEP_METERS = 0.5
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
private const val DEGREES_E7 = 10_000_000.0
private const val MILLIMETERS_PER_METER = 1_000.0
private const val CENTIMETERS_PER_METER = 100.0
private const val MILLIVOLTS_PER_VOLT = 1_000.0
private const val CENTIAMPS_PER_AMP = 100.0
private const val CENTIDEGREES_PER_DEGREE = 100.0
private const val DECIPERCENT_PER_PERCENT = 10.0
private const val CENTIPERCENT_PER_PERCENT = 100.0
