package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassicLinkEvidenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val connection = ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 1,
        packetStatistics = PacketStatistics(receivedPackets = 4))
    private val vehicle = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 10, attitude = AttitudeState(lastUpdatedAtEpochMillis = 10),
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, linkKind = TelemetryLinkKind.CLASSIC,
            startedAtEpochMillis = 1, receivedBytes = 10, lastBytesAtEpochMillis = 10))

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun captureRequiresOpenClassicLinkWithoutCompetitorAndDoesNotExposeIdentity() = runTest(dispatcher) {
        val model = ClassicLinkEvidenceViewModel(dispatcher)
        listOf(false to connection, true to connection.copy(status = TransportStatus.CONNECTING)).forEach { (active, link) ->
            model.capture(active, false, link, vehicle, 10)
            assertNull(model.state.value.snapshot)
        }
        model.capture(true, true, connection, vehicle, 10)
        assertNull(model.state.value.snapshot)
        model.capture(true, false, connection, vehicle.copy(displayName = "private rover", vehicleId = "secret"), 10)
        assertNotNull(model.state.value.snapshot)
        assertFalse(model.state.value.snapshot!!.contains("private"))
        assertFalse(model.state.value.snapshot!!.contains("secret"))
    }

    @Test fun reviewAndPickerRequireAllTelemetryLinksClosed() = runTest(dispatcher) {
        val model = ClassicLinkEvidenceViewModel(dispatcher)
        model.capture(true, false, connection, vehicle, 10)
        model.review(true, false)
        model.review(false, true)
        assertFalse(model.state.value.reviewing)
        model.review(false, false)
        assertTrue(model.state.value.reviewing)
        assertFalse(model.chooseDestination(true, false))
        assertFalse(model.chooseDestination(false, true))
        assertTrue(model.chooseDestination(false, false))
    }

    @Test fun frozenCaptureSurvivesDisconnectCloseDiscardAndSuccessfulWriteClearsIt() = runTest(dispatcher) {
        val model = ClassicLinkEvidenceViewModel(dispatcher)
        model.capture(true, false, connection, vehicle, 10)
        val frozen = model.state.value.snapshot!!
        model.review(false, false)
        model.closePreview()
        assertEquals(frozen, model.state.value.snapshot)
        model.review(false, false)
        assertTrue(model.chooseDestination(false, false))
        var writes = 0
        val output = ByteArrayOutputStream()
        model.save { writes++; output }
        model.save { writes++; output }
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(frozen, output.toString("UTF-8"))
        assertNull(model.state.value.snapshot)
    }

    @Test fun cancellationFailuresAndBusyStatesKeepSnapshotForRetry() = runTest(dispatcher) {
        val model = ClassicLinkEvidenceViewModel(dispatcher)
        model.capture(true, false, connection, vehicle, 10)
        val frozen = model.state.value.snapshot
        model.review(false, false)
        assertTrue(model.chooseDestination(false, false))
        model.pickerCancelled()
        assertEquals(frozen, model.state.value.snapshot)
        assertFalse(model.state.value.choosingDestination)
        assertTrue(model.chooseDestination(false, false))
        model.save { throw IOException("private provider path") }
        advanceUntilIdle()
        assertEquals(frozen, model.state.value.snapshot)
        assertTrue(model.state.value.message!!.contains("partial file"))
        assertFalse(model.state.value.message!!.contains("private"))
        model.discard()
        assertNull(model.state.value.snapshot)
    }
}
