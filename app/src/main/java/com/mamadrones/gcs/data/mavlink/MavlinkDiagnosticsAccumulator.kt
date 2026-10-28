package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.MavlinkMessageObservation
import com.mamadrones.gcs.domain.model.ObservedMavlinkMessage
import com.mamadrones.gcs.domain.model.RadioStatusObservation

enum class MessageDisposition { ACCEPTED, BEFORE_AUTOPILOT_HEARTBEAT, OTHER_SOURCE }

/** Owned by one receive coroutine. No packet contents, unbounded history, or device identifiers. */
class MavlinkDiagnosticsAccumulator(kind: TelemetryLinkKind, startedAt: Long, startedAtElapsed: Long? = null) {
    var snapshot = MavlinkDiagnostics(active = true, linkKind = kind, startedAtEpochMillis = startedAt, startedAtElapsedMillis = startedAtElapsed)
        private set

    fun recordBytes(bytes: ByteArray, receivedAt: Long, receivedAtElapsed: Long? = null) {
        if (bytes.isEmpty()) return
        val v1Starts = bytes.count { it == 0xFE.toByte() }
        val v2Starts = bytes.count { it == 0xFD.toByte() }
        snapshot = snapshot.copy(receivedChunks = snapshot.receivedChunks + 1,
            receivedBytes = snapshot.receivedBytes + bytes.size,
            mavlinkV1StartBytes = snapshot.mavlinkV1StartBytes + v1Starts,
            mavlinkV2StartBytes = snapshot.mavlinkV2StartBytes + v2Starts,
            lastBytesAtEpochMillis = receivedAt, lastBytesAtElapsedMillis = receivedAtElapsed)
    }

    fun recordResult(result: MavlinkParseResult, disposition: MessageDisposition?, receivedAt: Long, receivedAtElapsed: Long? = null) {
        val current = snapshot
        snapshot = when (result) {
            is MavlinkParseResult.RadioStatus -> current.copy(
                validatedRadioStatusFrames = current.validatedRadioStatusFrames + 1,
                latestRadioStatus = RadioStatusObservation(
                    result.systemId, result.componentId, result.rxErrors, result.fixedPackets,
                    result.rssi, result.remoteRssi, result.txBufferPercent, result.noise,
                    result.remoteNoise, receivedAt,
                ),
            )
            is MavlinkParseResult.Message -> {
                val admission = requireNotNull(disposition)
                val type = result.message.observedType()
                val previous = current.messageObservations.firstOrNull { it.type == type }
                    ?: MavlinkMessageObservation(type)
                val observation = previous.copy(
                    decodedV1 = previous.decodedV1 + if (result.wireVersion == MavlinkWireVersion.V1) 1 else 0,
                    decodedV2 = previous.decodedV2 + if (result.wireVersion == MavlinkWireVersion.V2) 1 else 0,
                    accepted = previous.accepted + if (admission == MessageDisposition.ACCEPTED) 1 else 0,
                    beforeHeartbeat = previous.beforeHeartbeat + if (admission == MessageDisposition.BEFORE_AUTOPILOT_HEARTBEAT) 1 else 0,
                    otherSource = previous.otherSource + if (admission == MessageDisposition.OTHER_SOURCE) 1 else 0,
                    lastDecodedAtEpochMillis = receivedAt,
                    lastDecodedAtElapsedMillis = receivedAtElapsed,
                    lastAcceptedAtElapsedMillis = if (admission == MessageDisposition.ACCEPTED) receivedAtElapsed else previous.lastAcceptedAtElapsedMillis,
                    lastAcceptedAtEpochMillis = if (admission == MessageDisposition.ACCEPTED) receivedAt else previous.lastAcceptedAtEpochMillis,
                )
                val decoded = current.copy(decodedMessages = current.decodedMessages + 1,
                    decodedV1Messages = current.decodedV1Messages + if (result.wireVersion == MavlinkWireVersion.V1) 1 else 0,
                    decodedV2Messages = current.decodedV2Messages + if (result.wireVersion == MavlinkWireVersion.V2) 1 else 0,
                    messageObservations = (current.messageObservations.filterNot { it.type == type } + observation)
                        .sortedBy { it.type.messageId })
                when (admission) {
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

/** Exhaustive mapping keeps a newly decoded type from silently disappearing from inspection. */
private fun MavlinkMessage.observedType(): ObservedMavlinkMessage = when (this) {
    is MavlinkMessage.Heartbeat -> ObservedMavlinkMessage.HEARTBEAT
    is MavlinkMessage.SystemStatus -> ObservedMavlinkMessage.SYS_STATUS
    is MavlinkMessage.GpsRawInt -> ObservedMavlinkMessage.GPS_RAW_INT
    is MavlinkMessage.Attitude -> ObservedMavlinkMessage.ATTITUDE
    is MavlinkMessage.GlobalPositionInt -> ObservedMavlinkMessage.GLOBAL_POSITION_INT
    is MavlinkMessage.ServoOutputRaw -> ObservedMavlinkMessage.SERVO_OUTPUT_RAW
    is MavlinkMessage.RcChannels -> ObservedMavlinkMessage.RC_CHANNELS
    is MavlinkMessage.VfrHud -> ObservedMavlinkMessage.VFR_HUD
    is MavlinkMessage.BatteryStatus -> ObservedMavlinkMessage.BATTERY_STATUS
    is MavlinkMessage.AutopilotVersion -> ObservedMavlinkMessage.AUTOPILOT_VERSION
    is MavlinkMessage.HomePosition -> ObservedMavlinkMessage.HOME_POSITION
    is MavlinkMessage.StatusText -> ObservedMavlinkMessage.STATUSTEXT
}
