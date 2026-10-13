package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.screens.MavlinkInspectorScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** In-memory diagnostic fixtures only; does not open any telemetry link. */
class MavlinkInspectorUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private fun sample(now: Long = System.currentTimeMillis(), active: Boolean = false) = MavlinkDiagnostics(
        active = active, linkKind = TelemetryLinkKind.CLASSIC, startedAtEpochMillis = now - 1_000,
        messageObservations = listOf(MavlinkMessageObservation(ObservedMavlinkMessage.HEARTBEAT,
            decodedV1 = 2, decodedV2 = 3, accepted = 3, otherSource = 2,
            lastDecodedAtEpochMillis = now, lastAcceptedAtEpochMillis = now)))

    @Test fun searchAndObservedFilterDoNotInventMissingMessageReception() {
        compose.setContent { MamaGcsTheme { Surface { MavlinkInspectorScreen(sample(20_000)) } } }
        compose.onNodeWithTag("inspector-search").performScrollTo().performTextReplacement("148")
        compose.onNodeWithTag("inspector-result-count").performScrollTo().assertTextEquals("Message types shown: 1")
        compose.onNodeWithText("AUTOPILOT_VERSION · ID 148").assertExists()
        compose.onNodeWithText("NOT OBSERVED").assertExists()
        compose.onNodeWithTag("inspector-observed-only").performScrollTo().performClick()
        compose.onNodeWithTag("inspector-result-count").performScrollTo().assertTextEquals("Message types shown: 0")
        compose.onNodeWithTag("inspector-search").performScrollTo().performTextReplacement("heartbeat")
        compose.onNodeWithTag("inspector-result-count").performScrollTo().assertTextEquals("Message types shown: 1")
        compose.onNodeWithText("LAST SESSION · HISTORICAL").assertExists()
    }

    @Test fun historicalCountsAreReadableInBothThemesAndNewSessionClearsThem() {
        val diagnostics = mutableStateOf(sample())
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) { Surface { MavlinkInspectorScreen(diagnostics.value) } } }
        compose.onNodeWithTag("inspector-observed-only").performScrollTo().performClick()
        compose.onNodeWithTag("inspector-message-0").performScrollTo().assertIsDisplayed()
        capture("phase29-inspector-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithText("LAST SESSION · HISTORICAL").assertExists()
        capture("phase29-inspector-dark")
        compose.runOnIdle { diagnostics.value = MavlinkDiagnostics(active = true,
            linkKind = TelemetryLinkKind.CLASSIC, startedAtEpochMillis = System.currentTimeMillis()) }
        compose.onNodeWithTag("inspector-result-count").performScrollTo().assertTextEquals("Message types shown: 0")
        compose.onNodeWithTag("inspector-message-0").assertDoesNotExist()
    }

    @Test fun recentAcceptedLabelExpiresWithoutNewMessages() {
        val diagnostics = mutableStateOf(MavlinkDiagnostics())
        compose.setContent { MamaGcsTheme { Surface { MavlinkInspectorScreen(diagnostics.value) } } }
        compose.onNodeWithTag("inspector-observed-only").performScrollTo().performClick()
        compose.runOnIdle { diagnostics.value = sample(active = true) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("RECENT ACCEPTED MESSAGE").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("NO RECENT ACCEPTED MESSAGE").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { diagnostics.value = diagnostics.value.copy(active = false) }
        compose.onNodeWithText("LAST SESSION · HISTORICAL").assertExists()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
