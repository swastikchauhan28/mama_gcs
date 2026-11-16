package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.VehicleStatusText
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

    @Test fun `future receive time is not presented as fresh`() {
        assertEquals("Receive time unavailable", autopilotWarningAge(VehicleStatusText(4, "Warning", 5_000L), 4_000L))
    }

    @Test fun `diagnostics retains an older warning beyond the five recent messages`() {
        val warning = VehicleStatusText(4, "PreArm: Compass 1 not calibrated", 1_000L)
        val state = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
            statusTexts = (1..5).map { VehicleStatusText(6, "Info $it", (2_000 + it).toLong()) } + warning)
        val rows = ConsolePanels.statusTexts(state).rows
        assertEquals(6, rows.size)
        assertEquals("Last warning · WARNING", rows.last().first)
        assertEquals(true, rows.last().second.startsWith(warning.text))
    }
}
