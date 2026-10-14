package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.data.mavlink.MavlinkMessage
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HomePositionPanelTest {
    private val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        lastHeartbeatAtEpochMillis = 100_000,
        mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 1_000),
        homePosition = HomePositionState(-35.3632621, 149.1652374, 584.0, 2_000))
    @Test fun homeIsRetainedMetadataNotAContinuouslyRefreshedMeasurement() {
        assertEquals(state.homePosition, HomePositionEvidence.visibleOnMap(state, 105_000))
        val panel = HomePositionPanel.panel(state, 105_000)
        assertEquals("REPORTED HOME · NOT VERIFIED", panel.status)
        assertEquals("-35.3632621 °", panel.rows.toMap()["Latitude"])
        assertTrue(panel.note!!.contains("103 s ago"))
    }
    @Test fun heartbeatExpiryDisconnectedAndInactiveSessionHideMapMarker() {
        assertNull(HomePositionEvidence.visibleOnMap(state, 105_001))
        listOf(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED),
            state.copy(mavlinkDiagnostics = state.mavlinkDiagnostics.copy(active = false)),
            state.copy(lastHeartbeatAtEpochMillis = null), state.copy(lastHeartbeatAtEpochMillis = 999),
            state.copy(lastHeartbeatAtEpochMillis = 105_001)).forEach {
            assertNull(HomePositionEvidence.visibleOnMap(it, 105_000))
            assertEquals("LAST OBSERVATION · LINK NOT LIVE", HomePositionPanel.panel(it, 105_000).status)
        }
    }
    @Test fun disconnectedPanelIsHistoricalWhileMarkerIsHidden() {
        val closed = state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED)
        assertEquals("LAST OBSERVATION · LINK NOT LIVE", HomePositionPanel.panel(closed, 100_000).status)
        assertEquals("149.1652374 °", HomePositionPanel.panel(closed, 100_000).rows.toMap()["Longitude"])
        assertNull(HomePositionEvidence.visibleOnMap(closed, 100_000))
    }
    @Test fun missingAndPreviousSessionOrFutureSamplesCannotAppear() {
        assertEquals("NOT RECEIVED", HomePositionPanel.panel(VehicleState(), 100_000).status)
        listOf(999L, 100_001L).forEach { time ->
            val invalid = state.copy(homePosition = state.homePosition!!.copy(receivedAtEpochMillis = time))
            assertNull(HomePositionEvidence.visibleOnMap(invalid, 100_000))
            assertTrue(HomePositionPanel.panel(invalid, 100_000).rows.all { it.second == "—" })
        }
        listOf(null, -1L, 100_001L).forEach { start ->
            assertNull(HomePositionEvidence.visibleOnMap(state.copy(mavlinkDiagnostics =
                state.mavlinkDiagnostics.copy(startedAtEpochMillis = start)), 100_000))
        }
    }
    @Test fun invalidCoordinatePairsAreRejectedIncludingNonFiniteValues() {
        listOf(null to 0.0, 91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY).forEach { (lat, lon) ->
            val invalid = state.copy(homePosition = state.homePosition!!.copy(latitude = lat, longitude = lon))
            assertNull(HomePositionEvidence.visibleOnMap(invalid, 100_000))
            assertEquals("INVALID COORDINATES · MARKER HIDDEN", HomePositionPanel.panel(invalid, 100_000).status)
        }
    }
    @Test fun repositoryScalesCoordinatesAndInvalidReplacementClearsOldPosition() {
        val repo = VehicleRepositoryImpl()
        repo.onHomePosition(MavlinkMessage.HomePosition(1, 1, -353632621, 1491652374, -12345), 2_000)
        assertEquals(HomePositionState(-35.3632621, 149.1652374, -12.345, 2_000), repo.vehicleState.value.homePosition)
        repo.onHomePosition(MavlinkMessage.HomePosition(1, 1, 910000000, 0, 5), 3_000)
        assertEquals(HomePositionState(null, null, null, 3_000), repo.vehicleState.value.homePosition)
    }
    @Test fun zeroAndCoordinateLimitsAreValidAndDoNotAlterVehiclePosition() {
        val repo = VehicleRepositoryImpl()
        listOf(0 to 0, 900000000 to 1800000000, -900000000 to -1800000000).forEach { (lat, lon) ->
            repo.onHomePosition(MavlinkMessage.HomePosition(1, 1, lat, lon, 0), 2_000)
            assertTrue(repo.vehicleState.value.homePosition!!.hasCoordinate)
            assertEquals(GlobalPositionState(), repo.vehicleState.value.position)
            assertTrue(repo.vehicleState.value.positionTrack.isEmpty())
        }
    }
}
