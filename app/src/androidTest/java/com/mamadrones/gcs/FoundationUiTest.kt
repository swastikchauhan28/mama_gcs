package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.navigation.MamaGcsApp
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class FoundationUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("qa")).apply { mkdirs() }
        val file = File(directory, "$name-${context.resources.configuration.screenWidthDp}.png")
        file.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    private fun launch() {
        compose.setContent {
            var theme by remember { mutableStateOf(ThemeMode.DARK) }
            MamaGcsTheme(theme) {
                MamaGcsApp(VehicleState(), SettingsUiState(AppPreferences(theme), loading = false), { theme = it })
            }
        }
    }
    @Test fun initialDashboardDoesNotEnableStop() {
        launch()
        compose.onNodeWithText("●  NOT CONNECTED").assertIsDisplayed()
        compose.onNodeWithText("■  EMERGENCY STOP · UNAVAILABLE").assertIsNotEnabled()
        capture("dashboard-dark")
        compose.onNodeWithText("Select vehicle · none paired").performClick()
        compose.onNodeWithText("Vehicle selection").assertIsDisplayed()
    }
    @Test fun secondaryScreensAndThemeAreReachable() {
        launch()
        val destinations = listOf("Health" to "Vehicle health", "Motors" to "VESC drive system", "Spray" to "Pump, nozzles and application flow", "Hydraulic" to "Pump, valves and pressure", "Diagnostics" to "Vehicle and communication inspection", "Admin" to "Local users, pairing and configuration")
        destinations.forEach { (label, heading) ->
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText(heading).assertIsDisplayed()
            compose.onNodeWithText("‹ Back").performClick()
        }
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("Light").assertIsSelected()
        capture("settings-light")
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
    }
    @Test fun primaryScreensRemainNonOperational() {
        launch()
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("Center vehicle").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Control").performClick()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        // The Mission quick action is only on Home, so this matches bottom/side navigation here.
        compose.onNodeWithText("Mission").performClick()
        compose.onNodeWithText("Start mission").performScrollTo().assertIsNotEnabled()
    }
}
