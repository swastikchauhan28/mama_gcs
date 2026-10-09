package com.mamadrones.gcs

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicBoolean

/** Native map -> real mission dialog -> ViewModel -> DataStore. No vehicle link. */
class MissionMapDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun mapSelectionAndLongPressRequireConfirmationAndExplicitSave() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val original = MissionDraft("Map dialog acceptance", listOf(
            DraftWaypoint("a", -35.3632621, 149.1652374), DraftWaypoint("b", -35.364, 149.166)))
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { repository.save(original) }
            val activity = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
            activity.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                var ready = false
                activity.onActivity { ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                ready
            }
            compose.openWorkspace("mission")
            lateinit var view: MapView
            var map: MapLibreMap? = null
            compose.waitForIdle()
            activity.onActivity {
                view = requireNotNull(findMap(it.window.decorView))
                view.getMapAsync { loaded -> map = loaded }
            }
            compose.waitUntil(45_000) {
                var ready = false
                activity.onActivity { ready = map?.style?.isFullyLoaded == true }
                ready
            }
            val loadedMap = requireNotNull(map)
            lateinit var fittedCamera: org.maplibre.android.camera.CameraPosition
            activity.onActivity {
                val padding = (48 * view.resources.displayMetrics.density).toInt()
                fittedCamera = requireNotNull(loadedMap.getCameraForLatLngBounds(
                    LatLngBounds.Builder().includes(original.waypoints.map { point ->
                        LatLng(point.latitude, point.longitude)
                    }).build(), intArrayOf(padding, padding, padding, padding)))
            }
            val idle = AtomicBoolean(false)
            val listener = MapLibreMap.OnCameraIdleListener { idle.set(true) }
            activity.onActivity { loadedMap.addOnCameraIdleListener(listener) }
            try {
                compose.onNodeWithTag("mission-fit").performClick()
                // An idle event from initial positioning can arrive after listener attachment.
                // Require the requested fit result before projecting a touch location.
                compose.waitUntil(10_000) {
                    var fitted = false
                    activity.onActivity {
                        val camera = loadedMap.cameraPosition
                        fitted = camera.target?.distanceTo(requireNotNull(fittedCamera.target))?.let { it < 0.1 } == true &&
                            kotlin.math.abs(camera.zoom - fittedCamera.zoom) < 0.0001
                    }
                    idle.get() && fitted
                }
            } finally { activity.onActivity { loadedMap.removeOnCameraIdleListener(listener) } }

            var first = Offset.Zero
            activity.onActivity {
                val point = loadedMap.projection.toScreenLocation(LatLng(original.waypoints[0].latitude, original.waypoints[0].longitude))
                first = Offset(point.x, point.y)
                val visible = android.graphics.Rect()
                assertTrue("Native map must be visible", view.isShown && view.getGlobalVisibleRect(visible))
                assertTrue("Projected waypoint $first outside map ${view.width}x${view.height}; visible=$visible",
                    first.x in 0f..view.width.toFloat() && first.y in 0f..view.height.toFloat())
                val origin = IntArray(2)
                view.getLocationOnScreen(origin)
                assertTrue("Waypoint is clipped: $first origin=${origin.toList()} visible=$visible",
                    visible.contains((origin[0] + first.x).toInt(), (origin[1] + first.y).toInt()))
            }
            gesture(view, first, 100)
            awaitDialog("Edit waypoint")
            compose.onNodeWithTag("mission-latitude").assertTextContains("-35.3632621")
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.362")
            compose.onNodeWithText("Cancel").performClick()
            compose.waitForIdle()
            assertEquals(original, runBlocking { repository.load() })
            assertNull(runBlocking { repository.loadRecovery() })

            gesture(view, first, 100)
            awaitDialog("Edit waypoint")
            compose.onNodeWithTag("mission-latitude").assertTextContains("-35.3632621")
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.362")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.loadRecovery()?.waypoints?.first()?.latitude == -35.362 } }
            val edited = requireNotNull(runBlocking { repository.loadRecovery() })
            assertEquals("a", edited.waypoints.first().id)
            assertEquals(original, runBlocking { repository.load() })

            var press = Offset.Zero
            var expected = LatLng()
            activity.onActivity {
                press = Offset(view.width * 0.28f, view.height * 0.55f)
                expected = loadedMap.projection.fromScreenLocation(android.graphics.PointF(press.x, press.y))
            }
            gesture(view, press, 1000)
            awaitDialog("Add waypoint")
            fun field(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.toDouble()
            assertEquals(expected.latitude, field("mission-latitude"), 0.000001)
            assertEquals(expected.longitude, field("mission-longitude"), 0.000001)
            compose.onNodeWithText("Cancel").performClick()
            compose.waitForIdle()
            assertEquals(edited, runBlocking { repository.loadRecovery() })
            gesture(view, press, 1000)
            awaitDialog("Add waypoint")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.loadRecovery()?.waypoints?.size == 3 } }
            assertEquals(original, runBlocking { repository.load() })
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-save"))
            compose.onNodeWithTag("mission-save").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.load().waypoints.size == 3 } }
            val saved = runBlocking { repository.load() }
            assertEquals(edited.waypoints, saved.waypoints.take(2))
            assertEquals(expected.latitude, saved.waypoints.last().latitude, 0.000001)
            assertEquals(expected.longitude, saved.waypoints.last().longitude, 0.000001)
            assertNull(runBlocking { repository.loadRecovery() })
        } finally {
            scenario?.close()
            runBlocking { repository.save(previous); recovery?.let { repository.saveRecovery(it) } }
        }
    }

    private fun awaitDialog(title: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: ComposeTimeoutException) {
            val roots = compose.onAllNodes(isRoot())
            val tree = roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
            throw AssertionError("Missing $title. UI: $tree", failure)
        }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun gesture(view: MapView, point: Offset, duration: Long) {
        val location = IntArray(2)
        // Compose can be idle before Android returns focus after a dialog closes.
        // Injecting at that point sends the tap to a different window or drops it.
        try {
            compose.waitUntil(10_000) {
                var focused = false
                instrumentation.runOnMainSync { focused = view.isShown && view.hasWindowFocus() }
                focused
            }
        } catch (failure: ComposeTimeoutException) {
            var details = ""
            instrumentation.runOnMainSync {
                details = "shown=${view.isShown}, attached=${view.isAttachedToWindow}, " +
                    "windowVisibility=${view.windowVisibility}, root=${view.rootView.javaClass.simpleName}, " +
                    "rootFocus=${view.rootView.hasWindowFocus()}, size=${view.width}x${view.height}"
            }
            throw AssertionError("Map input window unavailable: $details", failure)
        }
        instrumentation.runOnMainSync {
            assertTrue("Map window must have input focus", view.hasWindowFocus())
            view.getLocationOnScreen(location)
        }
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                point.x + location[0], point.y + location[1], 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                check(instrumentation.uiAutomation.injectInputEvent(event, action != MotionEvent.ACTION_DOWN))
            } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN)
        // A scheduled sleep can overshoot under emulator load and turn a tap into
        // a long-press. Queue tap UP immediately; only long-presses hold DOWN.
        if (duration >= android.view.ViewConfiguration.getLongPressTimeout()) SystemClock.sleep(duration)
        send(MotionEvent.ACTION_UP)
        compose.waitForIdle()
    }
}
