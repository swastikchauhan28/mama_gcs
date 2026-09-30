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

    companion object {
        const val HEARTBEAT_MESSAGE_ID = 0
        const val MAV_MODE_FLAG_SAFETY_ARMED = 128
        const val MAV_AUTOPILOT_INVALID = 8
    }
}
