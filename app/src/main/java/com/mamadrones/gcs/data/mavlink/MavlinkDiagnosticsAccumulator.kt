package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind

enum class MessageDisposition { ACCEPTED, BEFORE_AUTOPILOT_HEARTBEAT, OTHER_SOURCE }

/** Owned by one receive coroutine. No packet contents, unbounded history, or device identifiers. */
class MavlinkDiagnosticsAccumulator(kind: TelemetryLinkKind, startedAt: Long) {
    var snapshot = MavlinkDiagnostics(active = true, linkKind = kind, startedAtEpochMillis = startedAt)
        private set

    fun recordBytes(count: Int, receivedAt: Long) {
        require(count >= 0)
        if (count == 0) return
        snapshot = snapshot.copy(receivedChunks = snapshot.receivedChunks + 1,
            receivedBytes = snapshot.receivedBytes + count, lastBytesAtEpochMillis = receivedAt)
    }

    fun recordResult(result: MavlinkParseResult, disposition: MessageDisposition?, receivedAt: Long) {
        val current = snapshot
        snapshot = when (result) {
            is MavlinkParseResult.Message -> {
                val decoded = current.copy(decodedMessages = current.decodedMessages + 1)
                when (requireNotNull(disposition)) {
                    MessageDisposition.ACCEPTED -> decoded.copy(acceptedMessages = decoded.acceptedMessages + 1,
                        lastAcceptedAtEpochMillis = receivedAt)
                    MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT -> decoded.copy(ignoredBeforeHeartbeat = decoded.ignoredBeforeHeartbeat + 1)
                    MessageDisposition.OTHER_SOURCE -> decoded.copy(ignoredOtherSource = decoded.ignoredOtherSource + 1)
                }
            }
            is MavlinkParseResult.InvalidChecksum -> current.copy(checksumFailures = current.checksumFailures + 1)
            is MavlinkParseResult.MalformedMessage -> current.copy(malformedPayloads = current.malformedPayloads + 1)
            is MavlinkParseResult.UnsupportedMessage -> current.copy(unsupportedMessages = current.unsupportedMessages + 1,
                lastUnsupportedMessageId = result.messageId)
            is MavlinkParseResult.UnsupportedIncompatibilityFlags -> current.copy(unsupportedFlags = current.unsupportedFlags + 1)
            MavlinkParseResult.SignedPacketRejected -> current.copy(signedPacketsRejected = current.signedPacketsRejected + 1)
        }
    }
}
