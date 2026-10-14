package com.mamadrones.gcs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.dashboard.HomePositionCard
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test

class HomePositionUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    @Test fun reportedHomeAndHistoricalStatesAreReadableInBothThemesWithoutCommands() {
        val state = mutableStateOf(VehicleState())
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) { ScreenBody {
            HomePositionCard(state.value)
        } } }
        compose.onNodeWithText("NOT RECEIVED").assertIsDisplayed()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle {
                theme.value = mode
                val now = System.currentTimeMillis()
                state.value = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED,
                    lastHeartbeatAtEpochMillis = now,
                    mavlinkDiagnostics = MavlinkDiagnostics(active = true, startedAtEpochMillis = now - 9_000),
                    homePosition = HomePositionState(-35.3632621, 149.1652374, 584.0, now - 8_000))
            }
            compose.onNodeWithText("REPORTED HOME · NOT VERIFIED").assertIsDisplayed()
            compose.onNodeWithText("-35.3632621 °").assertIsDisplayed()
            compose.onNodeWithText("No Set Home, Return or message request is sent.", substring = true).assertIsDisplayed()
            java.io.File(compose.activity.getExternalFilesDir(null), "phase31-home-${mode.name}.png").outputStream().use {
                check(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.runOnIdle { state.value = state.value.copy(connectionStatus = VehicleConnectionState.DISCONNECTED) }
            compose.onNodeWithText("LAST OBSERVATION · LINK NOT LIVE").assertIsDisplayed()
        }
        compose.runOnIdle { state.value = VehicleState() }
        compose.onNodeWithText("-35.3632621 °").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
