package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Disposable emulator only. Restores its local draft/recovery; no telemetry session is opened. */
class FieldCoverageAppTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realPlannerShowsCoverageInPortraitAndLandscapeWithoutSavingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = LocalMissionDraftRepository(context)
        val before = runBlocking { repository.load() }
        val beforeRecovery = runBlocking { repository.loadRecovery() }
        val original = MissionDraft("Coverage acceptance field", listOf(DraftWaypoint("wp", 0.0, 0.0)), coverageTestOutline(true))
        try {
            runBlocking { repository.save(original) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.openWorkspace("mission")
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-tab-Coverage").performScrollTo().performClick()
                node("coverage-width").performTextReplacement("2")
                node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
                node("coverage-generate").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                node("coverage-diagram").assertIsDisplayed()
                assertWholeDiagramVisible()
                capture("phase76-app-portrait.png")
                node("coverage-compare").performClick()
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
                node("coverage-comparison-north-passes")
                capture("phase80-app-observe.png")
                node("coverage-comparison-north-passes").assertIsDisplayed()
                node("coverage-comparison-east-passes").assertIsDisplayed()
                capture("phase80-app-portrait.png")
                assertEquals(original, runBlocking { repository.load() })
                assertNull(runBlocking { repository.loadRecovery() })
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(10_000) {
                    var landscape = false
                    scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    landscape
                }
                // Preview is volatile across recreation; regenerate if the activity was recreated.
                node("coverage-width").performClick().performTextReplacement("2")
                fun keyboardVisible(): Boolean {
                    var visible = false
                    scenario.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                    return visible
                }
                compose.waitUntil(10_000) { keyboardVisible() }
                capture("phase76-app-landscape-settings-ime.png")
                node("coverage-width").performImeAction()
                compose.waitUntil(10_000) { !keyboardVisible() }
                capture("phase76-app-landscape-settings.png")
                node("coverage-generate").performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                node("coverage-diagram").assertIsDisplayed()
                assertWholeDiagramVisible()
                capture("phase76-app-landscape.png")
                node("coverage-compare").performClick()
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
                node("coverage-comparison-north-passes").assertIsDisplayed()
                node("coverage-comparison-east-passes").assertIsDisplayed()
                capture("phase80-app-landscape.png")
                assertEquals(original, runBlocking { repository.load() })
                assertNull(runBlocking { repository.loadRecovery() })
                node("mission-tab-Route").performClick()
                node("mission-edit-0").assertIsDisplayed()
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText("Start mission"))
                compose.onNodeWithText("Start mission").assertIsNotEnabled()
            }
        } finally {
            runBlocking { repository.save(before); beforeRecovery?.let { repository.saveRecovery(it) } }
        }
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        if (tag in setOf("coverage-width", "coverage-overlap", "coverage-generate")) {
            if (compose.onAllNodesWithTag("coverage-generate").fetchSemanticsNodes().isEmpty()) node("coverage-settings").performClick()
            val target = compose.onNodeWithTag(tag)
            return if (tag == "coverage-generate") target else target.performScrollTo()
        }
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        val target = compose.onNodeWithTag(tag).performScrollTo()
        if (tag.startsWith("coverage-comparison-") && tag != "coverage-comparison-warning") {
            repeat(12) {
                val viewport = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
                val bounds = target.getUnclippedBoundsInRoot()
                if (target.isDisplayed() && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom) return target
                compose.onNodeWithTag("mission-editor").performTouchInput {
                    if (bounds.top < viewport.top) swipeDown(startY = height * 0.25f, endY = height * 0.45f)
                    else swipeUp(startY = height * 0.75f, endY = height * 0.55f)
                }
            }
            val viewport = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
            val bounds = target.getUnclippedBoundsInRoot()
            assertTrue("Comparison item must fit vertically in editor: $tag $bounds / $viewport",
                target.isDisplayed() && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }
        return target
    }

    private fun assertWholeDiagramVisible() {
        val diagram = compose.onNodeWithTag("coverage-diagram").getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
        assertTrue("Whole diagram must fit within the editor: $diagram / $editor",
            diagram.top >= editor.top && diagram.bottom <= editor.bottom && diagram.left >= editor.left && diagram.right <= editor.right)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
}
