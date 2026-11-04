package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.MissionDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic local draft on a disposable emulator; the platform accessibility tree is the observed output. */
class FieldCoverageAccessibilityAppTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun coverageControlsResultsAndDiagramReachAndroidAccessibilityAtLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalMissionDraftRepository(context)
        val before = runBlocking { repository.load() }
        val beforeRecovery = runBlocking { repository.loadRecovery() }
        val fixture = MissionDraft("Accessibility field test", keepInFence = coverageTestOutline())
        try {
            runBlocking { repository.clearRecovery(); repository.save(fixture) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    check(it.resources.configuration.fontScale >= 1.8f) { "Set disposable emulator font to at least 1.8x" }
                }
                for ((orientation, label) in listOf(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait",
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    scenario.onActivity { it.requestedOrientation = orientation }
                    compose.waitUntil(15_000) {
                        var actual = 0
                        scenario.onActivity { actual = it.resources.configuration.orientation }
                        actual == if (label == "portrait") Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                    }
                    acknowledgeFullscreenEducation()
                    compose.openWorkspace("mission")
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("mission-editor").fetchSemanticsNodes().isNotEmpty() }
                    compose.selectMissionTab("Coverage")
                    compose.onNodeWithTag("coverage-settings").performScrollTo().performClick()
                    compose.onNodeWithTag("coverage-width").performScrollTo().assertIsDisplayed()
                    assertMaterialFieldLabel("Tool width (m)")
                    compose.onNodeWithTag("coverage-overlap").performScrollTo().assertIsDisplayed()
                    assertMaterialFieldLabel("Overlap (%)")
                    compose.onNodeWithTag("coverage-headland").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Headland width (m)", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("coverage-sideways-error").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Sideways error (m)", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    assertMaterialFieldLabel("Sideways error (m)")
                    compose.onNodeWithTag("coverage-desired-margin").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Extra margin (m)", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    assertMaterialFieldLabel("Extra margin (m)")
                    capture("phase170-$label-inputs.png")
                    compose.onNodeWithTag("coverage-width").performTextReplacement("2")
                    compose.onNodeWithTag("coverage-overlap").performTextReplacement("10")
                    compose.onNodeWithTag("coverage-overlap").performImeAction()
                    compose.onNodeWithTag("coverage-generate").assertIsEnabled().performClick()
                    compose.waitUntil(20_000) {
                        compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    showInEditor("coverage-spacing")
                    val resultLabels = accessibleNodes().map { it.label }
                    val spoken = resultLabels.joinToString(" | ")
                    val passIndex = spoken.indexOf("working passes")
                    val spacingIndex = spoken.indexOf("Maximum pass spacing")
                    assertTrue("Pass count and preview warning must be exposed: ${spoken.take(1200)}",
                        passIndex >= 0 && spoken.contains("preview only"))
                    assertTrue("Spacing must follow pass count in accessibility tree: ${spoken.take(1200)}",
                        spacingIndex > passIndex && spoken.contains("1.80 m"))
                    capture("phase170-$label-results.png")
                    showInEditor("coverage-diagram-summary")
                    var diagramLabels = accessibleNodes().map { it.label }
                    for (attempt in 0 until 24) {
                        if (diagramLabels.any { it.contains("North-up field coverage diagram") }) break
                        compose.onNodeWithTag("mission-editor").performTouchInput {
                            swipeUp(startY = height * 0.8f, endY = height * 0.6f)
                        }
                        diagramLabels = accessibleNodes().map { it.label }
                    }
                    capture("phase170-$label-diagram.png")
                    assertTrue("Diagram needs a descriptive accessible label: ${diagramLabels.joinToString(" | ").take(1200)}",
                        diagramLabels.any { it.contains("North-up field coverage diagram") &&
                            it.contains("green tool strips") && it.contains("Turns are not planned") })
                    assertEquals(fixture, runBlocking { repository.load() })
                }
            }
        } finally {
            runBlocking {
                repository.save(before)
                if (beforeRecovery == null) repository.clearRecovery() else repository.saveRecovery(beforeRecovery)
            }
        }
        assertEquals(before, runBlocking { repository.load() })
        assertEquals(beforeRecovery, runBlocking { repository.loadRecovery() })
    }

    private data class AccessibleNode(val label: String, val editable: Boolean)

    private fun accessibleNodes(): List<AccessibleNode> {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) automation.clearCache()
        val root = automation.rootInActiveWindow
        assertNotNull("Android accessibility root unavailable", root)
        assertEquals("Unexpected foreground accessibility window", "com.mamadrones.gcs", root!!.packageName?.toString())
        val found = mutableListOf<AccessibleNode>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            if (node.isImportantForAccessibility) {
                val label = listOfNotNull(node.text, node.contentDescription, node.hintText)
                    .joinToString(" ") { it.toString() }
                if (label.isNotBlank() || node.isEditable) {
                    found += AccessibleNode(label, node.isEditable)
                }
            }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        return found
    }

    /** Material fields expose their visual label beside the editable node; keep them adjacent in traversal. */
    private fun assertMaterialFieldLabel(expected: String) {
        compose.onNodeWithText(expected, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val nodes = accessibleNodes()
        assertTrue("Material editor and $expected are not adjacent in Android accessibility tree: ${nodes.joinToString(" | ").take(1200)}",
            nodes.indices.any { index ->
                nodes[index].editable && (nodes[index].label.contains(expected) ||
                    nodes.getOrNull(index - 1)?.label?.contains(expected) == true ||
                    nodes.getOrNull(index + 1)?.label?.contains(expected) == true)
            })
    }

    private fun showInEditor(tag: String) {
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.awaitSystemScreenshot()
        try {
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
                .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
