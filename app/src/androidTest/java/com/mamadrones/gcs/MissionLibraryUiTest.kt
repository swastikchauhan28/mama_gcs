package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real navigation and DataStore, with test-owned library copies only. Emulator only. */
class MissionLibraryUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun libraryCopyOpenDeleteAndNewDraftRespectConfirmationAndExplicitSave() {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.load() }
        val recovery = runBlocking { repository.loadRecovery() }
        val initialLibrary = runBlocking { repository.loadLibrary() }
        check(initialLibrary.size < MissionLibraryEntry.MAX_ENTRIES) { "Use an emulator with a free library slot" }
        val name = "qa_library_${UUID.randomUUID()}"
        val route = MissionDraft("Original draft", listOf(
            DraftWaypoint("a", -35.363, 149.165), DraftWaypoint("b", -35.364, 149.166)))
        var scenario: ActivityScenario<MainActivity>? = null
        fun saved() = runBlocking { repository.load() }
        fun library() = runBlocking { repository.loadLibrary() }
        try {
            runBlocking { repository.save(route) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.openWorkspace("mission")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("No unsaved changes").fetchSemanticsNodes().isNotEmpty() }
            action("Save to library")
            compose.onNode(hasText("Unique route name") and hasSetTextAction()).performTextReplacement(name)
            compose.onNodeWithText("Save copy").performClick()
            compose.waitUntil(10_000) { library().size == initialLibrary.size + 1 }
            val entry = library().single { it.draft.name == name }
            assertEquals(route.copy(name = name), entry.draft)
            assertEquals(route, saved())
            compose.onNodeWithTag("mission-save").assertIsNotEnabled()

            // The real store rejects a duplicate even with different case and surrounding spaces.
            action("Save to library")
            compose.onNode(hasText("Unique route name") and hasSetTextAction()).performTextReplacement(" ${name.uppercase()} ")
            compose.onNodeWithText("Save copy").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("A route with this name is already in the library.", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(initialLibrary.size + 1, library().size)
            assertEquals(route, saved())

            action("Rename")
            compose.onNode(hasText("Draft name") and hasSetTextAction()).performTextReplacement("Working edit")
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.loadRecovery()?.name == "Working edit" } }
            entryAction(entry.id, "Open")
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("Working edit", runBlocking { repository.loadRecovery()?.name })
            assertEquals(route, saved())
            entryAction(entry.id, "Open")
            compose.onNodeWithText("Open as draft").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.loadRecovery() == entry.draft } }
            assertEquals(route, saved())
            action("Save draft")
            compose.waitUntil(10_000) { saved() == entry.draft }

            entryAction(entry.id, "Delete")
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(library().any { it.id == entry.id })
            entryAction(entry.id, "Delete")
            compose.onNodeWithText("Delete route").performClick()
            compose.waitUntil(10_000) { library().none { it.id == entry.id } }
            assertEquals(entry.draft, saved())
            assertEquals(initialLibrary, library())

            action("New draft")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("mission-save").assertIsNotEnabled()
            action("New draft")
            compose.onNode(hasText("New draft") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { runBlocking { repository.loadRecovery() == MissionDraft() } }
            assertEquals(entry.draft, saved())
            action("Save draft")
            compose.waitUntil(10_000) { saved() == MissionDraft() }
        } finally {
            scenario?.close()
            runBlocking {
                repository.loadLibrary().filter { it.draft.name.equals(name, ignoreCase = true) }
                    .forEach { repository.deleteFromLibrary(it.id) }
                repository.save(previous)
                recovery?.let { repository.saveRecovery(it) }
            }
        }
    }

    private fun action(text: String) {
        if (text != "Save draft") {
            compose.openMissionFiles()
            compose.onNodeWithTag("mission-editor").performScrollToNode(hasText(text))
        }
        compose.onNodeWithText(text).assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    private fun entryAction(id: String, action: String) {
        compose.onNodeWithTag("mission-editor").performScrollToIndex(0)
        compose.openMissionFiles()
        compose.onNodeWithText("Library ·", substring = true).performClick()
        compose.onNode(hasText(action) and hasAnyAncestor(hasTestTag("library-entry-$id")))
            .performScrollTo().performClick()
    }
}
