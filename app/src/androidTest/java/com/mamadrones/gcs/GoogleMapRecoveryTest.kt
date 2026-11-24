package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.BasemapStyle
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.map.GoogleMapFailure
import com.mamadrones.gcs.presentation.map.GoogleMapRecoveryNotice
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.screens.MapSettingsScreen
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse

/** Synthetic display failures: no key changes, service shutdown or network fault injection. */
class GoogleMapRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    /** Run with an intentionally keyless debug build; never modify local.properties. */
    @Test fun keylessAppRoutesFromMapFailureToExistingSettings() {
        assumeFalse("This test requires a keyless debug build", BuildConfig.GOOGLE_MAPS_CONFIGURED)
        val state = mutableStateOf(SettingsUiState(loading = false).let {
            it.copy(preferences = it.preferences.copy(basemap = BasemapStyle.GOOGLE_STREETS))
        })
        compose.setContent { MamaGcsTheme {
            MamaGcsApp(vehicle = VehicleState(), settings = state.value, onThemeSelected = {},
                onBasemapSelected = { choice ->
                    state.value = state.value.copy(preferences = state.value.preferences.copy(basemap = choice))
                })
        } }
        compose.onNodeWithText(GoogleMapFailure.MISSING_KEY.title).assertIsDisplayed()
        compose.onNodeWithTag("google-map-choose-provider").assertIsDisplayed().performClick()
        compose.onNodeWithText("Map appearance").assertExists()
        assertEquals(BasemapStyle.GOOGLE_STREETS, state.value.preferences.basemap)
        compose.onNodeWithTag("basemap-STREETS").performScrollTo().performClick().assertIsSelected()
        assertEquals(BasemapStyle.STREETS, state.value.preferences.basemap)
    }

    @Test fun failureActionsOpenExistingSettingsWithoutSilentlyChangingProvider() {
        val failure = mutableStateOf(GoogleMapFailure.MISSING_KEY)
        val inSettings = mutableStateOf(false)
        val selected = mutableStateOf(BasemapStyle.GOOGLE_STREETS)
        var retries = 0
        compose.setContent { MamaGcsTheme {
            if (inSettings.value) {
                MapSettingsScreen(SettingsUiState(loading = false).copy(
                    preferences = SettingsUiState().preferences.copy(basemap = selected.value)),
                    onBasemapSelected = { selected.value = it }, onOpenMap = {})
            } else {
                GoogleMapRecoveryNotice(failure.value, Modifier.fillMaxSize(),
                    onChooseProvider = { inSettings.value = true },
                    onRetry = if (failure.value == GoogleMapFailure.TILES_TIMED_OUT) ({ retries++ }) else null)
            }
        } }

        GoogleMapFailure.entries.forEach { case ->
            compose.runOnIdle {
                selected.value = BasemapStyle.GOOGLE_STREETS
                failure.value = case
                inSettings.value = false
            }
            compose.onNodeWithText(case.title).assertIsDisplayed()
            assertEquals(BasemapStyle.GOOGLE_STREETS, selected.value)
            if (case == GoogleMapFailure.TILES_TIMED_OUT) {
                File(compose.activity.getExternalFilesDir(null), "phase230-map-timeout.png").outputStream().use {
                    assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                compose.onNodeWithTag("google-map-retry").assertIsDisplayed().performClick()
                assertEquals(1, retries)
            } else compose.onNodeWithTag("google-map-retry").assertDoesNotExist()
            compose.onNodeWithTag("google-map-choose-provider").assertIsDisplayed().performClick()
            compose.onNodeWithText("Map appearance").assertExists()
            compose.onNodeWithTag("basemap-GOOGLE_STREETS").performScrollTo().assertIsSelected()
            assertEquals(BasemapStyle.GOOGLE_STREETS, selected.value)
            compose.onNodeWithTag("basemap-STREETS").performScrollTo().performClick().assertIsSelected()
            assertEquals(BasemapStyle.STREETS, selected.value)
        }
    }
}
