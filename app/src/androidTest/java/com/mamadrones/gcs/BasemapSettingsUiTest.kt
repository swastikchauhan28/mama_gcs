package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.MapSettingsScreen
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BasemapSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun choicesSelectAndRemainReadableInBothThemes() {
        val state = mutableStateOf(SettingsUiState(loading = false))
        val theme = mutableStateOf(ThemeMode.LIGHT)
        var openMap = false
        compose.setContent { MamaGcsTheme(theme.value) { Surface {
            MapSettingsScreen(state.value, { state.value = state.value.copy(
                preferences = state.value.preferences.copy(basemap = it)) }) { openMap = true }
        } } }
        BasemapStyle.entries.forEach { style ->
            compose.onNodeWithTag("basemap-${style.name}").performScrollTo().performClick().assertIsSelected()
        }
        compose.onNodeWithText("Map appearance").performScrollTo()
        capture("phase30-maps-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        capture("phase30-maps-dark")
        compose.onNodeWithText("Open map").performScrollTo().performClick()
        assertTrue(openMap)
        compose.onNodeWithText("IMAGERY LIMITATIONS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OFFLINE REGIONS").performScrollTo().assertIsDisplayed()
    }

    @Test fun loadingAndSavingDisableChoicesAndSaveErrorKeepsPreviousSelection() {
        val state = mutableStateOf(SettingsUiState())
        var requests = 0
        compose.setContent { MamaGcsTheme { Surface {
            MapSettingsScreen(state.value, { requests++ }) {}
        } } }
        compose.onNodeWithTag("basemap-SATELLITE").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(loading = false, saving = true) }
        compose.onNodeWithTag("basemap-SATELLITE").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(saving = false, error = "Map choice could not be saved. Try again.") }
        compose.onNodeWithTag("basemap-STREETS").performScrollTo().assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("Map choice could not be saved. Try again.").performScrollTo().assertIsDisplayed()
        assertEquals(0, requests)
    }

    private fun capture(name: String) {
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
