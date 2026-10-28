package com.mamadrones.gcs.domain.model

enum class TelemetryLinkKind { UDP, BLE, CLASSIC, OTHER }

/** Latest radio-generated frame only; raw scales are device-specific and not vehicle health. */
data class RadioStatusObservation(
    val systemId: Int, val componentId: Int,
    val rxErrors: Int, val fixedPackets: Int,
    val rssi: Int, val remoteRssi: Int, val txBufferPercent: Int,
    val noise: Int, val remoteNoise: Int,
    val receivedAtEpochMillis: Long,
)

/** Fixed-size, memory-only session observations. Not authentication, packet loss, or health. */
data class MavlinkDiagnostics(
    val active: Boolean = false,
    val linkKind: TelemetryLinkKind = TelemetryLinkKind.OTHER,
    val startedAtEpochMillis: Long? = null,
    val receivedChunks: Long = 0,
    val receivedBytes: Long = 0,
    val mavlinkV1StartBytes: Long = 0,
    val mavlinkV2StartBytes: Long = 0,
    val decodedMessages: Long = 0,
    val decodedV1Messages: Long = 0,
    val decodedV2Messages: Long = 0,
    val validatedRadioStatusFrames: Long = 0,
    val latestRadioStatus: RadioStatusObservation? = null,
    val acceptedMessages: Long = 0,
    val ignoredBeforeHeartbeat: Long = 0,
    val ignoredOtherSource: Long = 0,
    val checksumFailures: Long = 0,
    val malformedPayloads: Long = 0,
    val unsupportedMessages: Long = 0,
    val unsupportedFlags: Long = 0,
    val signedPacketsRejected: Long = 0,
    val lastUnsupportedMessageId: Int? = null,
    val lastBytesAtEpochMillis: Long? = null,
    val lastAcceptedAtEpochMillis: Long? = null,
    val messageObservations: List<MavlinkMessageObservation> = emptyList(),
    // Elapsed values are volatile current-session receipt evidence, never persisted.
    val startedAtElapsedMillis: Long? = null,
    val lastBytesAtElapsedMillis: Long? = null,
) {
    val parserErrors: Long get() = checksumFailures + malformedPayloads
}
