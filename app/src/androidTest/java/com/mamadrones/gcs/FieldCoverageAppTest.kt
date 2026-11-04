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

    @Test fun enlargedTextCoverageControlsStayBelowToolbarInApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                check(it.resources.configuration.fontScale >= 1.8f) { "System font scale must be >= 1.8" }
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.openWorkspace("mission")
            compose.onNodeWithTag("mission-tab-Coverage").performScrollTo().performClick()
            val editor = compose.onNodeWithTag("mission-editor").getUnclippedBoundsInRoot()
            val save = compose.onNodeWithTag("mission-save").getUnclippedBoundsInRoot()
            assertTrue("Editor must start below the fixed toolbar", editor.top >= save.bottom)
            for (tag in listOf("coverage-settings", "coverage-options-open")) {
                val control = node(tag)
                val bounds = control.getUnclippedBoundsInRoot()
                assertTrue("$tag must be fully within the editor: $bounds / $editor",
                    control.isDisplayed() && bounds.top >= editor.top && bounds.bottom <= editor.bottom)
                capture("phase159-app-$tag.png")
            }
        }
    }

    @Test fun fieldEditorRecoversFromCrossingOutlineAndKeepsRoute() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = runBlocking { repository.load() }
        val beforeRecovery = runBlocking { repository.loadRecovery() }
        val points = listOf(DraftWaypoint("route-a", -35.36326, 149.16523), DraftWaypoint("route-b", -35.36330, 149.16527))
        val vertices = listOf(
            DraftWaypoint("f0", -35.3634, 149.1651), DraftWaypoint("f1", -35.3634, 149.1653),
            DraftWaypoint("f2", -35.3632, 149.1653), DraftWaypoint("f3", -35.3632, 149.1651))
        val original = MissionDraft("Editor recovery test", points, vertices)
        try {
            runBlocking { repository.save(original) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.openWorkspace("mission")
                compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                fun generate() {
                    node("coverage-width").performTextReplacement("2")
                    node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
                    node("coverage-generate").assertIsEnabled().performClick()
                    compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                }
                fun addVertex(point: DraftWaypoint) {
                    node("mission-add-fence-vertex").performClick()
                    compose.onNodeWithTag("mission-latitude").performTextReplacement(point.latitude.toString())
                    compose.onNodeWithTag("mission-longitude").performTextReplacement(point.longitude.toString())
                    compose.onNodeWithTag("mission-confirm-waypoint").performClick()
                }
                compose.selectMissionTab("Coverage")
                generate()
                node("coverage-diagram").assertIsDisplayed()
                compose.selectMissionTab("Field outline")
                node("mission-remove-fence-1").performClick()
                addVertex(vertices[1])
                compose.selectMissionTab("Coverage")
                node("coverage-empty").assertIsDisplayed()
                generate()
                node("coverage-error").assertTextEquals("The field outline crosses or touches itself. Reorder its vertices.")
                capture("phase162-app-crossing.png")
                compose.selectMissionTab("Field outline")
                node("mission-clear-fence").performClick()
                vertices.forEach(::addVertex)
                compose.selectMissionTab("Coverage")
                node("coverage-empty").assertIsDisplayed()
                generate()
                node("coverage-diagram").assertIsDisplayed()
                capture("phase162-app-corrected.png")
                compose.selectMissionTab("Route")
                for ((index, point) in points.withIndex()) {
                    node("mission-edit-$index").performClick()
                    compose.onNodeWithTag("mission-latitude").assertTextContains(point.latitude.toString(), substring = true)
                    compose.onNodeWithTag("mission-longitude").assertTextContains(point.longitude.toString(), substring = true)
                    compose.onNodeWithText("Cancel").performClick()
                }
                assertEquals("Preview and editor changes must not overwrite the saved draft", original, runBlocking { repository.load() })
            }
        } finally {
            runBlocking { repository.save(before); beforeRecovery?.let { repository.saveRecovery(it) } }
        }
    }

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
                compose.awaitRealKeyboard(::keyboardVisible) { node("coverage-width").performClick() }
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
        val bitmap = compose.awaitSystemScreenshot()
        try { File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
}
