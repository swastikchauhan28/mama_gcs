package com.mamadrones.gcs

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.MissionDraftGeoJsonCodec
import com.mamadrones.gcs.data.local.datastore.MissionDraftGpxCodec
import com.mamadrones.gcs.data.local.datastore.MissionDraftRouteCodec
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.os.ParcelFileDescriptor
import java.util.UUID

/** English Android DocumentsUI acceptance. Run on a disposable emulator, not a user's phone.
 * Uses the real system picker and local Downloads provider; no mocked activity results or links.
 */
class RouteDocumentPickerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val codec = MissionDraftRouteCodec(MissionDraftGeoJsonCodec(), MissionDraftGpxCodec())

    @Test fun exportBothFormatsAndImportThroughSystemPickerWithCancellation() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val name = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        val route = MissionDraft(name, listOf(
            DraftWaypoint("a", -35.3632621, 149.1652374),
            DraftWaypoint("b", -35.364, 149.166)), listOf(
            DraftWaypoint("f1", -35.37, 149.16), DraftWaypoint("f2", -35.37, 149.17),
            DraftWaypoint("f3", -35.36, 149.17)))
        val files = listOf("$name.geojson", "$name.gpx")
        // Claim only fresh, exact test-generated paths for cleanup. Never enumerate/delete downloads.
        files.forEach { assertEquals("", shell("test -e ${path(it)} && echo exists")) }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithTag("nav-mission").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty()
            }

            // Cancellation must return to the same draft and permit another export.
            action("Export GeoJSON")
            awaitNative { it.className?.toString() == "android.widget.EditText" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitApp()
            assertEquals(route, runBlocking { repository.load() })

            listOf("Export GeoJSON", "Export GPX").zip(files).forEach { (label, file) ->
                action(label)
                downloads()
                awaitNative { it.className?.toString() == "android.widget.EditText" && it.text?.toString() == file }
                clickNative { it.text?.toString()?.equals("Save", ignoreCase = true) == true }
                awaitApp()
                var content = ""
                awaitCondition("exported file $file") {
                    content = shell("cat ${path(file)} 2>/dev/null")
                    content.isNotBlank()
                }
                val decoded = codec.decode(content)
                assertEquals(route.name, decoded.name)
                assertEquals(route.waypoints.map { it.latitude to it.longitude },
                    decoded.waypoints.map { it.latitude to it.longitude })
                assertEquals(if (file.endsWith("geojson")) 3 else 0, decoded.keepInFence.size)
            }

            action("Import route file")
            awaitNative { it.packageName?.toString()?.endsWith("documentsui") == true }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitApp()
            compose.onNodeWithText("Import route preview").assertDoesNotExist()
            assertEquals(route, runBlocking { repository.load() })

            // Import preview cancellation must not replace the current editor or committed route.
            importFile(files.first())
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Import route preview").assertDoesNotExist()
            assertEquals(route, runBlocking { repository.load() })

            files.forEach { file ->
                val committedBeforeImport = runBlocking { repository.load() }
                importFile(file)
                compose.onNodeWithText("Replace working draft").performClick()
                // Imports remain local working copies until an explicit Save draft.
                assertEquals(committedBeforeImport, runBlocking { repository.load() })
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-save"))
                compose.onNodeWithTag("mission-save").assertIsEnabled().performClick()
                compose.waitUntil(15_000) {
                    val saved = runBlocking { repository.load() }
                    saved.name == name && saved.keepInFence.size == (if (file.endsWith("geojson")) 3 else 0) &&
                        compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty()
                }
                val saved = runBlocking { repository.load() }
                assertEquals(route.waypoints.map { it.latitude to it.longitude },
                    saved.waypoints.map { it.latitude to it.longitude })
            }
        } finally {
            scenario?.close()
            runBlocking { repository.save(previous); recovery?.let { repository.saveRecovery(it) } }
            files.forEach { shell("rm -f ${path(it)}") }
        }
    }

    private fun action(text: String) {
        compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsEnabled().performClick()
        // Export opens DocumentsUI from a LaunchedEffect on the next Compose frame.
        compose.waitForIdle()
    }

    private fun importFile(file: String) {
        action("Import route file")
        downloads()
        clickNative { it.text?.toString() == file }
        awaitApp()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Import route preview").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun downloads() {
        clickNative { it.contentDescription?.toString() == "Show roots" }
        clickNative { it.text?.toString() == "Downloads" }
    }

    private fun awaitApp() = awaitCondition("Mama GCS foreground") {
        automation.rootInActiveWindow?.packageName?.toString() == "com.mamadrones.gcs"
    }

    private fun clickNative(predicate: (AccessibilityNodeInfo) -> Boolean) {
        // A toolbar title may have the same text as a drawer row. Wait for the
        // actionable match rather than selecting non-interactive headings.
        // DocumentsUI can replace nodes while its drawer animates. A rejected
        // accessibility action has not clicked the control; reacquire and retry.
        awaitCondition("accepted document picker click") {
            val match = find(automation.rootInActiveWindow) {
                predicate(it) && clickableAncestor(it) != null
            }
            match?.let { clickableAncestor(it)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
        }
    }

    private fun clickableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = start
        while (node != null && !node.isClickable) node = node.parent
        return node?.takeIf { it.isEnabled }
    }

    private fun awaitNative(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var node: AccessibilityNodeInfo? = null
        awaitCondition("document picker control") {
            node = find(automation.rootInActiveWindow, predicate)
            node != null
        }
        return requireNotNull(node)
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun awaitCondition(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description; foreground=${automation.rootInActiveWindow?.packageName}")
    }

    private fun path(file: String): String {
        require(Regex("qa_route_[a-f0-9]{32}\\.(geojson|gpx)").matches(file))
        return "/sdcard/Download/$file"
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
}
