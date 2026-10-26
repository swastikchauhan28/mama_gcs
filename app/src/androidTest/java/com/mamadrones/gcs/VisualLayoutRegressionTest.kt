package com.mamadrones.gcs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.navigation.StationShell
import com.mamadrones.gcs.presentation.screens.RoverInstrumentBar
import com.mamadrones.gcs.presentation.screens.SettingsScreen
import com.mamadrones.gcs.presentation.screens.SettingsSection
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaColors
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Rendering and geometry regressions only; no real transport or vehicle commands. */
class VisualLayoutRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun narrowHeadingIsFullyVisibleBesideCompassAtNormalAndLargeFont() {
        val width = mutableStateOf(320.dp)
        val fontScale = mutableStateOf(1f)
        val state = mutableStateOf(VehicleState())
        var moreClicks = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                MamaGcsTheme {
                    Box(Modifier.width(width.value)) {
                        RoverInstrumentBar(state.value, onShowInstruments = { moreClicks++ })
                    }
                }
            }
        }
        for (testWidth in listOf(320.dp, 392.dp)) {
            for (scale in listOf(1f, 1.3f)) {
                for (heading in listOf(null, 359.9)) {
                    compose.runOnIdle {
                        width.value = testWidth
                        fontScale.value = scale
                        state.value = VehicleState(connectionStatus = VehicleConnectionState.CONNECTED, headingDegrees = heading)
                    }
                    val reading = compose.onNodeWithTag("rover-heading-reading").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val dial = compose.onNodeWithTag("rover-heading").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val bar = compose.onNodeWithTag("rover-instrument-bar").fetchSemanticsNode().boundsInRoot
                    assertTrue("Heading must fit before the compass", reading.right <= dial.left + 1f)
                    assertTrue("Heading must remain inside the bar", reading.left >= bar.left && reading.bottom <= bar.bottom)
                    val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag("rover-heading-reading")) and hasText("", substring = true), useUnmergedTree = true)
                    assertEquals(2, texts.fetchSemanticsNodes().size)
                    repeat(2) { index ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        texts[index].assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single()
                        val visible = texts[index].fetchSemanticsNode().boundsInRoot
                        // Paragraph geometry is fractional, while Text's measured size is integral.
                        // Check actual line extents with one physical pixel of rounding tolerance,
                        // not hasVisualOverflow, which can flag an otherwise fully visible label.
                        repeat(layout.lineCount) { line ->
                            assertFalse("Heading must not be ellipsized", layout.isLineEllipsized(line))
                            assertTrue("Heading line must fit its measured width", layout.getLineRight(line) <= layout.size.width + 1f)
                            assertTrue("Heading line must fit its measured height", layout.getLineBottom(line) <= layout.size.height + 1f)
                        }
                        assertTrue("Heading text must not be clipped horizontally", visible.width + 1f >= layout.size.width)
                        assertTrue("Heading text must not be clipped vertically", visible.height + 1f >= layout.size.height)
                    }
                    compose.onNodeWithTag("show-instruments").assertIsDisplayed().performClick()
                }
            }
        }
        compose.runOnIdle { assertEquals(8, moreClicks) }
    }

    @Test fun settingsHeaderUsesPaintedThemeBackgroundAndContrastingInk() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            MamaGcsTheme(theme.value) {
                StationShell(VehicleState(), "general", {}, {}) {
                    SettingsScreen(SettingsUiState(), onThemeSelected = {}, section = SettingsSection.GENERAL)
                }
            }
        }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.LIGHT)) {
            compose.runOnIdle { theme.value = mode }
            val colors = if (mode == ThemeMode.LIGHT) MamaColors.Light else MamaColors.Dark
            for ((label, ink) in listOf(
                "General settings" to colors.onBackground,
                "Display and measurement preferences for your rover station" to colors.onSurfaceVariant,
            )) {
                val text = compose.onNodeWithText(label).assertIsDisplayed()
                val bounds = text.fetchSemanticsNode().boundsInRoot
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                // Sample the padding next to the actual text, not a card or a theme constant.
                val background = pixels[(bounds.left - 2).toInt(), bounds.center.y.toInt()]
                assertTrue("$mode header background must be painted", closeColor(colors.background, background))
                val ratio = (maxOf(ink.luminance(), background.luminance()) + .05f) /
                    (minOf(ink.luminance(), background.luminance()) + .05f)
                assertTrue("$mode header needs at least 4.5:1 contrast", ratio >= 4.5f)
                var inkPixels = 0
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                    for (x in bounds.left.toInt() until bounds.right.toInt()) {
                        if (closeColor(pixels[x, y], ink)) inkPixels++
                    }
                }
                assertTrue("$mode must render the expected text ink, not inherit window defaults", inkPixels > 10)
            }
        }
    }

    @Test fun selectedMobileWorkspaceTabIsRevealedAfterNavigation() {
        val route = mutableStateOf("general")
        compose.setContent {
            MamaGcsTheme {
                Box(Modifier.width(360.dp)) {
                    StationShell(VehicleState(), route.value, onNavigate = { route.value = it }, onVehicleDetails = {}) {}
                }
            }
        }

        fun assertTabInsideStrip(tab: String) {
            compose.waitForIdle()
            val strip = compose.onNodeWithTag("category-strip").fetchSemanticsNode().boundsInRoot
            val selected = compose.onNodeWithTag("category-$tab").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tab must start inside the mobile tab strip", selected.left >= strip.left - 1f)
            assertTrue("$tab must end inside the mobile tab strip", selected.right <= strip.right + 1f)
        }

        compose.onNodeWithTag("station-menu").performClick()
        compose.onNodeWithTag("menu-admin").performClick()
        assertTabInsideStrip("admin")

        compose.runOnIdle { route.value = "rc-pwm" }
        assertTabInsideStrip("rc-pwm")

        // Scrolling an unselected tab remains available after automatic navigation.
        compose.onNodeWithTag("category-telemetry").performScrollTo().assertIsDisplayed()
    }

    @Test fun mobileHeaderShowsFullWorkspaceTitleAboveConnectionShortcut() {
        val route = mutableStateOf("diagnostics")
        val width = mutableStateOf(320.dp)
        val fontScale = mutableStateOf(1.3f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                MamaGcsTheme {
                    Box(Modifier.width(width.value)) {
                        StationShell(VehicleState(), route.value, onNavigate = { route.value = it }, onVehicleDetails = {}) {}
                    }
                }
            }
        }

        fun assertReadableTitle(expected: String) {
            val title = compose.onNodeWithTag("workspace-title").assertIsDisplayed()
            title.assertTextEquals(expected)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Workspace title must have at least one line", layouts.single().lineCount > 0)
            repeat(layouts.single().lineCount) { line ->
                assertFalse("Workspace title must not be ellipsized", layouts.single().isLineEllipsized(line))
            }
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val shortcut = compose.onNodeWithTag("connection-shortcut").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Connection shortcut must be below the mobile title", shortcut.top >= titleBounds.bottom - 1f)
        }

        assertReadableTitle("Analyze telemetry")
        compose.onNodeWithTag("connection-shortcut").performClick()
        compose.runOnIdle { assertEquals("settings", route.value) }
        assertReadableTitle("Application settings")

        compose.runOnIdle {
            width.value = 360.dp
            fontScale.value = 1f
            route.value = "more"
        }
        assertReadableTitle("Rover configuration")
    }

    private fun closeColor(a: Color, b: Color) = abs(a.red - b.red) < .02f &&
        abs(a.green - b.green) < .02f && abs(a.blue - b.blue) < .02f
}
