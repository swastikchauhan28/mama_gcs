package com.mamadrones.gcs.presentation.components

import com.mamadrones.gcs.domain.model.ClassicCheckStage
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicConnectionSimpleSummaryTest {
    @Test fun everyStageHasAPlainLanguageExplanation() {
        val expected = mapOf(
            ClassicCheckStage.CLOSED to "No remote telemetry session is open.",
            ClassicCheckStage.CONNECTING to "The app is trying to open the Bluetooth link.",
            ClassicCheckStage.ERROR to "The Bluetooth link could not be opened or was lost.",
            ClassicCheckStage.WAITING_SESSION to "Bluetooth is open, but this session has no current decoder evidence yet.",
            ClassicCheckStage.WAITING_BYTES to "Bluetooth is open, but no data has arrived in this session yet.",
            ClassicCheckStage.BYTES_STALE to "Data arrived earlier, but none arrived in the last 5 seconds.",
            ClassicCheckStage.WAITING_MAVLINK to "Data is arriving, but valid MAVLink is not confirmed.",
            ClassicCheckStage.WAITING_HEARTBEAT to "Some MAVLink is readable, but a current autopilot heartbeat is not confirmed.",
            ClassicCheckStage.WAITING_MEASUREMENTS to "An autopilot heartbeat is recent, but measurements are not confirmed.",
            ClassicCheckStage.RECEIVING to "An autopilot heartbeat and some measurements are recent; rover control is still unavailable.",
        )
        assertEquals(ClassicCheckStage.entries.toSet(), expected.keys)
        expected.forEach { (stage, summary) -> assertEquals(summary, classicConnectionSimpleSummary(stage)) }
    }
}
