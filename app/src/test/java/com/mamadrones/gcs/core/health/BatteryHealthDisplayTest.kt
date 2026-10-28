package com.mamadrones.gcs.core.health

import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import org.junit.Assert.*
import org.junit.Test

class BatteryHealthDisplayTest {
    private val summary = BatteryState(lastUpdatedAtEpochMillis = 1, lastUpdatedAtElapsedMillis = 19_000)
    private val oldIssue = BatteryState(batteryId = 1, chargeState = 3, lastUpdatedAtEpochMillis = 1, lastUpdatedAtElapsedMillis = 10_000)
    private val pack2 = BatteryState(batteryId = 2, chargeState = 1, lastUpdatedAtEpochMillis = 1, lastUpdatedAtElapsedMillis = 19_000)
    private fun vehicle() = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
        battery = summary, batteries = listOf(oldIssue, pack2))
    private fun rows(state: VehicleState, now: Long = 20_001) = ConsolePanels.health(state, now).rows.toMap()

    @Test fun staleIssueRemainsHistoricalWhileOtherSourcesAreFresh() {
        val state = vehicle()
        val rows = rows(state)
        assertEquals("REPORTED ISSUE", rows["Battery evidence (latest retained)"])
        assertEquals("STALE", rows["Battery pack 1 freshness"])
        assertEquals("FRESH", rows["Battery pack 2 freshness"])
        assertEquals("FRESH", rows["System battery summary freshness"])
        assertEquals("NOT ASSESSED", ConsolePanels.health(state, 20_001).status)
    }

    @Test fun disconnectedEvidenceIsRetainedButFreshnessUnavailable() {
        val state = vehicle().copy(connectionStatus = VehicleConnectionState.DISCONNECTED)
        val rows = rows(state)
        assertEquals("REPORTED ISSUE", rows["Battery evidence (latest retained)"])
        assertEquals("UNAVAILABLE", rows["Battery pack 1 freshness"])
        assertEquals("UNAVAILABLE", rows["Battery pack 2 freshness"])
        assertEquals("UNAVAILABLE", rows["System battery summary freshness"])
        assertEquals("NOT RECEIVED", rows["MAVLink heartbeat"])
    }

    @Test fun newerSampleReplacesOldIssueAndFreshnessRecovers() {
        val state = vehicle().copy(batteries = listOf(oldIssue.copy(chargeState = 1, lastUpdatedAtElapsedMillis = 20_001), pack2))
        assertEquals("REPORTED", rows(state)["Battery evidence (latest retained)"])
        assertEquals("FRESH", rows(state)["Battery pack 1 freshness"])
    }

    @Test fun missingFutureAndNegativeTimesFailClosedAndWallClockDoesNotDriveFreshness() {
        for (time in listOf(null, -1L, 20_002L)) {
            assertEquals("UNAVAILABLE", rows(vehicle().copy(battery = summary.copy(lastUpdatedAtElapsedMillis = time)))["System battery summary freshness"])
        }
        for (epoch in listOf(1L, Long.MAX_VALUE)) {
            assertEquals("STALE", rows(vehicle().copy(batteries = listOf(oldIssue.copy(lastUpdatedAtEpochMillis = epoch))))["Battery pack 1 freshness"])
        }
        val none = rows(VehicleState())
        assertEquals("NOT RECEIVED", none["Battery evidence (latest retained)"])
        assertEquals("UNAVAILABLE", none["System battery summary freshness"])
    }
}
