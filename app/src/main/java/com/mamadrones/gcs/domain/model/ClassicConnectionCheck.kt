package com.mamadrones.gcs.domain.model

enum class ClassicCheckStage {
    CLOSED, CONNECTING, ERROR, WAITING_SESSION, WAITING_BYTES, BYTES_STALE,
    WAITING_MAVLINK, WAITING_HEARTBEAT, WAITING_MEASUREMENTS, RECEIVING,
}

data class ClassicConnectionAssessment(
    val stage: ClassicCheckStage,
    val socketOpen: Boolean = false,
    val freshBytes: Boolean = false,
    val freshHeartbeat: Boolean = false,
    val measurementMessages: List<String> = emptyList(),
    val freshDecodedMessage: Boolean = false,
)

/** Receive evidence only. Never authenticates the rover or grants permission to command it. */
object ClassicConnectionCheck {
    // Display freshness, not a validated failsafe or a requested telemetry rate.
    const val FRESH_FOR_MILLIS = 5_000L

    fun assess(active: Boolean, connection: ConnectionState, vehicle: VehicleState,
        nowEpochMillis: Long): ClassicConnectionAssessment {
        if (connection.status == TransportStatus.ERROR) return ClassicConnectionAssessment(ClassicCheckStage.ERROR)
        if (!active) return ClassicConnectionAssessment(ClassicCheckStage.CLOSED)
        if (connection.status == TransportStatus.CONNECTING) return ClassicConnectionAssessment(ClassicCheckStage.CONNECTING)
        if (connection.status != TransportStatus.OPEN) return ClassicConnectionAssessment(ClassicCheckStage.CLOSED)
        val diagnostics = vehicle.mavlinkDiagnostics
        val opened = connection.connectedAtEpochMillis
        val started = diagnostics.startedAtEpochMillis
        if (!diagnostics.active || diagnostics.linkKind != TelemetryLinkKind.CLASSIC ||
            started == null || opened == null || started < 0 || started > opened || opened > nowEpochMillis) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_SESSION, socketOpen = true)
        }
        fun current(timestamp: Long?) = timestamp != null && timestamp >= opened && timestamp <= nowEpochMillis
        fun fresh(timestamp: Long?) = current(timestamp) && nowEpochMillis - timestamp!! <= FRESH_FOR_MILLIS
        if (diagnostics.receivedBytes <= 0 || !current(diagnostics.lastBytesAtEpochMillis)) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_BYTES, socketOpen = true)
        }
        if (!fresh(diagnostics.lastBytesAtEpochMillis)) {
            return ClassicConnectionAssessment(ClassicCheckStage.BYTES_STALE, socketOpen = true)
        }
        val decoded = diagnostics.decodedMessages > 0 && diagnostics.messageObservations.any {
            it.decoded > 0 && fresh(it.lastDecodedAtEpochMillis)
        }
        if (!decoded) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_MAVLINK,
                socketOpen = true, freshBytes = true)
        }
        val acceptedHeartbeat = diagnostics.messageObservations.any {
            it.type == ObservedMavlinkMessage.HEARTBEAT && it.accepted > 0 &&
                fresh(it.lastAcceptedAtEpochMillis)
        }
        if (!vehicle.connected || !fresh(vehicle.lastHeartbeatAtEpochMillis) || !acceptedHeartbeat) {
            return ClassicConnectionAssessment(ClassicCheckStage.WAITING_HEARTBEAT,
                socketOpen = true, freshBytes = true, freshDecodedMessage = true)
        }
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
        return ClassicConnectionAssessment(
            if (messages.isEmpty()) ClassicCheckStage.WAITING_MEASUREMENTS else ClassicCheckStage.RECEIVING,
            socketOpen = true, freshBytes = true, freshHeartbeat = true,
            measurementMessages = messages, freshDecodedMessage = true)
    }
}
