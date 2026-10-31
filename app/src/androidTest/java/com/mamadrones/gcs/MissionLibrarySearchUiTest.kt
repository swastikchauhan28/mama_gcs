package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Local library display fixture; callbacks never reach the repository or a vehicle. */
class MissionLibrarySearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun searchFiltersRoutesAndKeepsActionsBoundToIdsAtLargeText() {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
        }
        val entries = listOf(
            MissionLibraryEntry("lib-field", MissionDraft("Long field route name for search verification"), 1_790_000_000_000L),
            MissionLibraryEntry("lib-east", MissionDraft("Eastern survey"), 1_790_000_001_000L),
            MissionLibraryEntry("lib-north", MissionDraft("Northern boundary"), 1_790_000_002_000L),
        )
        val actions = mutableListOf<MissionPlanAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(loading = false, library = entries), actions::add)
                    }
                }
            }
        }

        fun openLibrary() {
            compose.openMissionFiles()
            compose.onNodeWithTag("mission-editor").performScrollToIndex(0)
            compose.onNodeWithText("Library ·", substring = true).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Local route library · 3").assertIsDisplayed()
        }

        fun searchFor(query: String) {
            compose.onNodeWithTag("mission-library-search").assertIsDisplayed().performTextReplacement(query)
        }

        openLibrary()
        searchFor("nOrTh")
        compose.onNodeWithTag("mission-library-result-count").assertTextEquals("1 of 3 routes")
        compose.onNodeWithTag("mission-library-search").assertIsDisplayed()
        val screenshot = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val screenshotFile = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase68-route-search.png")
        screenshotFile.outputStream().use { output ->
            assertTrue("Filtered library screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        compose.onNodeWithTag("library-entry-lib-north").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("library-entry-lib-field").assertDoesNotExist()
        compose.onNodeWithTag("library-entry-lib-east").assertDoesNotExist()

        searchFor("zzz")
        compose.onNodeWithTag("mission-library-result-count").assertTextEquals("0 of 3 routes")
        compose.onNodeWithTag("mission-library-no-matches").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("library-entry-lib-north").assertDoesNotExist()
        compose.onNodeWithTag("mission-library-clear-search").performClick()
        compose.onNodeWithTag("mission-library-result-count").assertDoesNotExist()
        entries.forEach { compose.onNodeWithTag("library-entry-${it.id}").assertExists() }
        val searchTop = compose.onNodeWithTag("mission-library-search").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("library-entry-lib-north").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("mission-library-search").assertIsDisplayed()
        assertEquals("Search field must stay fixed while route cards scroll", searchTop,
            compose.onNodeWithTag("mission-library-search").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("Close").assertIsDisplayed()
        val scrolledScreenshot = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase69-pinned-library-search.png")
            .outputStream().use { output ->
                assertTrue("Scrolled library screenshot must be written", scrolledScreenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }

        searchFor("FiElD")
        compose.onNodeWithTag("library-entry-lib-field").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("library-entry-lib-north").assertDoesNotExist()
        compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag("library-entry-lib-field")))
            .performScrollTo().performClick()
        compose.onNodeWithText("Open as draft").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MissionPlanAction.OpenLibraryEntry("lib-field")), actions) }

        openLibrary()
        entries.forEach { compose.onNodeWithTag("library-entry-${it.id}").assertExists() }
        compose.onNodeWithTag("mission-library-result-count").assertDoesNotExist()
        searchFor("NORTH")
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag("library-entry-lib-north")))
            .performScrollTo().performClick()
        compose.onNodeWithText("Delete route").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(
                MissionPlanAction.OpenLibraryEntry("lib-field"),
                MissionPlanAction.DeleteLibraryEntry("lib-north"),
            ), actions)
        }
    }

    @Test fun keyboardKeepsSearchResultAndCloseReachableAtLargeText() {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val entries = listOf(
            MissionLibraryEntry("lib-field", MissionDraft("Field boundary"), 1_790_000_000_000L),
            MissionLibraryEntry("lib-north", MissionDraft("Northern boundary"), 1_790_000_001_000L),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(loading = false, library = entries), {})
                    }
                }
            }
        }
        compose.openMissionFiles()
        compose.onNodeWithTag("mission-editor").performScrollToIndex(0)
        compose.onNodeWithText("Library ·", substring = true).performScrollTo().performClick()
        compose.onNodeWithTag("mission-library-search").performClick()

        fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

        compose.awaitRealKeyboard(::imeVisible) { compose.onNodeWithTag("mission-library-search").performClick() }
        compose.onNodeWithTag("mission-library-search").performTextInput("north")
        compose.onNodeWithTag("mission-library-result-count").assertTextEquals("1 of 2 routes")
        compose.onNodeWithTag("library-entry-lib-field").assertDoesNotExist()
        assertTrue("IME must remain visible before Done", imeVisible())

        val screenshot = compose.awaitSystemScreenshot()
        File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase70-library-ime.png")
            .outputStream().use { output ->
                assertTrue("Keyboard-open screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        screenshot.recycle()
        compose.onNodeWithTag("mission-library-search").performImeAction()
        compose.waitUntil(10_000) { !imeVisible() }
        compose.onNodeWithTag("mission-library-result-count").assertTextEquals("1 of 2 routes")
        compose.onNodeWithTag("library-entry-lib-north").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag("library-entry-lib-north")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        val afterDone = compose.awaitSystemScreenshot()
        File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase70-library-after-done.png")
            .outputStream().use { output ->
                assertTrue("After-Done screenshot must be written", afterDone.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        afterDone.recycle()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Local route library · 2").assertDoesNotExist()
    }
}
