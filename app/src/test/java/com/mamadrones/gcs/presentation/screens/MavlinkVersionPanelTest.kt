package com.mamadrones.gcs.presentation.screens

import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import org.junit.Assert.assertEquals
import org.junit.Test

class MavlinkVersionPanelTest {
    @Test fun versionCountsAreUnknownBeforeSessionAndRetainedAsHistory() {
        val empty = ConsolePanels.mavlinkDiagnostics(MavlinkDiagnostics()).rows.toMap()
        assertEquals("—", empty["Decoded MAVLink 1"])
        assertEquals("—", empty["Decoded MAVLink 2"])
        val history = ConsolePanels.mavlinkDiagnostics(MavlinkDiagnostics(startedAtEpochMillis = 1,
            decodedMessages = 5, decodedV1Messages = 2, decodedV2Messages = 3))
        assertEquals("2", history.rows.toMap()["Decoded MAVLink 1"])
        assertEquals("3", history.rows.toMap()["Decoded MAVLink 2"])
        assertEquals("LAST SESSION · OTHER · CLOSED", history.status)
    }
}
