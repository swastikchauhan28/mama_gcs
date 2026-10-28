package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class ClassicConnectionCheckTest {
    private val now = 20_000L
    private val connection = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 10_000L, connectedAtElapsedMillis = 10_000L)
    private val heartbeat = MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT,
        decodedV2 = 1, accepted = 1, lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now,
        lastDecodedAtElapsedMillis = now, lastAcceptedAtElapsedMillis = now)
    private val rcObservation = MavlinkMessageObservation(ObservedMavlinkMessage.RC_CHANNELS,
        decodedV2 = 1, accepted = 1, lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now,
        lastDecodedAtElapsedMillis = now, lastAcceptedAtElapsedMillis = now)
    private val diagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
        startedAtEpochMillis = 9_000L, startedAtElapsedMillis = 9_000L, receivedBytes = 100, lastBytesAtEpochMillis = now, lastBytesAtElapsedMillis = now,
        decodedMessages = 1, messageObservations = listOf(heartbeat))
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = now, lastHeartbeatAtElapsedMillis = now, mavlinkDiagnostics = diagnostics,
        attitude = AttitudeState(lastUpdatedAtEpochMillis = now))
    private fun assess(state: VehicleState = vehicle, link: ConnectionState = connection,
        active: Boolean = true, time: Long = now) = ClassicConnectionCheck.assess(active, link, state, time, time)

    @Test fun freshSessionProvidesReceiveEvidenceOnly() {
        val result = assess()
        assertEquals(ClassicCheckStage.RECEIVING, result.stage)
        assertTrue(result.socketOpen && result.freshBytes && result.freshDecodedMessage && result.freshHeartbeat)
        assertEquals(listOf("Attitude"), result.measurementMessages)
        assertFalse(result.freshRcChannels)
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
                assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(startedAtElapsedMillis = it))).stage)
        }
        listOf(null, -1L, 8_999L, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_SESSION, assess(link = connection.copy(connectedAtElapsedMillis = it)).stage)
        }
    }
    @Test fun byteCountAndCurrentReceiptAreBothRequired() {
        listOf(null, -1L, 9_999L, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_BYTES,
                assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(lastBytesAtElapsedMillis = it))).stage)
        }
        assertEquals(ClassicCheckStage.WAITING_BYTES,
            assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(receivedBytes = 0))).stage)
    }
    @Test fun staleBytesDoNotLeaveFreshHeartbeatClaim() {
        val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(lastBytesAtElapsedMillis = now - 5_001)))
        assertEquals(ClassicCheckStage.BYTES_STALE, result.stage)
        assertFalse(result.freshHeartbeat)
        assertTrue(result.measurementMessages.isEmpty())
    }
    @Test fun freshBytesDoNotSubstituteForHeartbeat() {
        listOf(null, -1L, 9_999L, now - 5_001, now + 1).forEach {
            assertEquals(ClassicCheckStage.WAITING_HEARTBEAT, assess(vehicle.copy(lastHeartbeatAtElapsedMillis = it)).stage)
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
        assertEquals(ClassicStreamDiagnosis.REJECTED_FRAME_CANDIDATES, result.streamDiagnosis)
    }
    @Test fun undecodedClassicCountersUseOnlyBoundedEvidencePatterns() {
        fun diagnosisFor(diagnostics: MavlinkDiagnostics): ClassicStreamDiagnosis? = assess(
            vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
                decodedMessages = 0, messageObservations = emptyList()))).streamDiagnosis

        assertEquals(ClassicStreamDiagnosis.NO_MAVLINK_START_BYTES,
            diagnosisFor(diagnostics.copy(mavlinkV1StartBytes = 0, mavlinkV2StartBytes = 0)))
        assertEquals(ClassicStreamDiagnosis.REJECTED_FRAME_CANDIDATES,
            diagnosisFor(diagnostics.copy(mavlinkV1StartBytes = 2, checksumFailures = 1,
                unsupportedMessages = 9)))
        assertEquals(ClassicStreamDiagnosis.UNVERIFIED_UNSUPPORTED_CANDIDATES,
            diagnosisFor(diagnostics.copy(mavlinkV2StartBytes = 2, unsupportedMessages = 1)))
        assertEquals(ClassicStreamDiagnosis.NO_VALIDATED_SUPPORTED_FRAME,
            diagnosisFor(diagnostics.copy(mavlinkV1StartBytes = 2)))
        val radioOnly = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
            decodedMessages = 0, messageObservations = emptyList(), validatedRadioStatusFrames = 1,
            checksumFailures = 7)))
        assertEquals(ClassicCheckStage.WAITING_MAVLINK, radioOnly.stage)
        assertEquals(ClassicStreamDiagnosis.VALIDATED_RADIO_ONLY, radioOnly.streamDiagnosis)
        assertFalse(radioOnly.freshDecodedMessage || radioOnly.freshHeartbeat || radioOnly.freshRcChannels)
    }
    @Test fun staleOrOutOfSessionDecodeDoesNotBecomeCurrentMavlinkEvidence() {
        listOf(null, 9_999L, now - 5_001, now + 1).forEach { time ->
            val result = assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
                messageObservations = listOf(heartbeat.copy(lastDecodedAtElapsedMillis = time)))))
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
        val sample = vehicle.copy(lastHeartbeatAtElapsedMillis = now - 5_000,
            attitude = AttitudeState(lastUpdatedAtEpochMillis = now - 5_000),
            mavlinkDiagnostics = diagnostics.copy(lastBytesAtElapsedMillis = now - 5_000))
        assertEquals(ClassicCheckStage.RECEIVING, assess(sample).stage)
        assertEquals(ClassicCheckStage.BYTES_STALE, assess(sample, time = now + 1).stage)
    }
    @Test fun reconnectCannotReuseSamplesBeforeNewSocketOpened() {
        assertEquals(ClassicCheckStage.WAITING_BYTES,
            assess(link = connection.copy(connectedAtElapsedMillis = now + 1), time = now + 2).stage)
    }
    @Test fun listsOnlyFreshGroupsAndDoesNotClaimValidSensorValues() {
        val result = assess(vehicle.copy(gps = GpsState(fix = GpsFix.NO_FIX, lastUpdatedAtEpochMillis = now),
            position = GlobalPositionState(lastUpdatedAtEpochMillis = now),
            battery = BatteryState(lastUpdatedAtEpochMillis = now),
            roverHud = RoverHudState(lastUpdatedAtEpochMillis = now),
            systemStatus = AutopilotSystemStatus(lastUpdatedAtEpochMillis = now),
            rcInput = RcInputState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = now)))
        assertEquals(listOf("GPS", "Position", "Attitude", "Battery", "Rover HUD", "System status"),
            result.measurementMessages)
        assertFalse(result.freshRcChannels) // A state timestamp alone is not selected-source evidence.
    }
    @Test fun rcReceiptRequiresFreshAcceptedObservationAndStateInCurrentClassicSession() {
        val current = vehicle.copy(rcInput = RcInputState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = now),
            mavlinkDiagnostics = diagnostics.copy(messageObservations = listOf(heartbeat, rcObservation)))
        assertTrue(assess(current).freshRcChannels)
        assertFalse(assess(current.copy(mavlinkDiagnostics = diagnostics.copy(
            messageObservations = listOf(heartbeat, rcObservation.copy(accepted = 0, otherSource = 1))))).freshRcChannels)
        listOf(null, 9_999L, now - 5_001, now + 1).forEach { time ->
            assertFalse(assess(current.copy(rcInput = RcInputState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = time))).freshRcChannels)
            assertFalse(assess(current.copy(mavlinkDiagnostics = diagnostics.copy(
                messageObservations = listOf(heartbeat, rcObservation.copy(lastAcceptedAtElapsedMillis = time))))).freshRcChannels)
        }
        assertFalse(assess(current.copy(lastHeartbeatAtElapsedMillis = now - 5_001)).freshRcChannels)
        assertFalse(assess(current, active = false).freshRcChannels)
        assertFalse(assess(current, link = connection.copy(connectedAtElapsedMillis = now + 1), time = now + 2).freshRcChannels)
    }
    @Test fun rcFreshnessUsesElapsedEvenWhenCalendarClockJumps() {
        val current = vehicle.copy(attitude = AttitudeState(),
            rcInput = RcInputState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = now),
            mavlinkDiagnostics = diagnostics.copy(messageObservations = listOf(heartbeat, rcObservation)))
        for (epoch in listOf(1L, Long.MAX_VALUE)) {
            val result = ClassicConnectionCheck.assess(true, connection, current, epoch, now)
            assertTrue(result.freshRcChannels)
            assertEquals(listOf("RC input"), result.measurementMessages)
            assertEquals(ClassicCheckStage.RECEIVING, result.stage)
        }
    }
    @Test fun rcRequiresBothElapsedReceiptsAndRecoversAfterExpiry() {
        val current = vehicle.copy(rcInput = RcInputState(lastUpdatedAtEpochMillis = now, lastUpdatedAtElapsedMillis = now),
            mavlinkDiagnostics = diagnostics.copy(messageObservations = listOf(heartbeat, rcObservation)))
        for (time in listOf(null, -1L, 9_999L, now - 5_001, now + 1)) {
            val missingSample = current.copy(rcInput = current.rcInput.copy(lastUpdatedAtElapsedMillis = time))
            assertFalse(assess(missingSample).freshRcChannels)
            assertFalse(assess(missingSample).measurementMessages.contains("RC input"))
            val missingAcceptance = current.copy(mavlinkDiagnostics = diagnostics.copy(
                messageObservations = listOf(heartbeat, rcObservation.copy(lastAcceptedAtElapsedMillis = time))))
            assertFalse(assess(missingAcceptance).freshRcChannels)
            assertFalse(assess(missingAcceptance).measurementMessages.contains("RC input"))
        }
        val boundary = current.copy(rcInput = current.rcInput.copy(lastUpdatedAtElapsedMillis = now - 5_000),
            mavlinkDiagnostics = diagnostics.copy(messageObservations = listOf(heartbeat,
                rcObservation.copy(lastAcceptedAtElapsedMillis = now - 5_000))))
        assertTrue(assess(boundary).freshRcChannels)
        assertFalse(assess(boundary, time = now + 1).freshRcChannels)
        assertTrue(assess(current).freshRcChannels)
        assertTrue(assess(current).measurementMessages.contains("RC input"))
        assertFalse(assess(current.copy(connectionStatus = VehicleConnectionState.DISCONNECTED)).freshRcChannels)
    }

    @Test fun calendarClockJumpsDoNotChangeByteDecodeOrHeartbeatEvidence() {
        for (epoch in listOf(1L, Long.MAX_VALUE)) {
            val result = ClassicConnectionCheck.assess(true, connection, vehicle, epoch, now)
            assertTrue(result.freshBytes)
            assertTrue(result.freshDecodedMessage)
            assertTrue(result.freshHeartbeat)
            assertEquals(ClassicCheckStage.WAITING_MEASUREMENTS, result.stage)
        }
    }
    @Test fun missingElapsedNeverFallsBackToCalendarAndNewReceiptRecovers() {
        assertEquals(ClassicCheckStage.WAITING_SESSION, ClassicConnectionCheck.assess(true, connection, vehicle, now, null).stage)
        assertEquals(ClassicCheckStage.WAITING_HEARTBEAT,
            assess(vehicle.copy(lastHeartbeatAtElapsedMillis = null)).stage)
        assertEquals(ClassicCheckStage.WAITING_MAVLINK, assess(vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
            messageObservations = listOf(heartbeat.copy(lastDecodedAtElapsedMillis = null))))).stage)
        val stale = vehicle.copy(mavlinkDiagnostics = diagnostics.copy(lastBytesAtElapsedMillis = now - 5_001))
        assertEquals(ClassicCheckStage.BYTES_STALE, assess(stale).stage)
        assertEquals(ClassicCheckStage.RECEIVING, assess(stale.copy(mavlinkDiagnostics = diagnostics)).stage)
    }
    @Test fun heartbeatRequiresCurrentAcceptedElapsedObservation() {
        for (time in listOf(null, -1L, 9_999L, now - 5_001, now + 1)) {
            val state = vehicle.copy(mavlinkDiagnostics = diagnostics.copy(
                messageObservations = listOf(heartbeat.copy(lastAcceptedAtElapsedMillis = time))))
            assertEquals(ClassicCheckStage.WAITING_HEARTBEAT, assess(state).stage)
        }
    }

}
