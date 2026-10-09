package com.mamadrones.gcs.domain.model

enum class TelemetryLinkKind { UDP, BLE, CLASSIC, OTHER }

/** Fixed-size, memory-only session observations. Not authentication, packet loss, or health. */
data class MavlinkDiagnostics(
    val active: Boolean = false,
    val linkKind: TelemetryLinkKind = TelemetryLinkKind.OTHER,
    val startedAtEpochMillis: Long? = null,
    val receivedChunks: Long = 0,
    val receivedBytes: Long = 0,
    val decodedMessages: Long = 0,
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
) {
    val parserErrors: Long get() = checksumFailures + malformedPayloads
}
