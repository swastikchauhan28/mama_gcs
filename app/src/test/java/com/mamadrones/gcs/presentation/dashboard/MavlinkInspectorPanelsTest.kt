package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MavlinkInspectorPanelsTest {
    private val type = ObservedMavlinkMessage.HEARTBEAT
    private val observation = MavlinkMessageObservation(type, decodedV2 = 3, accepted = 1, otherSource = 2,
        lastDecodedAtEpochMillis = 20_000, lastAcceptedAtEpochMillis = 18_000)
    private val diagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = 10_000,
        messageObservations = listOf(observation))
    private fun panel(sample: MavlinkDiagnostics = diagnostics, now: Long = 20_000) =
        MavlinkInspectorPanels.panel(type, sample, now)

    @Test fun noSessionListsTheAppSubsetAsUnobservedNotVehicleCapabilities() {
        assertEquals(12, MavlinkInspectorPanels.visibleTypes(MavlinkDiagnostics(), "", false).size)
        assertTrue(MavlinkInspectorPanels.visibleTypes(MavlinkDiagnostics(), "", true).isEmpty())
        assertEquals("NOT OBSERVED", panel(MavlinkDiagnostics()).status)
        assertEquals("NOT RECEIVED", panel(MavlinkDiagnostics()).rows.toMap()["Last accepted"])
    }
    @Test fun nameAndIdSearchCombineWithObservedFilter() {
        assertEquals(listOf(type), MavlinkInspectorPanels.visibleTypes(diagnostics, " heartBEAT ", true))
        assertEquals(listOf(ObservedMavlinkMessage.AUTOPILOT_VERSION),
            MavlinkInspectorPanels.visibleTypes(diagnostics, "148", false))
        assertTrue(MavlinkInspectorPanels.visibleTypes(diagnostics, "148", true).isEmpty())
        assertTrue(MavlinkInspectorPanels.visibleTypes(diagnostics, "does not exist", false).isEmpty())
    }
    @Test fun acceptedAndOtherSourceCountsAndTimesRemainDistinct() {
        val result = panel()
        assertEquals("RECENT ACCEPTED MESSAGE", result.status)
        assertEquals("3", result.rows.toMap()["Decoded (all sources)"])
        assertEquals("1", result.rows.toMap()["Accepted (selected autopilot)"])
        assertEquals("2", result.rows.toMap()["Ignored other source"])
        assertEquals("2s ago", result.rows.toMap()["Last accepted"])
        assertEquals("0s ago", result.rows.toMap()["Last decoded (any source)"])
    }
    @Test fun newFilteredMessagesDoNotMakeOldAcceptedSamplesRecent() {
        val sample = diagnostics.copy(messageObservations = listOf(observation.copy(lastAcceptedAtEpochMillis = 10_000)))
        assertEquals("NO RECENT ACCEPTED MESSAGE", panel(sample).status)
    }
    @Test fun fiveSecondDisplayWindowExpiresWithTimeAlone() {
        assertEquals("RECENT ACCEPTED MESSAGE", panel(now = 23_000).status)
        assertEquals("NO RECENT ACCEPTED MESSAGE", panel(now = 23_001).status)
    }
    @Test fun closedSessionIsHistoricalEvenForRecentReceipt() {
        val result = panel(diagnostics.copy(active = false))
        assertEquals("LAST SESSION · HISTORICAL", result.status)
        assertEquals("2s ago · HISTORICAL", result.rows.toMap()["Last accepted"])
    }
    @Test fun invalidOrPreviousSessionTimesNeverProduceRecentLabel() {
        listOf(null, -1L, 9_999L, 20_001L, Long.MAX_VALUE).forEach {
            assertEquals("ACCEPTED · TIME UNAVAILABLE", panel(diagnostics.copy(messageObservations =
                listOf(observation.copy(lastAcceptedAtEpochMillis = it)))).status)
        }
        listOf(null, -1L, 20_001L).forEach {
            assertEquals("ACCEPTED · TIME UNAVAILABLE", panel(diagnostics.copy(startedAtEpochMillis = it)).status)
        }
    }
    @Test fun observedButFilteredDoesNotClaimSelectedAutopilotReceipt() {
        val sample = diagnostics.copy(messageObservations = listOf(observation.copy(accepted = 0, lastAcceptedAtEpochMillis = null)))
        assertEquals(listOf(type), MavlinkInspectorPanels.visibleTypes(sample, "", true))
        assertEquals("DECODED · NOT ACCEPTED", panel(sample).status)
    }
}
