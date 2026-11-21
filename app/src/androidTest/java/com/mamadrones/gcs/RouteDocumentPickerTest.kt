package com.mamadrones.gcs

import android.os.SystemClock
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
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
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** English Android DocumentsUI acceptance. Run on a disposable emulator, not a user's phone.
 * Uses the real system picker and local Downloads provider; no mocked activity results or links.
 */
class RouteDocumentPickerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val codec = MissionDraftRouteCodec(MissionDraftGeoJsonCodec(), MissionDraftGpxCodec())

    @Test fun createFieldPreviewSaveExportAndReimportLocalRoute() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val name = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        val file = "$name.geojson"
        // Keep precision beyond seven decimals through UI save, DocumentsUI export and reimport.
        val points = listOf(-35.36326012345679 to 149.16523098765432, -35.36330098765432 to 149.16527012345679)
        val outline = listOf(
            -35.36335012345678 to 149.16520098765432,
            -35.36335098765432 to 149.16535012345678,
            -35.36320012345678 to 149.16535098765432,
        )
        assertEquals("", shell("test -e ${path(file)} && echo exists"))
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { repository.save(MissionDraft()) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            action("Rename")
            compose.onNodeWithText("Draft name").performTextReplacement(name)
            compose.onNodeWithText("Apply").performClick()
            fun addCoordinate(tab: String, tag: String, coordinate: Pair<Double, Double>) {
                compose.selectMissionTab(tab)
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).performClick()
                compose.onNodeWithTag("mission-latitude").performTextReplacement(coordinate.first.toString())
                compose.onNodeWithTag("mission-longitude").performTextReplacement(coordinate.second.toString())
                compose.onNodeWithTag("mission-confirm-waypoint").performClick()
            }
            points.forEach { addCoordinate("Route", "mission-add", it) }
            outline.forEach { addCoordinate("Field outline", "mission-add-fence-vertex", it) }
            compose.selectMissionTab("Coverage")
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("coverage-settings"))
            compose.onNodeWithTag("coverage-settings").performClick()
            compose.onNodeWithTag("coverage-width").performTextReplacement("2")
            compose.onNodeWithTag("coverage-overlap").apply { performTextReplacement("10"); performImeAction() }
            compose.onNodeWithTag("coverage-generate").assertIsEnabled().performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("coverage-settings") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("coverage-diagram"))
            compose.onNodeWithTag("coverage-diagram").assertIsDisplayed()
            val screenshot = compose.awaitSystemScreenshot()
            try {
                File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "phase150-coverage.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("coverage-options-open"))
            compose.onNodeWithTag("coverage-options-open").performClick()
            compose.onNodeWithTag("coverage-options-width-a").performScrollTo().performTextReplacement("2")
            compose.onNodeWithTag("coverage-options-overlap-a").performScrollTo().performTextReplacement("10")
            compose.onNodeWithTag("coverage-options-width-b").performScrollTo().performTextReplacement("1.25")
            compose.onNodeWithTag("coverage-options-overlap-b").performScrollTo().apply { performTextReplacement("25"); performImeAction() }
            compose.onNodeWithTag("coverage-options-compare").assertIsEnabled().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("coverage-option-a").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("coverage-option-a"))
            compose.onNode(hasText("Tool 2.0 m · Overlap 10.0%") and hasAnyAncestor(hasTestTag("coverage-option-a"))).assertExists()
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("coverage-option-b"))
            compose.onNode(hasText("Tool 1.25 m · Overlap 25.0%") and hasAnyAncestor(hasTestTag("coverage-option-b"))).assertExists()
            compose.onNodeWithTag("mission-save").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { runBlocking { repository.load().let { it.name == name && it.waypoints.size == 2 && it.keepInFence.size == 3 } } }
            val saved = runBlocking { repository.load() }
            assertEquals(points, saved.waypoints.map { it.latitude to it.longitude })
            assertEquals(outline, saved.keepInFence.map { it.latitude to it.longitude })

            action("Export GeoJSON")
            downloads()
            awaitNative { it.className?.toString() == "android.widget.EditText" && it.text?.toString() == file }
            clickNative { it.text?.toString()?.equals("Save", ignoreCase = true) == true }
            awaitApp()
            var content = ""
            awaitCondition("exported test route") { content = shell("cat ${path(file)} 2>/dev/null"); content.isNotBlank() }
            val exported = codec.decode(content)
            assertEquals(points, exported.waypoints.map { it.latitude to it.longitude })
            assertEquals(outline, exported.keepInFence.map { it.latitude to it.longitude })

            action("New draft")
            compose.onNode(hasText("New draft") and hasAnyAncestor(isDialog())).performClick()
            assertEquals(saved, runBlocking { repository.load() })
            importFile(file)
            compose.onNodeWithText("Replace working draft").performClick()
            assertEquals(saved, runBlocking { repository.load() })
            compose.onNodeWithTag("mission-save").assertIsEnabled().performClick()
            compose.waitUntil(15_000) {
                runBlocking { repository.load().let { it.name == name && it.waypoints.size == 2 && it.keepInFence.size == 3 } } &&
                    compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty()
            }
            val imported = runBlocking { repository.load() }
            assertEquals(points, imported.waypoints.map { it.latitude to it.longitude })
            assertEquals(outline, imported.keepInFence.map { it.latitude to it.longitude })
        } finally {
            scenario?.close()
            runBlocking { repository.save(previous); if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery) }
            shell("rm -f ${path(file)}")
        }
        assertEquals(previous, runBlocking { repository.load() })
        assertEquals(recovery, runBlocking { repository.loadRecovery() })
    }

    @Test fun invalidFileThroughPickerPreservesSavedAndWorkingDraft() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val file = "qa_route_" + UUID.randomUUID().toString().replace("-", "") + ".geojson"
        val route = MissionDraft("Invalid import test", listOf(DraftWaypoint("p", -35.36, 149.16)))
        val resolver = instrumentation.targetContext.contentResolver
        var fileUri: android.net.Uri? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file)
                put(MediaStore.Downloads.MIME_TYPE, "application/geo+json")
            }
            val created = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            fileUri = created
            requireNotNull(resolver.openOutputStream(created)).bufferedWriter().use { it.write("not a route") }
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
            compose.onNodeWithTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.365")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()

            action("Import route file")
            downloads()
            clickNative { it.text?.toString() == file }
            awaitApp()
            val error = "Unsupported route file. Choose a GeoJSON or GPX file."
            compose.waitUntil(15_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(error))
            compose.onNodeWithText(error).assertIsDisplayed()
            compose.onNodeWithText("Import route preview").assertDoesNotExist()
            assertEquals(route, runBlocking { repository.load() })
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
            compose.onNodeWithTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").assertTextContains("-35.365", substring = true)
            compose.onNodeWithText("Cancel").performClick()
        } finally {
            scenario?.close()
            runBlocking { repository.save(previous); recovery?.let { repository.saveRecovery(it) } }
            fileUri?.let { resolver.delete(it, null, null) }
        }
    }

    @Test fun rejectedGpxImportsPreserveSavedAndWorkingDraft() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val route = MissionDraft("Rejected GPX test", listOf(DraftWaypoint("p", -35.36, 149.16)))
        val prefix = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        val namespace = "http://www.topografix.com/GPX/1/1"
        val cases = listOf(
            Triple("${prefix}a.gpx",
                """<gpx version="1.1" xmlns="$namespace"><rte><rtept lat="invalid" lon="149.16"/></rte></gpx>""",
                "GPX route point has no valid latitude"),
            Triple("${prefix}b.gpx",
                buildString {
                    append("<gpx version=\"1.1\" xmlns=\"$namespace\"><rte>")
                    repeat(MissionDraft.MAX_WAYPOINTS + 1) { append("<rtept lat=\"-35.36\" lon=\"149.16\"/>") }
                    append("</rte></gpx>")
                },
                "Route has too many waypoints"),
        )
        val resolver = instrumentation.targetContext.contentResolver
        val fileUris = mutableListOf<android.net.Uri>()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            cases.forEach { (file, content, _) ->
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file)
                    put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                fileUris += uri
                requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) }
            }
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
            compose.onNodeWithTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.365")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()

            cases.forEach { (file, _, error) ->
                action("Import route file")
                downloads()
                clickNative { it.text?.toString() == file }
                awaitApp()
                compose.waitUntil(15_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(error))
                compose.onNodeWithText(error).assertIsDisplayed()
                compose.onNodeWithText("Import route preview").assertDoesNotExist()
                assertEquals(route, runBlocking { repository.load() })
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
                compose.onNodeWithTag("mission-edit-0").performClick()
                compose.onNodeWithTag("mission-latitude").assertTextContains("-35.365", substring = true)
                compose.onNodeWithText("Cancel").performClick()
            }
        } finally {
            scenario?.close()
            runBlocking {
                repository.save(previous)
                if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery)
            }
            fileUris.forEach { resolver.delete(it, null, null) }
        }
        assertEquals(previous, runBlocking { repository.load() })
        assertEquals(recovery, runBlocking { repository.loadRecovery() })
    }

    @Test fun overLimitGeoJsonImportsPreserveSavedAndWorkingDraft() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val route = MissionDraft("Rejected GeoJSON test", listOf(DraftWaypoint("p", -35.36, 149.16)))
        val prefix = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        val pointFeatures = JSONArray().apply {
            repeat(MissionDraft.MAX_WAYPOINTS + 1) {
                put(JSONObject().put("type", "Feature").put("geometry", JSONObject()
                    .put("type", "Point").put("coordinates", JSONArray().put(149.16).put(-35.36))))
            }
        }
        val ring = JSONArray().apply {
            repeat(MissionDraft.MAX_FENCE_VERTICES + 1) { index ->
                val angle = 2 * Math.PI * index / (MissionDraft.MAX_FENCE_VERTICES + 1)
                put(JSONArray().put(149.16 + 0.0001 * Math.cos(angle))
                    .put(-35.36 + 0.0001 * Math.sin(angle)))
            }
            put(getJSONArray(0)) // Close the otherwise valid 65-vertex ring.
        }
        val polygon = JSONObject().put("type", "Feature")
            .put("properties", JSONObject().put("role", "keep-in-fence"))
            .put("geometry", JSONObject().put("type", "Polygon")
                .put("coordinates", JSONArray().put(ring)))
        val cases = listOf(
            Triple("${prefix}a.geojson", JSONObject().put("type", "FeatureCollection")
                .put("features", pointFeatures).toString(), "Route has too many waypoints"),
            Triple("${prefix}b.geojson", JSONObject().put("type", "FeatureCollection")
                .put("features", JSONArray().put(polygon)).toString(),
                "Keep-in outline must have 3 to ${MissionDraft.MAX_FENCE_VERTICES} vertices"),
        )
        val resolver = instrumentation.targetContext.contentResolver
        val fileUris = mutableListOf<android.net.Uri>()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            cases.forEach { (file, content, _) ->
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file)
                    put(MediaStore.Downloads.MIME_TYPE, "application/geo+json")
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                fileUris += uri
                requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) }
            }
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
            compose.onNodeWithTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.365")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()

            cases.forEach { (file, _, error) ->
                action("Import route file")
                downloads()
                clickNative { it.text?.toString() == file }
                awaitApp()
                compose.waitUntil(15_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(error))
                compose.onNodeWithText(error).assertIsDisplayed()
                compose.onNodeWithText("Import route preview").assertDoesNotExist()
                assertEquals(route, runBlocking { repository.load() })
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
                compose.onNodeWithTag("mission-edit-0").performClick()
                compose.onNodeWithTag("mission-latitude").assertTextContains("-35.365", substring = true)
                compose.onNodeWithText("Cancel").performClick()
            }
        } finally {
            scenario?.close()
            runBlocking {
                repository.save(previous)
                if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery)
            }
            fileUris.forEach { resolver.delete(it, null, null) }
        }
        assertEquals(previous, runBlocking { repository.load() })
        assertEquals(recovery, runBlocking { repository.loadRecovery() })
    }

    @Test fun maximumGeoJsonRouteImportsAndSavesExactly() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val savedBefore = MissionDraft("Before maximum import", listOf(DraftWaypoint("p", -35.36, 149.16)))
        val name = "Maximum GeoJSON route"
        val file = "qa_route_" + UUID.randomUUID().toString().replace("-", "") + ".geojson"
        val points = (0 until MissionDraft.MAX_WAYPOINTS).map { index ->
            (-35.36002 + index * 0.0000001) to (149.15998 + index * 0.0000001)
        }
        val outline = (0 until MissionDraft.MAX_FENCE_VERTICES).map { index ->
            val angle = 2 * Math.PI * index / MissionDraft.MAX_FENCE_VERTICES
            (-35.36 + 0.0002 * Math.sin(angle)) to (149.16 + 0.0002 * Math.cos(angle))
        }
        val features = JSONArray().apply {
            points.forEachIndexed { index, (latitude, longitude) ->
                put(JSONObject().put("type", "Feature")
                    .put("properties", JSONObject().put("sequence", index + 1))
                    .put("geometry", JSONObject().put("type", "Point")
                        .put("coordinates", JSONArray().put(longitude).put(latitude))))
            }
            val ring = JSONArray().apply {
                outline.forEach { (latitude, longitude) -> put(JSONArray().put(longitude).put(latitude)) }
                put(JSONArray().put(outline.first().second).put(outline.first().first))
            }
            put(JSONObject().put("type", "Feature")
                .put("properties", JSONObject().put("role", "keep-in-fence"))
                .put("geometry", JSONObject().put("type", "Polygon")
                    .put("coordinates", JSONArray().put(ring))))
        }
        val content = JSONObject().put("type", "FeatureCollection").put("name", name)
            .put("properties", JSONObject().put("format", "mama-gcs-route").put("version", 2))
            .put("features", features).toString()
        assertTrue("Boundary fixture must fit the file-size limit", content.length <= 256_000)
        val resolver = instrumentation.targetContext.contentResolver
        var fileUri: android.net.Uri? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file)
                put(MediaStore.Downloads.MIME_TYPE, "application/geo+json")
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            fileUri = uri
            requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) }
            runBlocking { repository.save(savedBefore) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }

            action("Import route file")
            downloads()
            clickNative { it.text?.toString() == file }
            awaitApp()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Import route preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("250 waypoints", substring = true).assertIsDisplayed()
            assertEquals(savedBefore, runBlocking { repository.load() })
            compose.onNodeWithText("Replace working draft").performClick()
            assertEquals(savedBefore, runBlocking { repository.load() })
            compose.onNodeWithTag("mission-save").assertIsEnabled().performClick()
            compose.waitUntil(30_000) {
                runBlocking { repository.load().let { it.name == name && it.waypoints.size == 250 && it.keepInFence.size == 64 } }
            }
            val saved = runBlocking { repository.load() }
            assertEquals(points, saved.waypoints.map { it.latitude to it.longitude })
            assertEquals(outline, saved.keepInFence.map { it.latitude to it.longitude })
        } finally {
            scenario?.close()
            runBlocking {
                repository.save(previous)
                if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery)
            }
            fileUri?.let { resolver.delete(it, null, null) }
        }
        assertEquals(previous, runBlocking { repository.load() })
        assertEquals(recovery, runBlocking { repository.loadRecovery() })
    }

    @Test fun oversizedRouteFilesPreserveSavedAndWorkingDraft() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val route = MissionDraft("Oversized import test", listOf(DraftWaypoint("p", -35.36, 149.16)))
        val prefix = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        fun paddedFile(start: String, end: String): String =
            start + " ".repeat(256_001 - start.length - end.length) + end
        val cases = listOf(
            Triple("${prefix}a.geojson", "application/geo+json", paddedFile(
                "{\"type\":\"FeatureCollection\",\"features\":[],\"padding\":\"", "\"}")),
            Triple("${prefix}b.gpx", "application/gpx+xml", paddedFile(
                "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><rte><!--",
                "--></rte></gpx>")),
        )
        cases.forEach { (_, _, content) -> assertEquals(256_001, content.length) }
        val resolver = instrumentation.targetContext.contentResolver
        val fileUris = mutableListOf<android.net.Uri>()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            cases.forEach { (file, mimeType, content) ->
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                fileUris += uri
                requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) }
            }
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("mission-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
            compose.onNodeWithTag("mission-edit-0").performClick()
            compose.onNodeWithTag("mission-latitude").performTextReplacement("-35.365")
            compose.onNodeWithTag("mission-confirm-waypoint").performClick()

            cases.forEach { (file, _, _) ->
                action("Import route file")
                downloads()
                clickNative { it.text?.toString() == file }
                awaitApp()
                val error = "Route file exceeds the import size limit"
                compose.waitUntil(15_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(error))
                compose.onNodeWithText(error).assertIsDisplayed()
                compose.onNodeWithText("Import route preview").assertDoesNotExist()
                assertEquals(route, runBlocking { repository.load() })
                compose.onNodeWithTag("mission-editor").performScrollToNode(hasTestTag("mission-edit-0"))
                compose.onNodeWithTag("mission-edit-0").performClick()
                compose.onNodeWithTag("mission-latitude").assertTextContains("-35.365", substring = true)
                compose.onNodeWithText("Cancel").performClick()
            }
        } finally {
            scenario?.close()
            runBlocking {
                repository.save(previous)
                if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery)
            }
            fileUris.forEach { resolver.delete(it, null, null) }
        }
        assertEquals(previous, runBlocking { repository.load() })
        assertEquals(recovery, runBlocking { repository.loadRecovery() })
    }

    @Test fun exportBothFormatsAndImportThroughSystemPickerWithCancellation() {
        val repository = LocalMissionDraftRepository(instrumentation.targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val name = "qa_route_" + UUID.randomUUID().toString().replace("-", "")
        val route = MissionDraft(name, listOf(
            DraftWaypoint("a", -35.36326212345679, 149.16523749876543),
            DraftWaypoint("b", -35.36400098765432, 149.16600012345679)), listOf(
            DraftWaypoint("f1", -35.37, 149.16), DraftWaypoint("f2", -35.37, 149.17),
            DraftWaypoint("f3", -35.36, 149.17)))
        val files = listOf("$name.geojson", "$name.gpx")
        // Claim only fresh, exact test-generated paths for cleanup. Never enumerate/delete downloads.
        files.forEach { assertEquals("", shell("test -e ${path(it)} && echo exists")) }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitApp()
            compose.openWorkspace("mission")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty()
            }
            val gpxNotice = "GPX carries route waypoints only. Use GeoJSON to preserve the local outline."
            compose.openMissionFiles()
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(gpxNotice))
            compose.onNodeWithText(gpxNotice).assertIsDisplayed()

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
            runBlocking {
                repository.save(previous)
                if (recovery == null) repository.clearRecovery() else repository.saveRecovery(recovery)
            }
            files.forEach { shell("rm -f ${path(it)}") }
        }
    }

    private fun action(text: String) {
        compose.openMissionFiles()
        if (text != "Save draft") compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(text))
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
        acknowledgeFullscreenEducation()
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
