package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.VehicleStatusText
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutopilotWarningTest {
    @Test fun `current session shows latest warning despite newer routine messages`() {
        val warning = VehicleStatusText(2, "PreArm: Compass 1 not calibrated", 1_000L)
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(6, "Ready", 2_000L), warning,
                VehicleStatusText(4, "Earlier warning", 500L)))

        assertEquals(warning, latestAutopilotWarning(state))
        assertEquals("CRITICAL", severityName(warning.severity))
        assertEquals("1s ago", autopilotWarningAge(warning, 2_000L))
        assertNull(latestAutopilotWarning(state.copy(connectionStatus = VehicleConnectionState.DEGRADED)))
        assertNull(latestAutopilotWarning(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED)))
        assertNull(latestAutopilotWarning(state.copy(statusTexts = emptyList())))
    }

    @Test fun `warning report count includes repeats but excludes routine and hidden sessions`() {
        val warning = VehicleStatusText(2, "PreArm: Compass 1 not calibrated", 1_000L)
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = listOf(VehicleStatusText(6, "Routine status", 1_100L), warning, warning,
                VehicleStatusText(4, "GPS warning", 900L)))
        assertEquals(3, currentAutopilotWarningReportCount(state))
        assertEquals(warning, latestAutopilotWarning(state))
        assertEquals(0, currentAutopilotWarningReportCount(state.copy(statusTexts = emptyList())))
        assertEquals(0, currentAutopilotWarningReportCount(state.copy(connectionStatus = VehicleConnectionState.DEGRADED)))
        assertEquals(0, currentAutopilotWarningReportCount(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED)))
        assertEquals(4, state.statusTexts.size)
    }

    @Test fun `future receive time is not presented as fresh`() {
        assertEquals("Receive time unavailable", autopilotWarningAge(VehicleStatusText(4, "Warning", 5_000L), 4_000L))
    }

    @Test fun `diagnostics shows bounded history and filters without changing retained state`() {
        val warning = VehicleStatusText(4, "PreArm: Compass 1 not calibrated", 1_000L)
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = (1..5).map { VehicleStatusText(6, "Info $it", (2_000 + it).toLong()) } + warning)
        val all = ConsolePanels.statusTexts(state, nowEpochMillis = 3_000L)
        assertEquals("CURRENT SESSION · 6 / 6 shown", all.status)
        assertEquals(6, all.rows.size)
        assertEquals("WARNING · 6", all.rows.last().first)
        val filtered = ConsolePanels.statusTexts(state, warningsOnly = true, nowEpochMillis = 3_000L)
        assertEquals("CURRENT SESSION · 1 / 6 shown", filtered.status)
        assertEquals(1, filtered.rows.size)
        assertEquals(true, filtered.rows.single().second.startsWith(warning.text))
        assertEquals(6, state.statusTexts.size)
        assertEquals("LAST SESSION · HISTORICAL · 1 / 6 shown",
            ConsolePanels.statusTexts(state.copy(connectionStatus = VehicleConnectionState.DISCONNECTED), true, 3_000L).status)
        assertEquals("LINK NOT LIVE · RETAINED · 1 / 6 shown",
            ConsolePanels.statusTexts(state.copy(connectionStatus = VehicleConnectionState.DEGRADED,
                mavlinkDiagnostics = MavlinkDiagnostics(active = true)), true, 3_000L).status)
    }

    @Test fun `message search combines with severity filter and keeps history intact`() {
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED, statusTexts = listOf(
            VehicleStatusText(6, "Compass status normal", 1_000L),
            VehicleStatusText(2, "PreArm: Compass 1 not calibrated", 1_000L),
            VehicleStatusText(4, "GPS warning", 1_000L)))
        val found = ConsolePanels.statusTexts(state, nowEpochMillis = 2_000L, query = "  cOmPaSs  ")
        assertEquals("CURRENT SESSION · 2 / 3 shown", found.status)
        assertEquals(2, found.rows.size)
        val warnings = ConsolePanels.statusTexts(state, warningsOnly = true, nowEpochMillis = 2_000L,
            query = "compass")
        assertEquals("CURRENT SESSION · 1 / 3 shown", warnings.status)
        assertEquals(true, warnings.rows.single().second.startsWith("PreArm: Compass"))
        val missing = ConsolePanels.statusTexts(state, warningsOnly = true, nowEpochMillis = 2_000L,
            query = "motor")
        assertEquals("CURRENT SESSION · 0 / 3 shown", missing.status)
        assertEquals(true, missing.rows.isEmpty())
        assertEquals(true, missing.note!!.startsWith("No messages match the active filters."))
        assertEquals(3, ConsolePanels.statusTexts(state, nowEpochMillis = 2_000L, query = "").rows.size)
        assertEquals(3, state.statusTexts.size)
    }
}
