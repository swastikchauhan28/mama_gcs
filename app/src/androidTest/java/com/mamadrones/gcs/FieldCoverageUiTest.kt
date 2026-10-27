package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.mission.*
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

internal fun coverageTestOutline(triangle: Boolean = false): List<DraftWaypoint> {
    val xy = if (triangle) listOf(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0)
        else listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0)
    return xy.mapIndexed { i, (x, y) -> DraftWaypoint("f$i", Math.toDegrees(y / 6_371_000.0), Math.toDegrees(x / 6_371_000.0)) }
}

/** Synthetic geometry only; callbacks are captured in memory and never reach storage or a vehicle. */
class FieldCoverageUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()
    private val actions = mutableListOf<MissionPlanAction>()

    @Test fun settingsRegeneratePreviewAndNeverReplaceRoute() {
        show()
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
        generate()
        node("coverage-pass-count").assertTextEquals("5 working passes · preview only")
        node("coverage-spacing").assertTextEquals("Maximum pass spacing: 2.00 m")
        node("coverage-area-headland").assertTextEquals("Estimated reserved headland: none (0 m width)")
        node("coverage-area-gap").assertTextEquals("Estimated unplanned main area: none detected")
        node("coverage-range-headland").assertTextContains("0.0–0.0", substring = true)
        node("coverage-range-gap").assertTextContains("Modeled unplanned main range", substring = true)
        node("coverage-range-warning").assertTextContains("not actual soil", substring = true)
        node("coverage-area-resolution").assertTextContains("Estimates can be inaccurate", substring = true)
        node("coverage-area-resolution").assertTextContains("Narrow gaps may be missed", substring = true)
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        generate()
        node("coverage-pass-count").assertTextEquals("6 working passes · preview only")
        node("coverage-spacing").assertTextEquals("Maximum pass spacing: 1.80 m")
        node("coverage-east-west").performClick()
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        generate()
        node("coverage-pass-count").assertTextEquals("11 working passes · preview only")
        node("coverage-overlap").apply { performTextReplacement("100"); performImeAction() }
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-input-error").assertTextEquals("Enter overlap from 0 to 80 percent.")
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-empty").assertIsDisplayed()
        assertTrue(actions.isEmpty())
    }

    @Test fun missingAndChangedOutlineCannotLeaveAnOldPreview() {
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-width").performTextReplacement("2")
        generate()
        node("coverage-pass-count").assertIsDisplayed()
        compose.runOnIdle { draft.value = MissionDraft() }
        node("coverage-empty").assertIsDisplayed()
        node("coverage-width").performTextReplacement("2")
        generate(expectError = true)
        node("coverage-error").assertTextEquals("Add 3 to 64 field-outline vertices first.")
        assertTrue(actions.isEmpty())
    }

    @Test fun trianglePreviewAndControlsRemainReachableAtLargeText() {
        show(largeText = true, draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline(true))))
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        node("coverage-generate").assertIsDisplayed()
        capture("phase76-large-inputs.png")
        generate()
        node("coverage-diagram").assertIsDisplayed()
        capture("phase76-large-triangle.png")
        node("coverage-compare").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
        node("coverage-comparison-north-passes")
        capture("phase80-large-observe.png")
        node("coverage-comparison-north-passes").assertIsDisplayed()
        node("coverage-comparison-east-passes").assertIsDisplayed()
        capture("phase80-large-comparison.png")
        assertTrue(actions.isEmpty())
    }

    @Test fun headlandInvalidatesAndPreviewsRectangleTriangleAndConcaveFields() {
        val metres = 6_371_000.0
        fun field(xy: List<Pair<Double, Double>>) = xy.mapIndexed { i, (x, y) -> DraftWaypoint("h$i", Math.toDegrees(y / metres), Math.toDegrees(x / metres)) }
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-width").performTextReplacement("1")
        node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
        generate()
        node("coverage-headland-result").assertTextEquals("Reserved headland: 0.50 m")
        node("coverage-area-headland").assertTextContains("≈", substring = true)
        node("coverage-area-gap").assertTextContains("≈", substring = true)
        node("coverage-range-headland").assertTextContains("Modeled headland range", substring = true)
        node("coverage-range-gap").assertTextContains("Modeled unplanned main range", substring = true)
        node("coverage-range-warning").assertTextContains("not actual soil", substring = true)
        capture("phase79-ranges.png")
        node("coverage-area-resolution").assertTextContains("Narrow gaps may be missed", substring = true)
        capture("phase78-estimates.png")
        node("coverage-diagram").assertIsDisplayed()
        capture("phase77-rectangle.png")
        val shapes = listOf(coverageTestOutline(true), field(listOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)))
        for ((index, outline) in shapes.withIndex()) {
            compose.runOnIdle { draft.value = MissionDraft(keepInFence = outline) }
            node("coverage-empty").assertIsDisplayed()
            node("coverage-width").performTextReplacement("1")
            node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
            generate()
            node("coverage-headland-result").assertTextEquals("Reserved headland: 0.50 m")
            node("coverage-diagram").assertIsDisplayed()
            capture(if (index == 0) "phase77-triangle.png" else "phase77-concave.png")
        }
        node("coverage-headland").apply { performTextReplacement("101"); performImeAction() }
        node("coverage-generate").assertIsNotEnabled()
        node("coverage-input-error").assertTextEquals("Enter a headland width from 0 to 100 metres.")
        assertTrue(actions.isEmpty())
    }

    @Test fun compareDirectionsShowsIndependentCardsAndClearsAfterChanges() {
        val metres = 6_371_000.0
        fun field(xy: List<Pair<Double, Double>>) = xy.mapIndexed { i, (x, y) -> DraftWaypoint("d$i", Math.toDegrees(y / metres), Math.toDegrees(x / metres)) }
        val draft = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))
        show(draft = draft)
        node("coverage-compare").assertIsNotEnabled()
        node("coverage-width").performTextReplacement("2")
        node("coverage-overlap").apply { performTextReplacement("0"); performImeAction() }
        generate()
        node("coverage-compare").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
        node("coverage-comparison-warning").assertTextContains("Overlapping ranges do not show", substring = true)
        node("coverage-comparison-north-passes").assertTextEquals("5 working passes")
        node("coverage-comparison-north-length").assertTextEquals("Working length: 100.0 m")
        node("coverage-comparison-north-range").assertTextContains("Modeled unplanned range:", substring = true)
        node("coverage-comparison-east-passes").assertTextEquals("10 working passes")
        node("coverage-comparison-east-length").assertTextEquals("Working length: 100.0 m")
        node("coverage-comparison-east-range").assertTextContains("Modeled unplanned range:", substring = true)
        capture("phase80-metrics.png")
        node("coverage-comparison-east-diagram").assertIsDisplayed()
        capture("phase80-comparison.png")
        node("coverage-comparison-north-map").performClick()
        node("coverage-map-status").assertTextContains("North / south preview selected", substring = true)
        node("coverage-comparison-east-map").performClick()
        node("coverage-map-status").assertTextContains("East / west preview selected", substring = true)
        node("coverage-map-hide").performClick()
        compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
        node("coverage-comparison-north-map").performClick()
        node("coverage-map-status").assertTextContains("North / south preview selected", substring = true)
        node("coverage-pass-count").assertTextEquals("5 working passes · preview only")
        node("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
        compose.onNodeWithTag("coverage-close-settings").performClick()
        node("coverage-compare").assertIsEnabled()
        compose.onAllNodesWithTag("coverage-map-status").assertCountEquals(0)
        compose.onAllNodesWithTag("coverage-comparison").assertCountEquals(0)
        node("coverage-empty").assertIsDisplayed()
        val shapes = listOf(coverageTestOutline(true), field(listOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0)))
        for (shape in shapes) {
            compose.runOnIdle { draft.value = MissionDraft(keepInFence = shape) }
            node("coverage-width").performTextReplacement("1")
            node("coverage-headland").apply { performTextReplacement("0.5"); performImeAction() }
            compose.onNodeWithTag("coverage-close-settings").performClick()
            node("coverage-compare").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("coverage-comparison").fetchSemanticsNodes().isNotEmpty() }
            node("coverage-comparison-north-passes").assertIsDisplayed()
            node("coverage-comparison-east-passes").assertIsDisplayed()
            node("coverage-comparison-east-map").performClick()
            node("coverage-map-status").assertTextContains("East / west preview selected", substring = true)
        }
        assertTrue(actions.isEmpty())
    }

    @Test fun comparisonShowsIndependentDirectionErrors() {
        compose.setContent {
            MamaGcsTheme {
                FieldCoverageComparisonPanel(CoverageDirectionComparison(
                    CoverageCalculation.Invalid("North problem"), CoverageCalculation.Invalid("East problem")))
            }
        }
        compose.onNodeWithTag("coverage-comparison-north-error").performScrollTo().assertTextEquals("Unavailable: North problem")
        compose.onNodeWithTag("coverage-comparison-east-error").performScrollTo().assertTextEquals("Unavailable: East problem")
        assertTrue(actions.isEmpty())
    }

    private fun show(largeText: Boolean = false, draft: MutableState<MissionDraft> = mutableStateOf(MissionDraft(keepInFence = coverageTestOutline()))) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (largeText) it.window.setLayout((320 * it.resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
        }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (largeText) 2f else 1f)) {
                MamaGcsTheme {
                    Box(if (largeText) Modifier.width(320.dp) else Modifier) {
                        MissionScreen(VehicleState(), MissionPlanUiState(draft = draft.value, loading = false), actions::add, mapStyleUrl = null)
                    }
                }
            }
        }
        compose.onNodeWithTag("mission-tab-Coverage").performScrollTo().performClick()
    }

    private fun node(tag: String): SemanticsNodeInteraction {
        if (tag in setOf("coverage-width", "coverage-overlap", "coverage-headland", "coverage-generate", "coverage-input-error", "coverage-east-west", "coverage-north-south")) {
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

    private fun generate(expectError: Boolean = false) {
        node("coverage-generate").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        node(if (expectError) "coverage-error" else "coverage-pass-count").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.awaitScreenshotFrame(compose.activity.window.decorView)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
