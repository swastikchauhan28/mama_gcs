package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.screens.DiagnosticsScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** A screen-only fixture: no socket, saved operator data, or hardware command. */
class DecoderWindowUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun observationStartsAndMarksSessionLossIncomplete() {
        val active = MavlinkDiagnostics(
            active = true,
            linkKind = TelemetryLinkKind.TCP,
            startedAtEpochMillis = System.currentTimeMillis(),
            startedAtElapsedMillis = android.os.SystemClock.elapsedRealtime() - 1_000,
        )
        val state = mutableStateOf(VehicleState(mavlinkDiagnostics = active))
        compose.setContent { MamaGcsTheme { Surface { DiagnosticsScreen(state.value) } } }

        compose.onNodeWithTag("decoder-window-start").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("OBSERVING · TCP").assertExists()
        compose.onNodeWithTag("decoder-window-start").assertIsNotEnabled()

        compose.runOnIdle { state.value = state.value.copy(mavlinkDiagnostics = active.copy(active = false)) }
        compose.onNodeWithText("INCOMPLETE").assertExists()
        compose.onNodeWithText("Receive session ended or changed").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "decoder-window-simulated.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
