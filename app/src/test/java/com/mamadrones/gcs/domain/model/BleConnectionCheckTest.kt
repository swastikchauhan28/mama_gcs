package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class BleConnectionCheckTest {
    private val now = 20_000L
    private val link = BleReceiveEvidence(connected = true, receiveOptionAvailable = true, subscribed = true,
        connectedAtEpochMillis = 10_000, notifications = 2, bytes = 20, lastBytesAtEpochMillis = now)
    private val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT, decodedV2 = 1,
        accepted = 1, lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now)
    private val diagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.BLE,
        startedAtEpochMillis = 11_000, receivedBytes = 20, lastBytesAtEpochMillis = now,
        decodedMessages = 1, messageObservations = listOf(heartbeat))
    private fun assess(l: BleReceiveEvidence = link, d: MavlinkDiagnostics = diagnostics,
        status: VehicleConnectionState = VehicleConnectionState.CONNECTED, hb: Long? = now, time: Long = now,
    ) = BleConnectionCheck.assess(l, d, status, hb, time)

    @Test fun liveEvidenceRequiresAllFiveStages() {
        val result = assess()
        assertEquals(BleCheckStage.RECEIVING, result.stage)
        assertTrue(result.gattOpen && result.subscribed && result.freshBytes && result.freshDecodedMessage && result.freshHeartbeat)
    }

    @Test fun closedConnectingClosingAndErrorDiscardOldEvidence() {
        listOf(link.copy(connected = false) to BleCheckStage.CLOSED,
            link.copy(connecting = true) to BleCheckStage.CONNECTING,
            link.copy(closing = true) to BleCheckStage.CLOSING,
            link.copy(hasError = true) to BleCheckStage.ERROR).forEach { (input, stage) ->
            assertEquals(BleConnectionAssessment(stage), assess(input))
        }
    }

    @Test fun remoteOnlyDoesNotBorrowFreshTelemetry() {
        val result = assess(link.copy(subscribed = false))
        assertEquals(BleConnectionAssessment(BleCheckStage.REMOTE_ONLY, gattOpen = true), result)
        assertEquals(BleCheckStage.NO_RECEIVE_CHARACTERISTIC,
            assess(link.copy(subscribed = false, receiveOptionAvailable = false)).stage)
    }

    @Test fun pendingSubscriptionIsNotAnActiveReceiver() {
        assertEquals(BleConnectionAssessment(BleCheckStage.SUBSCRIBING, gattOpen = true),
            assess(link.copy(subscribing = true)))
    }

    @Test fun foreignAndClosedDecoderSessionsCannotSupplyEvidence() {
        TelemetryLinkKind.entries.filter { it != TelemetryLinkKind.BLE }.forEach {
            assertEquals(BleCheckStage.WAITING_SESSION, assess(d = diagnostics.copy(linkKind = it)).stage)
        }
        assertEquals(BleCheckStage.WAITING_SESSION, assess(d = diagnostics.copy(active = false)).stage)
    }

    @Test fun bleDecoderMustStartAfterGattAndBeforeNow() {
        listOf(null, -1L, 9_999L, now + 1).forEach {
            assertEquals(BleCheckStage.WAITING_SESSION, assess(d = diagnostics.copy(startedAtEpochMillis = it)).stage)
        }
        listOf(null, -1L, 11_001L, now + 1).forEach {
            assertEquals(BleCheckStage.WAITING_SESSION, assess(link.copy(connectedAtEpochMillis = it)).stage)
        }
        assertEquals(BleCheckStage.RECEIVING, assess(d = diagnostics.copy(startedAtEpochMillis = 10_000)).stage)
    }

    @Test fun bytesRequireBothTransportAndDecoderCountsAndCurrentTimestamps() {
        listOf(link.copy(notifications = 0), link.copy(bytes = 0), link.copy(bytes = -1)).forEach {
            assertEquals(BleCheckStage.WAITING_BYTES, assess(it).stage)
        }
        assertEquals(BleCheckStage.WAITING_BYTES, assess(d = diagnostics.copy(receivedBytes = 0)).stage)
        listOf(null, -1L, 10_999L, now + 1).forEach {
            assertEquals(BleCheckStage.WAITING_BYTES, assess(link.copy(lastBytesAtEpochMillis = it)).stage)
            assertEquals(BleCheckStage.WAITING_BYTES, assess(d = diagnostics.copy(lastBytesAtEpochMillis = it)).stage)
        }
    }

    @Test fun staleTransportOrDecoderBytesSuppressAllDownstreamClaims() {
        listOf(assess(link.copy(lastBytesAtEpochMillis = now - 5001)),
            assess(d = diagnostics.copy(lastBytesAtEpochMillis = now - 5001))).forEach {
            assertEquals(BleCheckStage.BYTES_STALE, it.stage)
            assertFalse(it.freshBytes || it.freshDecodedMessage || it.freshHeartbeat)
        }
    }

    @Test fun rawOrRejectedBytesAreNotDecodedMavlink() {
        val result = assess(d = diagnostics.copy(decodedMessages = 0, messageObservations = emptyList(),
            checksumFailures = 10, signedPacketsRejected = 20))
        assertEquals(BleCheckStage.WAITING_MAVLINK, result.stage)
        assertTrue(result.freshBytes)
        assertFalse(result.freshHeartbeat)
    }

    @Test fun decodedCountersAloneOrStaleObservationsAreNotRecentMessages() {
        assertEquals(BleCheckStage.WAITING_MAVLINK, assess(d = diagnostics.copy(messageObservations = emptyList())).stage)
        listOf(null, 10_999L, now - 5001, now + 1).forEach {
            assertEquals(BleCheckStage.WAITING_MAVLINK,
                assess(d = diagnostics.copy(messageObservations = listOf(heartbeat.copy(lastDecodedAtEpochMillis = it)))).stage)
        }
    }

    @Test fun foreignOrUnselectedHeartbeatDoesNotProveAutopilotLiveness() {
        val result = assess(d = diagnostics.copy(messageObservations = listOf(heartbeat.copy(accepted = 0, otherSource = 1))))
        assertEquals(BleCheckStage.WAITING_HEARTBEAT, result.stage)
        assertTrue(result.freshDecodedMessage)
        assertFalse(result.freshHeartbeat)
    }

    @Test fun heartbeatNeedsFreshAcceptedObservationAndCurrentVehicleStatus() {
        listOf(null, 10_999L, now - 5001, now + 1).forEach {
            assertEquals(BleCheckStage.WAITING_HEARTBEAT, assess(hb = it).stage)
            assertEquals(BleCheckStage.WAITING_HEARTBEAT,
                assess(d = diagnostics.copy(messageObservations = listOf(heartbeat.copy(lastAcceptedAtEpochMillis = it)))).stage)
        }
        VehicleConnectionState.entries.filter { it != VehicleConnectionState.CONNECTED }.forEach {
            assertEquals(BleCheckStage.WAITING_HEARTBEAT, assess(status = it).stage)
        }
    }

    @Test fun boundaryIsInclusiveAndTimeAloneExpiresEvidence() {
        assertEquals(BleCheckStage.RECEIVING, assess(time = now + 5000).stage)
        assertEquals(BleCheckStage.BYTES_STALE, assess(time = now + 5001).stage)
    }

    @Test fun reconnectDoesNotReuseEarlierSessionOrSamples() {
        val next = link.copy(connectedAtEpochMillis = now + 1)
        assertEquals(BleCheckStage.WAITING_SESSION, assess(next, time = now + 2).stage)
        assertEquals(BleCheckStage.WAITING_BYTES,
            assess(next, diagnostics.copy(startedAtEpochMillis = now + 1), time = now + 2).stage)
    }
}
