package com.mamadrones.gcs.domain.model

enum class ClassicCheckStage {
    CLOSED, CONNECTING, ERROR, WAITING_SESSION, WAITING_BYTES, BYTES_STALE,
    WAITING_MAVLINK, WAITING_HEARTBEAT, WAITING_MEASUREMENTS, RECEIVING,
}

/**
 * Bounded interpretation of current-session decoder counters while bytes are fresh but no
 * supported MAVLink message has validated. It identifies no physical fault or configuration.
 */
enum class ClassicStreamDiagnosis {
    VALIDATED_RADIO_ONLY,
    NO_MAVLINK_START_BYTES,
    REJECTED_FRAME_CANDIDATES,
    UNVERIFIED_UNSUPPORTED_CANDIDATES,
    NO_VALIDATED_SUPPORTED_FRAME;

    companion object {
        fun from(diagnostics: MavlinkDiagnostics): ClassicStreamDiagnosis = when {
            diagnostics.validatedRadioStatusFrames > 0 -> VALIDATED_RADIO_ONLY
            diagnostics.checksumFailures > 0 || diagnostics.malformedPayloads > 0 ->
                REJECTED_FRAME_CANDIDATES
            diagnostics.mavlinkV1StartBytes + diagnostics.mavlinkV2StartBytes == 0L ->
                NO_MAVLINK_START_BYTES
            diagnostics.unsupportedMessages > 0 || diagnostics.unsupportedFlags > 0 ->
                UNVERIFIED_UNSUPPORTED_CANDIDATES
            else -> NO_VALIDATED_SUPPORTED_FRAME
        }
    }
}

data class ClassicConnectionAssessment(
    val stage: ClassicCheckStage,
    val socketOpen: Boolean = false,
    val freshBytes: Boolean = false,
    val freshHeartbeat: Boolean = false,
    val measurementMessages: List<String> = emptyList(),
    val freshDecodedMessage: Boolean = false,
    val freshRcChannels: Boolean = false,
    val streamDiagnosis: ClassicStreamDiagnosis? = null,
)

/** Receive evidence only. Never authenticates the rover or grants permission to command it. */
object ClassicConnectionCheck {
    // Display freshness, not a validated failsafe or a requested telemetry rate.
    const val FRESH_FOR_MILLIS = 5_000L

    fun assess(active: Boolean, connection: ConnectionState, vehicle: VehicleState,
        nowEpochMillis: Long, nowElapsedMillis: Long? = null): ClassicConnectionAssessment {
        if (connection.status == TransportStatus.ERROR) return ClassicConnectionAssessment(ClassicCheckStage.ERROR)
        if (!active) return ClassicConnectionAssessment(ClassicCheckStage.CLOSED)
        if (connection.status == TransportStatus.CONNECTING) return ClassicConnectionAssessment(ClassicCheckStage.CONNECTING)
        if (connection.status != TransportStatus.OPEN) return ClassicConnectionAssessment(ClassicCheckStage.CLOSED)
        val diagnostics = vehicle.mavlinkDiagnostics
        val openedElapsed = connection.connectedAtElapsedMillis
        val startedElapsed = diagnostics.startedAtElapsedMillis
        val nowElapsed = nowElapsedMillis
        if (!diagnostics.active || diagnostics.linkKind != TelemetryLinkKind.CLASSIC ||
            startedElapsed == null || openedElapsed == null || nowElapsed == null ||
            startedElapsed < 0 || startedElapsed > openedElapsed || openedElapsed > nowElapsed) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_SESSION, socketOpen = true)
        }
        fun currentElapsed(timestamp: Long?) = timestamp != null && timestamp >= openedElapsed && timestamp <= nowElapsed
        fun freshElapsed(timestamp: Long?) = currentElapsed(timestamp) && nowElapsed - timestamp!! <= FRESH_FOR_MILLIS
        if (diagnostics.receivedBytes <= 0 || !currentElapsed(diagnostics.lastBytesAtElapsedMillis)) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_BYTES, socketOpen = true)
        }
        if (!freshElapsed(diagnostics.lastBytesAtElapsedMillis)) {
            return ClassicConnectionAssessment(ClassicCheckStage.BYTES_STALE, socketOpen = true)
        }
        val decoded = diagnostics.decodedMessages > 0 && diagnostics.messageObservations.any {
            it.decoded > 0 && freshElapsed(it.lastDecodedAtElapsedMillis)
        }
        if (!decoded) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_MAVLINK,
                socketOpen = true, freshBytes = true,
                streamDiagnosis = ClassicStreamDiagnosis.from(diagnostics))
        }
        val acceptedHeartbeat = diagnostics.messageObservations.any {
            it.type == ObservedMavlinkMessage.HEARTBEAT && it.accepted > 0 &&
                freshElapsed(it.lastAcceptedAtElapsedMillis)
        }
        if (!vehicle.connected || !freshElapsed(vehicle.lastHeartbeatAtElapsedMillis) || !acceptedHeartbeat) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_HEARTBEAT,
                socketOpen = true, freshBytes = true, freshDecodedMessage = true)
        }
        // Measurement/RC evidence still uses calendar timestamps; do not borrow an invalid epoch boundary.
        val openedEpoch = connection.connectedAtEpochMillis
        val startedEpoch = diagnostics.startedAtEpochMillis
        fun fresh(timestamp: Long?): Boolean = openedEpoch != null && startedEpoch != null &&
            startedEpoch >= 0 && startedEpoch <= openedEpoch && openedEpoch <= nowEpochMillis &&
            timestamp != null && timestamp >= openedEpoch && timestamp <= nowEpochMillis &&
            nowEpochMillis - timestamp <= FRESH_FOR_MILLIS
        // Receipt is distinct from sensor validity: a GPS message can report NO_FIX.
        val messages = listOf(
            "GPS" to vehicle.gps.lastUpdatedAtEpochMillis,
            "Position" to vehicle.position.lastUpdatedAtEpochMillis,
            "Attitude" to vehicle.attitude.lastUpdatedAtEpochMillis,
            "Battery" to vehicle.battery.lastUpdatedAtEpochMillis,
            "Rover HUD" to vehicle.roverHud.lastUpdatedAtEpochMillis,
            "System status" to vehicle.systemStatus.lastUpdatedAtEpochMillis,
            "RC input" to vehicle.rcInput.lastUpdatedAtEpochMillis,
        ).filter { fresh(it.second) }.map { it.first }
        // Both timestamps must belong to this socket session: a previous/foreign RC state is not receipt evidence.
        val freshRcChannels = fresh(vehicle.rcInput.lastUpdatedAtEpochMillis) &&
            diagnostics.messageObservations.any {
                it.type == ObservedMavlinkMessage.RC_CHANNELS && it.accepted > 0 &&
                    fresh(it.lastAcceptedAtEpochMillis)
            }
        return ClassicConnectionAssessment(
            if (messages.isEmpty()) ClassicCheckStage.WAITING_MEASUREMENTS else ClassicCheckStage.RECEIVING,
            socketOpen = true, freshBytes = true, freshHeartbeat = true,
            measurementMessages = messages, freshDecodedMessage = true,
            freshRcChannels = freshRcChannels)
    }
}
