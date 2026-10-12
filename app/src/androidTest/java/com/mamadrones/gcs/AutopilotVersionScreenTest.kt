package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.AutopilotVersionContent
import org.junit.Rule
import org.junit.Test

class AutopilotVersionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun absentVersionExplainsPassiveReception() {
        compose.setContent { MaterialTheme { AutopilotVersionContent(VehicleState(), 10_000) } }
        compose.onNodeWithText("NOT RECEIVED").assertExists()
        compose.onNodeWithText("PASSIVE RECEPTION").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4.7.0 · official (type 255)").assertDoesNotExist()
    }

    @Test fun disconnectedVersionIsHistoricalNotConnected() {
        val state = VehicleState(mavlinkDiagnostics = MavlinkDiagnostics(startedAtEpochMillis = 1_000),
            autopilotVersion = AutopilotVersionState(0x040700FF, 0, 0, 0, 0, 0, 2_000))
        compose.setContent { MaterialTheme { AutopilotVersionContent(state, 10_000) } }
        compose.onNodeWithText("LAST OBSERVATION · LINK NOT LIVE").assertExists()
        compose.onNodeWithText("4.7.0 · official (type 255)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NOT HARDWARE VERIFICATION").performScrollTo().assertIsDisplayed()
    }
}
