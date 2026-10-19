package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class ClassicConnectionCheckTest {
    private val now = 20_000L
    private val connection = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 10_000L)
    private val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT,
        decodedV2 = 1, accepted = 1, lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now)
    private val diagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
        startedAtEpochMillis = 9_000L, receivedBytes = 100, lastBytesAtEpochMillis = now,
        decodedMessages = 1, messageObservations = listOf(heartbeat))
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = now, mavlinkDiagnostics = diagnostics,
        attitude = AttitudeState(lastUpdatedAtEpochMillis = now))
    private fun assess(state: VehicleState = vehicle, link: ConnectionState = connection,
        active: Boolean = true, time: Long = now) = ClassicConnectionCheck.assess(active, link, state, time)

    @Test fun freshSessionProvidesReceiveEvidenceOnly() {
        val result = assess()
        assertEquals(ClassicCheckStage.RECEIVING, result.stage)
        assertTrue(result.socketOpen && result.freshBytes && result.freshDecodedMessage && result.freshHeartbeat)
        assertEquals(listOf("Attitude"), result.measurementMessages)
    }
    @Test fun closedAndConnectingCannotBorrowLiveVehicleData() {
        assertEquals(ClassicCheckStage.CLOSED, assess(active = false).stage)
        assertEquals(ClassicCheckStage.CONNECTING, assess(link = connection.copy(status = TransportStatus.CONNECTING)).stage)
        assertEquals(ClassicCheckStage.CLOSED, assess(link = ConnectionState()).stage)
        assertFalse(assess(active = false).freshHeartbeat)
    }
    @Test fun errorEvenAfterControllerReleasesLink() {
        assertEquals(ClassicCheckStage.ERROR, assess(active = false,
            link = connection.copy(status = TransportStatus.ERROR)).stage)
    }
    @Test fun otherLinksAndHistoricalDecoderSessionsAreNotAccepted() {
        TelemetryLinkKind.entries.filter { it != TelemetryLinkKind.CLASSIC }.forEach {
            assertEquals(ClassicCheckStage.WAITING_SESSION,
                assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(linkKind = it))).stage)
        }
        assertEquals(ClassicCheckStage.WAITING_SESSION,
            assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(active = false))).stage)
    }
    @Test fun invalidSessionBoundariesDoNotProvideEvidence() {
        listOf(null, -1L, 10_001L, Long.MAX_VALUE).forEach {
            assertEquals(ClassicCheckStage.WAITING_SESSION,
                assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(startedAtEpochMillis = it))).stage)
        }
        listOf(null, -1L, 8_999L, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_SESSION, assess(link = connection.copy(connectedAtEpochMillis = it)).stage)
        }
    }
    @Test fun byteCountAndCurrentReceiptAreBothRequired() {
        listOf(null, -1L, 9_999L, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_BYTES,
                assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(lastBytesAtEpochMillis = it))).stage)
        }
        assertEquals(ClassicCheckStage.WAITING_BYTES,
            assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(receivedBytes = 0))).stage)
    }
    @Test fun staleBytesDoNotLeaveFreshHeartbeatClaim() {
        val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(lastBytesAtEpochMillis = now - 5_001)))
        assertEquals(ClassicCheckStage.BYTES_STALE, result.stage)
        assertFalse(result.freshHeartbeat)
        assertTrue(result.measurementMessages.isEmpty())
    }
    @Test fun freshBytesDoNotSubstituteForHeartbeat() {
        listOf(null, -1L, 9_999L, now - 5_001, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_HEARTBEAT, assess(vehicle.copy(lastHeartbeatAtEpochMillis = it)).stage)
        }
        VehicleConnectionState.entries.filter { it != VehicleConnectionState.CONNECTED }.forEach {
            assertEquals(ClassicCheckStage.WAITING_HEARTBEAT, assess(vehicle.copy(connectionStatus = it)).stage)
        }
    }
    @Test fun serialBytesWithoutValidatedMavlinkHaveTheirOwnStage() {
        val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
            decodedMessages = 0, messageObservations = emptyList(), checksumFailures = 419,
            unsupportedMessages = 643)))
        assertEquals(ClassicCheckStage.WAITING_MAVLINK, result.stage)
        assertTrue(result.socketOpen && result.freshBytes)
        assertFalse(result.freshDecodedMessage || result.freshHeartbeat)
    }
    @Test fun staleOrOutOfSessionDecodeDoesNotBecomeCurrentMavlinkEvidence() {
        listOf(null, 9_999L, now - 5_001, now + 1).forEach { time ->
            val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
                messageObservations = listOf(heartbeat.copy(lastDecodedAtEpochMillis = time)))))
            assertEquals(ClassicCheckStage.WAITING_MAVLINK, result.stage)
        }
        assertEquals(ClassicCheckStage.WAITING_MAVLINK,
            assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(messageObservations = emptyList()))).stage)
    }
    @Test fun decodedMessageFromAnotherSourceDoesNotProveAutopilotHeartbeat() {
        val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
            messageObservations = listOf(heartbeat.copy(accepted = 0, otherSource = 1)))))
        assertEquals(ClassicCheckStage.WAITING_HEARTBEAT, result.stage)
        assertTrue(result.freshDecodedMessage)
        assertFalse(result.freshHeartbeat)
    }
    @Test fun heartbeatAndAcceptedCounterAreNotMeasurements() {
        assertEquals(ClassicCheckStage.WAITING_MEASUREMENTS, assess(vehicle.copy(attitude = AttitudeState(),
            mavlinkDiagnostics = diagnostics.copy(acceptedMessages = 50))).stage)
    }
    @Test fun measurementsExpireIndependentlyAndRespectSessionBoundary() {
        listOf(null, -1L, 9_999L, now - 5_001, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_MEASUREMENTS,
                assess(vehicle.copy(attitude = AttitudeState(lastUpdatedAtEpochMillis = it))).stage)
        }
    }
    @Test fun fiveSecondBoundaryAndTimeAloneExpiry() {
        val sample = vehicle.copy(lastHeartbeatAtEpochMillis = now - 5_000,
            attitude = AttitudeState(lastUpdatedAtEpochMillis = now - 5_000),
            mavlinkDiagnostics = diagnostics.copy(lastBytesAtEpochMillis = now - 5_000))
        assertEquals(ClassicCheckStage.RECEIVING, assess(sample).stage)
        assertEquals(ClassicCheckStage.BYTES_STALE, assess(sample, time = now + 1).stage)
    }
    @Test fun reconnectCannotReuseSamplesBeforeNewSocketOpened() {
        assertEquals(ClassicCheckStage.WAITING_BYTES,
            assess(link = connection.copy(connectedAtEpochMillis = now + 1), time = now + 2).stage)
    }
    @Test fun listsOnlyFreshGroupsAndDoesNotClaimValidSensorValues() {
        val result = assess(vehicle.copy(gps = GpsState(fix = GpsFix.NO_FIX, lastUpdatedAtEpochMillis = now),
            position = GlobalPositionState(lastUpdatedAtEpochMillis = now),
            battery = BatteryState(lastUpdatedAtEpochMillis = now),
            roverHud = RoverHudState(lastUpdatedAtEpochMillis = now),
            systemStatus = AutopilotSystemStatus(lastUpdatedAtEpochMillis = now),
            rcInput = RcInputState(lastUpdatedAtEpochMillis = now)))
        assertEquals(listOf("GPS", "Position", "Attitude", "Battery", "Rover HUD", "System status", "RC input"),
            result.measurementMessages)
    }
}
