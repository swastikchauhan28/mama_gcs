package com.mamadrones.gcs.domain.model

import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.data.mavlink.*
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import org.junit.Assert.*
import org.junit.Test

class BatteryEvidenceTest {
    private val pack1 = BatteryState(percentage = 80, batteryId = 1, lastUpdatedAtEpochMillis = 20_000L, lastUpdatedAtElapsedMillis = 10_000L)
    private val pack2 = BatteryState(percentage = 65, batteryId = 2, lastUpdatedAtEpochMillis = 20_000L, lastUpdatedAtElapsedMillis = 19_000L)
    private val summary = BatteryState(percentage = 70, lastUpdatedAtEpochMillis = 20_000L, lastUpdatedAtElapsedMillis = 19_000L)
    private fun vehicle() = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED, battery = summary, batteries = listOf(pack1, pack2))

    @Test fun sourcesExpireAndRecoverIndependently() {
        val state = vehicle()
        assertEquals(BatteryFreshness.FRESH, BatteryEvidence.freshness(pack1, true, 20_000L))
        assertEquals(BatteryFreshness.STALE, BatteryEvidence.freshness(pack1, true, 20_001L))
        assertEquals(BatteryFreshness.FRESH, BatteryEvidence.freshness(pack2, true, 20_001L))
        val rows = ConsolePanels.battery(state, 20_001L).rows.toMap()
        assertEquals("STALE", rows["Pack 1 (BATTERY_STATUS) freshness"])
        assertEquals("UNKNOWN", rows["Pack 1 (BATTERY_STATUS) remaining"])
        assertEquals("65 %", rows["Pack 2 (BATTERY_STATUS) remaining"])
        assertEquals("70 %", rows["System summary (SYS_STATUS) remaining"])
        val recovered = state.copy(batteries = listOf(pack1.copy(lastUpdatedAtElapsedMillis = 20_001L), pack2))
        assertEquals("80 %", ConsolePanels.battery(recovered, 20_001L).rows.toMap()["Pack 1 (BATTERY_STATUS) remaining"])
        assertEquals("STALE", ConsolePanels.battery(recovered, 29_001L).rows.toMap()["System summary (SYS_STATUS) freshness"])
    }

    @Test fun routerCarriesIndependentElapsedTimesForBothSources() {
        val repository = VehicleRepositoryImpl()
        val router = MavlinkMessageRouter(repository)
        router.route(MavlinkParseResult.Message(MavlinkMessage.SystemStatus(1, 1, 7, 3, 1, 765, 24_600, -1, 70, 250, 4)), 30_000L, 1_000L)
        router.route(MavlinkParseResult.Message(MavlinkMessage.BatteryStatus(1, 1, 1, 2_500, listOf(12_000), 325, 81)), 1L, 2_000L)
        assertEquals(1_000L, repository.vehicleState.value.battery.lastUpdatedAtElapsedMillis)
        assertEquals(2_000L, repository.vehicleState.value.batteries.single().lastUpdatedAtElapsedMillis)
    }

    @Test fun calendarClockDoesNotChangeFreshness() {
        for (epoch in listOf(1L, Long.MAX_VALUE)) {
            val sample = pack1.copy(lastUpdatedAtEpochMillis = epoch)
            assertEquals(BatteryFreshness.FRESH, BatteryEvidence.freshness(sample, true, 20_000L))
            assertEquals(BatteryFreshness.STALE, BatteryEvidence.freshness(sample, true, 20_001L))
        }
    }

    @Test fun missingInvalidFutureAndDisconnectedAreUnavailable() {
        for (received in listOf(null, -1L, 20_001L)) {
            assertEquals(BatteryFreshness.UNAVAILABLE, BatteryEvidence.freshness(pack1.copy(lastUpdatedAtElapsedMillis = received), true, 20_000L))
        }
        assertEquals(BatteryFreshness.UNAVAILABLE, BatteryEvidence.freshness(pack1, false, 20_000L))
        assertEquals(BatteryFreshness.UNAVAILABLE, BatteryEvidence.freshness(pack1, true, -1L))
        val rows = ConsolePanels.battery(vehicle().copy(connectionStatus = VehicleConnectionState.DISCONNECTED), 20_000L).rows.toMap()
        assertEquals("UNKNOWN", rows["System summary (SYS_STATUS) remaining"])
    }
}
