package com.mamadrones.gcs.domain.model

/** Display-only snapshot. Contains no device identifier, characteristic value or command path. */
data class BleReceiveEvidence(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val closing: Boolean = false,
    val hasError: Boolean = false,
    val receiveOptionAvailable: Boolean = false,
    val subscribing: Boolean = false,
    val subscribed: Boolean = false,
    val connectedAtEpochMillis: Long? = null,
    val notifications: Long = 0,
    val bytes: Long = 0,
    val lastBytesAtEpochMillis: Long? = null,
)

enum class BleCheckStage {
    CLOSED, CONNECTING, CLOSING, ERROR, REMOTE_ONLY, NO_RECEIVE_CHARACTERISTIC,
    SUBSCRIBING, WAITING_SESSION, WAITING_BYTES, BYTES_STALE,
    WAITING_MAVLINK, WAITING_HEARTBEAT, RECEIVING,
}

data class BleConnectionAssessment(
    val stage: BleCheckStage,
    val gattOpen: Boolean = false,
    val subscribed: Boolean = false,
    val freshBytes: Boolean = false,
    val freshDecodedMessage: Boolean = false,
    val freshHeartbeat: Boolean = false,
)

/** First missing receive stage, not a health verdict or a failsafe. No I/O or state changes. */
object BleConnectionCheck {
    const val FRESH_FOR_MILLIS = 5_000L

    fun assess(link: BleReceiveEvidence, diagnostics: MavlinkDiagnostics,
        vehicleConnection: VehicleConnectionState, heartbeatAtEpochMillis: Long?, nowEpochMillis: Long,
    ): BleConnectionAssessment {
        if (link.closing) return BleConnectionAssessment(BleCheckStage.CLOSING)
        if (link.hasError) return BleConnectionAssessment(BleCheckStage.ERROR)
        if (link.connecting) return BleConnectionAssessment(BleCheckStage.CONNECTING)
        if (!link.connected) return BleConnectionAssessment(BleCheckStage.CLOSED)
        if (link.subscribing) return BleConnectionAssessment(BleCheckStage.SUBSCRIBING, gattOpen = true)
        if (!link.subscribed) return BleConnectionAssessment(
            if (link.receiveOptionAvailable) BleCheckStage.REMOTE_ONLY else BleCheckStage.NO_RECEIVE_CHARACTERISTIC,
            gattOpen = true)
        val base = BleConnectionAssessment(BleCheckStage.WAITING_SESSION, gattOpen = true, subscribed = true)
        val opened = link.connectedAtEpochMillis
        val started = diagnostics.startedAtEpochMillis
        // Unlike Classic, BLE connects/discovers GATT before the user starts its decoder session.
        if (!diagnostics.active || diagnostics.linkKind != TelemetryLinkKind.BLE ||
            opened == null || started == null || opened < 0 || started < opened || started > nowEpochMillis) return base
        fun current(time: Long?) = time != null && time >= started && time <= nowEpochMillis
        fun fresh(time: Long?) = current(time) && nowEpochMillis - time!! <= FRESH_FOR_MILLIS
        if (link.notifications <= 0 || link.bytes <= 0 || diagnostics.receivedBytes <= 0 ||
            !current(link.lastBytesAtEpochMillis) || !current(diagnostics.lastBytesAtEpochMillis)) {
            return base.copy(stage = BleCheckStage.WAITING_BYTES)
        }
        if (!fresh(link.lastBytesAtEpochMillis) || !fresh(diagnostics.lastBytesAtEpochMillis)) {
            return base.copy(stage = BleCheckStage.BYTES_STALE)
        }
        val bytes = base.copy(stage = BleCheckStage.WAITING_MAVLINK, freshBytes = true)
        if (diagnostics.decodedMessages <= 0 || diagnostics.messageObservations.none {
                (it.decodedV1 > 0 || it.decodedV2 > 0) && fresh(it.lastDecodedAtEpochMillis)
            }) return bytes
        val decoded = bytes.copy(stage = BleCheckStage.WAITING_HEARTBEAT, freshDecodedMessage = true)
        val acceptedHeartbeat = diagnostics.messageObservations.any {
            it.type == ObservedMavlinkMessage.HEARTBEAT && it.accepted > 0 && fresh(it.lastAcceptedAtEpochMillis)
        }
        if (vehicleConnection != VehicleConnectionState.CONNECTED || !fresh(heartbeatAtEpochMillis) ||
            !acceptedHeartbeat) return decoded
        return decoded.copy(stage = BleCheckStage.RECEIVING, freshHeartbeat = true)
    }
}
