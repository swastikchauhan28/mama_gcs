package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.ClassicCheckStage
import com.mamadrones.gcs.domain.model.ClassicConnectionAssessment
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.presentation.components.ClassicConnectionCheckPanel
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic copy/visibility test. Never opens Bluetooth or commands hardware. */
class ClassicConnectionPlainLanguageUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun eachStageExplainsOnlyItsReceiveEvidence() {
        val check = mutableStateOf(ClassicConnectionAssessment(ClassicCheckStage.CLOSED))
        compose.setContent { MamaGcsTheme {
            Surface(Modifier.verticalScroll(rememberScrollState())) { ClassicConnectionCheckPanel(check.value) }
        } }
        val expected = listOf(
            ClassicCheckStage.CLOSED to "No remote telemetry session is open.",
            ClassicCheckStage.CONNECTING to "The app is trying to open the Bluetooth link.",
            ClassicCheckStage.ERROR to "The Bluetooth link could not be opened or was lost.",
            ClassicCheckStage.WAITING_SESSION to "Bluetooth is open, but this session has no current decoder evidence yet.",
            ClassicCheckStage.WAITING_BYTES to "Bluetooth is open, but no data has arrived in this session yet.",
            ClassicCheckStage.BYTES_STALE to "Data arrived earlier, but none arrived in the last 5 seconds.",
            ClassicCheckStage.WAITING_MAVLINK to "Data is arriving, but Cube MAVLink is not confirmed.",
            ClassicCheckStage.WAITING_HEARTBEAT to "Some MAVLink is readable, but a current autopilot heartbeat is not confirmed.",
            ClassicCheckStage.WAITING_MEASUREMENTS to "An autopilot heartbeat is recent, but measurements are not confirmed.",
            ClassicCheckStage.RECEIVING to "An autopilot heartbeat and some measurements are recent; rover control is still unavailable.",
        )
        expected.forEach { (stage, summary) ->
            compose.runOnIdle { check.value = ClassicConnectionAssessment(stage) }
            compose.onNodeWithTag("classic-check-simple-summary")
                .assertTextEquals("In simple words: $summary")
        }
    }

    @Test fun undecodedBytesSummaryIsVisibleInBothThemes() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MamaGcsTheme(theme.value) {
            Surface(Modifier.verticalScroll(rememberScrollState())) {
                ClassicConnectionCheckPanel(ClassicConnectionAssessment(
                    ClassicCheckStage.WAITING_MAVLINK, socketOpen = true, freshBytes = true))
            }
        } }
        compose.onNodeWithTag("classic-check-simple-summary")
            .assertTextEquals("In simple words: Data is arriving, but Cube MAVLink is not confirmed.")
        capture("phase47-simple-classic-light")
        compose.runOnIdle { theme.value = ThemeMode.DARK }
        compose.onNodeWithTag("classic-check-simple-summary")
            .assertTextEquals("In simple words: Data is arriving, but Cube MAVLink is not confirmed.")
        capture("phase47-simple-classic-dark")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
