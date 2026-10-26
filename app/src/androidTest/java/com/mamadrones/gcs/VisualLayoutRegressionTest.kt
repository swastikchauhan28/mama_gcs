package com.mamadrones.gcs

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.mission.MissionPlanAction
import com.mamadrones.gcs.presentation.mission.MissionPlanUiState
import com.mamadrones.gcs.presentation.navigation.StationShell
import com.mamadrones.gcs.presentation.screens.MissionScreen
import com.mamadrones.gcs.presentation.screens.RoverInstrumentBar
import com.mamadrones.gcs.presentation.screens.MapWorkspace
import com.mamadrones.gcs.presentation.screens.SettingsScreen
import com.mamadrones.gcs.presentation.screens.SettingsSection
import com.mamadrones.gcs.presentation.settings.SettingsUiState
import com.mamadrones.gcs.presentation.theme.MamaColors
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
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

    @Test fun enlargedMobileCategoryFitsAndSafetyStaysReachable() {
        val route = mutableStateOf("general")
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        StationShell(VehicleState(), route.value, onNavigate = { route.value = it }, onVehicleDetails = {}) {}
                    }
                }
            }
        }

        listOf("classic-bluetooth" to "Bluetooth / HC-05", "admin" to "Accounts & audit", "rc-pwm" to "RC & PWM").forEach { (selectedRoute, title) ->
            compose.runOnIdle { route.value = selectedRoute }
            compose.waitForIdle()
            val strip = compose.onNodeWithTag("category-strip").fetchSemanticsNode().boundsInRoot
            val tab = compose.onNodeWithTag("category-$selectedRoute").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$selectedRoute must fit inside the 2× text strip", tab.left >= strip.left - 1f && tab.right <= strip.right + 1f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(title, useUnmergedTree = true).onFirst()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            repeat(layouts.single().lineCount) { line ->
                assertFalse("$title must not be ellipsized", layouts.single().isLineEllipsized(line))
            }
            compose.onNodeWithTag("exit-workspace").assertIsDisplayed()
            compose.onNodeWithTag("safety-shortcut").assertIsDisplayed()
        }
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
            val shortcut = compose.onNodeWithTag("connection-shortcut").assertIsDisplayed()
                .assertTextEquals("Disconnected · Connect").fetchSemanticsNode().boundsInRoot
            assertTrue("Connection shortcut must be below the mobile title", shortcut.top >= titleBounds.bottom - 1f)
        }

        assertReadableTitle("Analyze telemetry")
        compose.onNodeWithTag("connection-shortcut").performClick()
        compose.runOnIdle { assertEquals("settings", route.value) }
        assertReadableTitle("Application settings")

        compose.runOnIdle { fontScale.value = 2f }
        assertReadableTitle("Application settings")
        compose.onNodeWithTag("safety-shortcut").assertIsDisplayed()
        compose.runOnIdle { route.value = "more" }
        assertReadableTitle("Rover configuration")

        compose.runOnIdle {
            width.value = 360.dp
            fontScale.value = 1f
            route.value = "more"
        }
        assertReadableTitle("Rover configuration")
    }

    @Test fun enlargedOperateMapActionsKeepWholeLabelsBesideStatus() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp).height(390.dp)) {
                        MapWorkspace(VehicleState(), showOperatorTools = true)
                    }
                }
            }
        }

        listOf("Plan", "Drive", "More").forEach { label ->
            val text = compose.onNodeWithTag("map-action-$label", useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().assertTextEquals(label)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$label should occupy one line", 1, layouts.single().lineCount)
            assertFalse("$label must not be ellipsized", layouts.single().isLineEllipsized(0))
        }
        val toolbar = compose.onNodeWithTag("operation-toolbar").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("operation-status").fetchSemanticsNode().boundsInRoot
        assertFalse("Large-text toolbar must not cover the mode/arming status", toolbar.overlaps(status))
    }

    @Test fun missionHeaderActionsAndDraftNameFitAtLargeText() {
        val draftName = "Long local field route"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(
                            draft = MissionDraft(name = draftName), loading = false), onAction = {})
                    }
                }
            }
        }

        val fileButton = compose.onNodeWithTag("mission-file-menu").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val saveButton = compose.onNodeWithTag("mission-save").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(draftName, useUnmergedTree = true).assertIsDisplayed()
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        repeat(layouts.single().lineCount) { line ->
            assertFalse("Draft name must not be ellipsized at large text", layouts.single().isLineEllipsized(line))
        }
        val screenWidthPx = 320f * compose.activity.resources.displayMetrics.density
        assertFalse("File and save buttons must not overlap", fileButton.overlaps(saveButton))
        assertTrue("File action must remain on screen", fileButton.left >= 0f && fileButton.right <= screenWidthPx + 1f)
        assertTrue("Save draft must remain on screen", saveButton.left >= 0f && saveButton.right <= screenWidthPx + 1f)
        assertTrue("Draft name must remain on screen", titleBounds.left >= 0f && titleBounds.right <= screenWidthPx + 1f)
        assertTrue("Draft name must remain readable between its actions", titleBounds.width > 80f)
    }

    @Test fun waypointDialogKeepsFieldsAndActionsReachableAtLargeText() {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
        }
        val actions = mutableListOf<MissionPlanAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(loading = false), actions::add)
                    }
                }
            }
        }

        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-add"))
        compose.onNodeWithTag("mission-add").performClick()
        compose.onNodeWithText("Add waypoint").assertIsDisplayed()
        val dialog = compose.onNode(isDialog())
        val dialogBounds = dialog.fetchSemanticsNode().boundsInRoot
        val latitudeNode = compose.onNodeWithTag("mission-latitude")
        val longitudeNode = compose.onNodeWithTag("mission-longitude")
        latitudeNode.performScrollTo().assertIsDisplayed()
        val latitude = latitudeNode.fetchSemanticsNode().boundsInRoot
        longitudeNode.performScrollTo().assertIsDisplayed()
        val longitude = longitudeNode.fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("Cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("mission-confirm-waypoint").assertIsDisplayed().assertIsNotEnabled()
            .fetchSemanticsNode().boundsInRoot
        listOf("latitude" to latitude, "longitude" to longitude, "Cancel" to cancel, "Add" to add).forEach { (name, bounds) ->
            assertTrue("$name must remain inside the dialog window", bounds.left >= dialogBounds.left &&
                bounds.top >= dialogBounds.top && bounds.right <= dialogBounds.right && bounds.bottom <= dialogBounds.bottom)
        }

        val screenshot = dialog.captureToImage().asAndroidBitmap()
        val screenshotFile = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase65-waypoint-dialog.png")
        screenshotFile.outputStream().use { output ->
            assertTrue("Dialog screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }

        latitudeNode.performScrollTo().performTextReplacement("-35.36")
        longitudeNode.performScrollTo().performTextReplacement("149.16")
        compose.onNodeWithTag("mission-confirm-waypoint").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(MissionPlanAction.Add(-35.36, 149.16)), actions)
        }
        compose.onNodeWithText("Add waypoint").assertDoesNotExist()
    }

    @Test fun waypointRowActionsFitAndKeepReorderBoundariesAtLargeText() {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
        }
        val waypoints = listOf(
            DraftWaypoint("wp-1", -35.3632621, 149.1652374),
            DraftWaypoint("wp-2", -35.3635000, 149.1660000),
            DraftWaypoint("wp-3", -35.3640000, 149.1670000),
        )
        val actions = mutableListOf<MissionPlanAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(
                            draft = MissionDraft(name = "Large text route", waypoints = waypoints), loading = false), actions::add)
                    }
                }
            }
        }

        val rowZero = listOf(
            "mission-edit-0" to "Edit", "mission-up-0" to "Up",
            "mission-down-0" to "Down", "mission-remove-0" to "Remove",
        )
        rowZero.forEach { (tag, label) ->
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
            val button = compose.onNodeWithTag(tag).assertIsDisplayed().assertTextEquals(label)
            val layouts = mutableListOf<TextLayoutResult>()
            button.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$label should remain on one line", 1, layouts.single().lineCount)
            assertFalse("$label must not be ellipsized", layouts.single().isLineEllipsized(0))
            val bounds = button.fetchSemanticsNode().boundsInRoot
            assertTrue("$label must stay inside the 320 dp screen", bounds.left >= 0f && bounds.right <= screenWidthPx + 1f)
        }
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-remove-0"))
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val screenshotFile = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase66-waypoint-actions.png")
        screenshotFile.outputStream().use { output ->
            assertTrue("Waypoint actions screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }

        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-up-0"))
        compose.onNodeWithTag("mission-up-0").assertIsNotEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-down-0"))
        compose.onNodeWithTag("mission-down-0").assertIsEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-up-1"))
        compose.onNodeWithTag("mission-up-1").assertIsEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-down-1"))
        compose.onNodeWithTag("mission-down-1").assertIsEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-up-2"))
        compose.onNodeWithTag("mission-up-2").assertIsEnabled()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-down-2"))
        compose.onNodeWithTag("mission-down-2").assertIsNotEnabled()

        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
        compose.onNodeWithTag("mission-edit-0").performClick()
        compose.onNodeWithText("Edit waypoint").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-down-0"))
        compose.onNodeWithTag("mission-down-0").performClick()
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-remove-0"))
        compose.onNodeWithTag("mission-remove-0").performClick()
        compose.runOnIdle {
            assertEquals(listOf(MissionPlanAction.Move("wp-1", 1), MissionPlanAction.Remove("wp-1")), actions)
        }
    }

    @Test fun missionLibraryDialogKeepsLongRoutesAndActionsReachableAtLargeText() {
        val screenWidthPx = (320f * compose.activity.resources.displayMetrics.density).toInt()
        compose.activity.runOnUiThread {
            compose.activity.window.setLayout(screenWidthPx, WindowManager.LayoutParams.MATCH_PARENT)
        }
        val longRouteName = "Long field route name for 2x text verification"
        val library = listOf(
            MissionLibraryEntry("library-long", MissionDraft(longRouteName, listOf(
                DraftWaypoint("long-a", -35.3632621, 149.1652374),
                DraftWaypoint("long-b", -35.3635000, 149.1660000),
            )), 1L),
            MissionLibraryEntry("library-middle", MissionDraft("Regional survey outline"), 2L),
            MissionLibraryEntry("library-last", MissionDraft("Northern boundary"), 3L),
        )
        val actions = mutableListOf<MissionPlanAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MamaGcsTheme {
                    Box(Modifier.width(320.dp)) {
                        MissionScreen(VehicleState(), MissionPlanUiState(
                            draft = MissionDraft(name = "Working draft"), loading = false, library = library,
                        ), actions::add)
                    }
                }
            }
        }

        fun openLibrary() {
            compose.openMissionFiles()
            compose.onNodeWithTag("mission-editor").performScrollToIndex(0)
            compose.onNodeWithText("Library ·", substring = true).performScrollTo().assertIsDisplayed().performClick()
        }

        openLibrary()
        val dialog = compose.onNode(isDialog())
        val dialogBounds = dialog.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Local route library · 3").assertIsDisplayed()
        compose.onNodeWithTag("library-entry-library-long").performScrollTo().assertIsDisplayed()
        val longName = compose.onNodeWithText(longRouteName, useUnmergedTree = true).assertIsDisplayed()
        val longNameBounds = longName.fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        longName.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        repeat(layouts.single().lineCount) { line ->
            assertFalse("Long route name must not be ellipsized", layouts.single().isLineEllipsized(line))
        }
        assertTrue("Long route name must stay within the library dialog", longNameBounds.left >= dialogBounds.left &&
            longNameBounds.top >= dialogBounds.top && longNameBounds.right <= dialogBounds.right &&
            longNameBounds.bottom <= dialogBounds.bottom)

        val open = compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag("library-entry-library-long")))
            .performScrollTo().assertIsDisplayed()
        val delete = compose.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag("library-entry-library-long")))
            .performScrollTo().assertIsDisplayed()
        val close = compose.onNodeWithText("Close").assertIsDisplayed()
        for ((label, node) in listOf("Open" to open, "Delete" to delete, "Close" to close)) {
            val actionLayouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(actionLayouts) }
            assertEquals("$label label should remain on one line", 1, actionLayouts.single().lineCount)
            assertFalse("$label label must not be ellipsized", actionLayouts.single().isLineEllipsized(0))
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$label must remain inside the library dialog", bounds.left >= dialogBounds.left &&
                bounds.top >= dialogBounds.top && bounds.right <= dialogBounds.right && bounds.bottom <= dialogBounds.bottom)
        }
        val screenshot = dialog.captureToImage().asAndroidBitmap()
        val screenshotFile = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "phase67-route-library.png")
        screenshotFile.outputStream().use { output ->
            assertTrue("Library screenshot must be written", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }

        close.performClick()
        compose.onNodeWithText("Local route library · 3").assertDoesNotExist()
        openLibrary()
        compose.onNode(hasText("Open") and hasAnyAncestor(hasTestTag("library-entry-library-long")))
            .performScrollTo().performClick()
        compose.onNodeWithText("Open library route?").assertIsDisplayed()
        compose.onNodeWithText("Open as draft").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MissionPlanAction.OpenLibraryEntry("library-long")), actions) }

        openLibrary()
        compose.onNodeWithTag("library-entry-library-last").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag("library-entry-library-last")))
            .performScrollTo().performClick()
        compose.onNodeWithText("Delete library route?").assertIsDisplayed()
        compose.onNodeWithText("Delete route").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(
                MissionPlanAction.OpenLibraryEntry("library-long"),
                MissionPlanAction.DeleteLibraryEntry("library-last"),
            ), actions)
        }
    }

    private fun closeColor(a: Color, b: Color) = abs(a.red - b.red) < .02f &&
        abs(a.green - b.green) < .02f && abs(a.blue - b.blue) < .02f
}
