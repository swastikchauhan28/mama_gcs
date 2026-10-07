package com.mamadrones.gcs.data.mavlink

data class MavlinkFrame(
    val sequence: Int,
    val systemId: Int,
    val componentId: Int,
    val messageId: Int,
    val payload: ByteArray
)

sealed interface MavlinkMessage {
    data class Heartbeat(
        val systemId: Int,
        val componentId: Int,
        val customMode: Long,
        val vehicleType: Int,
        val autopilotType: Int,
        val baseMode: Int,
        val systemStatus: Int,
        val mavlinkVersion: Int
    ) : MavlinkMessage {
        val isArmed: Boolean get() = baseMode and MAV_MODE_FLAG_SAFETY_ARMED != 0
    }

    data class GpsRawInt(
        val systemId: Int,
        val componentId: Int,
        val fixType: Int,
        val latitudeE7: Int,
        val longitudeE7: Int,
        val altitudeMillimetersMsl: Int,
        val eph: Int,
        val satellitesVisible: Int
    ) : MavlinkMessage

    data class GlobalPositionInt(
        val systemId: Int,
        val componentId: Int,
        val latitudeE7: Int,
        val longitudeE7: Int,
        val altitudeMillimetersMsl: Int,
        val vxCentimetersPerSecond: Int,
        val vyCentimetersPerSecond: Int,
        val vzCentimetersPerSecond: Int,
        val headingCentidegrees: Int
    ) : MavlinkMessage

    data class Attitude(
        val systemId: Int,
        val componentId: Int,
        val rollRadians: Float,
        val pitchRadians: Float,
        val yawRadians: Float,
        val rollRateRadiansPerSecond: Float,
        val pitchRateRadiansPerSecond: Float,
        val yawRateRadiansPerSecond: Float
    ) : MavlinkMessage

    /** Rover-relevant fields from MAVLink VFR_HUD; airspeed is intentionally omitted. */
    data class VfrHud(
        val systemId: Int,
        val componentId: Int,
        val groundSpeedMetersPerSecond: Float,
        val headingDegrees: Int,
        val throttlePercent: Int,
        val altitudeMetersMsl: Float,
        val climbRateMetersPerSecond: Float
    ) : MavlinkMessage

    data class SystemStatus(
        val systemId: Int,
        val componentId: Int,
        val sensorsPresent: Long,
        val sensorsEnabled: Long,
        val sensorsHealthy: Long,
        val loadDecipercent: Int,
        val voltageMillivolts: Int,
        val currentCentiamps: Int,
        val batteryRemainingPercent: Int,
        val communicationDropCentipercent: Int,
        val communicationErrors: Int
    ) : MavlinkMessage

    data class BatteryStatus(
        val systemId: Int,
        val componentId: Int,
        val batteryId: Int,
        val temperatureCentidegreesCelsius: Int,
        val cellVoltagesMillivolts: List<Int>,
        val currentCentiamps: Int,
        val remainingPercent: Int,
        val chargeState: Int? = null
    ) : MavlinkMessage

    data class StatusText(
        val systemId: Int,
        val componentId: Int,
        val severity: Int,
        val textChunk: ByteArray,
        val id: Int,
        val chunkSequence: Int
    ) : MavlinkMessage

    companion object {
        const val HEARTBEAT_MESSAGE_ID = 0
        const val SYS_STATUS_MESSAGE_ID = 1
        const val GPS_RAW_INT_MESSAGE_ID = 24
        const val ATTITUDE_MESSAGE_ID = 30
        const val VFR_HUD_MESSAGE_ID = 74
        const val GLOBAL_POSITION_INT_MESSAGE_ID = 33
        const val BATTERY_STATUS_MESSAGE_ID = 147
        const val STATUSTEXT_MESSAGE_ID = 253
        const val MAV_MODE_FLAG_SAFETY_ARMED = 128
        const val MAV_AUTOPILOT_INVALID = 8
    }
}
