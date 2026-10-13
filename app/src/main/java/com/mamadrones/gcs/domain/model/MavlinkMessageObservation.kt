package com.mamadrones.gcs.domain.model

/** The finite telemetry subset decoded by this app, not a vehicle capability list. */
enum class ObservedMavlinkMessage(val messageId: Int) {
    HEARTBEAT(0), SYS_STATUS(1), GPS_RAW_INT(24), ATTITUDE(30), GLOBAL_POSITION_INT(33),
    SERVO_OUTPUT_RAW(36), RC_CHANNELS(65), VFR_HUD(74), BATTERY_STATUS(147),
    AUTOPILOT_VERSION(148), STATUSTEXT(253),
}

/** Bounded, memory-only counters. No raw payloads, coordinates or sender identities. */
data class MavlinkMessageObservation(
    val type: ObservedMavlinkMessage,
    val decodedV1: Long = 0,
    val decodedV2: Long = 0,
    val accepted: Long = 0,
    val beforeHeartbeat: Long = 0,
    val otherSource: Long = 0,
    val lastDecodedAtEpochMillis: Long? = null,
    val lastAcceptedAtEpochMillis: Long? = null,
) {
    val decoded: Long get() = decodedV1 + decodedV2
}
