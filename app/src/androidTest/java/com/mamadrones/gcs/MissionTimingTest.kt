package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.mission.*
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic local draft only. No vehicle session, storage changes or movement commands. */
class MissionTimingTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val route = MissionDraft("Timing fixture", listOf(
        DraftWaypoint("a", 0.0, 0.0), DraftWaypoint("b", 0.0, 0.001)))

    private fun review() {
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-tab-Review"))
        compose.onNodeWithTag("mission-tab-Review").performClick()
    }
    private fun speed(value: String) {
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-planning-speed"))
        compose.onNodeWithTag("mission-planning-speed").performTextReplacement(value)
        compose.onNodeWithTag("mission-planning-speed").performImeAction()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-travel-total"))
    }

    @Test(timeout = 120_000) fun speedIsExplicitBoundedAndNeverDispatchesMissionActions() {
        var actions = 0
        compose.setContent { MamaGcsTheme {
            MissionScreen(VehicleState(), MissionPlanUiState(draft = route, loading = false), { actions++ })
        } }
        review()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-travel-total"))
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Enter a planning speed to estimate travel time.")
        speed("8")
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Total travel estimate: 51s")
        speed("14.4")
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Correct the speed to calculate; no previous estimate is shown.")
        compose.onNodeWithTag("mission-timing-leg-1").assertDoesNotExist()
        speed("4,0")
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Total travel estimate: 1m 41s")
        compose.onNodeWithTag("mission-save").assertIsNotEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
        compose.onNodeWithText("Start mission").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test(timeout = 120_000) fun changingRouteRecomputesLegsAndClearingDoesNotRetainOldTime() {
        val state = mutableStateOf(MissionPlanUiState(draft = route, loading = false))
        compose.setContent { MamaGcsTheme { MissionScreen(VehicleState(), state.value, {}) } }
        review(); speed("3.6")
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Total travel estimate: 1m 52s")
        compose.runOnIdle { state.value = state.value.copy(draft = route.add(DraftWaypoint("c", 0.0, 0.002))) }
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Total travel estimate: 3m 43s")
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-timing-leg-2"))
        compose.onNodeWithText("WP 2 → WP 3").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(draft = MissionDraft()) }
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-travel-total"))
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Add at least 2 waypoints to estimate travel time.")
        compose.onNodeWithTag("mission-timing-leg-2").assertDoesNotExist()
    }

    @Test(timeout = 120_000) fun unavailableRouteDisablesInputAndHidesPreviousEstimate() {
        val state = mutableStateOf(MissionPlanUiState(draft = route, loading = false))
        compose.setContent { MamaGcsTheme { MissionScreen(VehicleState(), state.value, {}) } }
        review(); speed("8")
        compose.runOnIdle { state.value = state.value.copy(loadFailed = true) }
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-planning-speed"))
        compose.onNodeWithTag("mission-planning-speed").assertIsNotEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-travel-total"))
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Route unavailable while loading or after a load failure.")
    }

    @Test(timeout = 120_000) fun timingPanelLightAndDarkThemeEvidence() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) {
            Surface { Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
                MissionTimingPanel("8", {}, MissionTravelEstimate.calculate(route, 8.0), true)
            } }
        } }
        compose.onNodeWithTag("mission-planning-speed").assertIsDisplayed()
        compose.onNodeWithTag("mission-travel-total").assertTextEquals("Total travel estimate: 51s")
        capture("phase26-timing-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithTag("mission-planning-speed").assertIsDisplayed()
        capture("phase26-timing-dark")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
