package com.mamadrones.gcs

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.*
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.domain.repository.*
import com.mamadrones.gcs.presentation.mission.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real coordinator with fault-injected storage, without touching user routes. */
class MissionWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stores = mutableListOf<ViewModelStore>()
    private val codec = MissionDraftRouteCodec(MissionDraftGeoJsonCodec(), MissionDraftGpxCodec())
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun model(repository: MemoryStore): MissionPlanViewModel {
        lateinit var model: MissionPlanViewModel
        main {
            model = MissionPlanViewModel(repository, codec)
            stores += ViewModelStore().apply { put("test", model) }
        }
        return model
    }
    private fun MissionPlanViewModel.act(action: MissionPlanAction) = main { dispatch(action) }
    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }
    @After fun cleanup() { main { stores.forEach { it.clear() } } }

    @Test fun allEditorActionsPreserveOrderAndSaveExplicitly() {
        val storage = MemoryStore()
        val vm = model(storage)
        vm.act(MissionPlanAction.Add(-35.0, 149.0))
        vm.act(MissionPlanAction.Add(-35.001, 149.001))
        val first = vm.state.value.draft.waypoints.first()
        val second = vm.state.value.draft.waypoints.last()
        vm.act(MissionPlanAction.Move(second.id, -1))
        vm.act(MissionPlanAction.Edit(first.copy(latitude = -35.002)))
        vm.act(MissionPlanAction.Rename("  QA route  "))
        vm.act(MissionPlanAction.AddFenceVertex(-35.01, 149.0))
        val vertex = vm.state.value.draft.keepInFence.single()
        vm.act(MissionPlanAction.RemoveFenceVertex(vertex.id))
        assertTrue(vm.state.value.draft.keepInFence.isEmpty())
        vm.act(MissionPlanAction.AddFenceVertex(-35.01, 149.0))
        vm.act(MissionPlanAction.ClearFence)
        vm.act(MissionPlanAction.Remove(second.id))
        assertEquals(-35.002, vm.state.value.draft.waypoints.single().latitude, 0.0)
        assertEquals("QA route", vm.state.value.draft.name)
        assertEquals(MissionDraft(), storage.saved)
        assertTrue(vm.state.value.dirty)
        vm.act(MissionPlanAction.Save)
        await { !vm.state.value.saving }
        assertEquals(vm.state.value.draft, storage.saved)
        assertFalse(vm.state.value.dirty)
        assertNull(storage.recovery)
        vm.act(MissionPlanAction.New)
        assertEquals(MissionDraft(), vm.state.value.draft)
        assertEquals("QA route", storage.saved.name)
    }

    @Test fun invalidEditsDoNotMutateDraft() {
        val vm = model(MemoryStore())
        listOf(MissionPlanAction.Add(91.0, 0.0), MissionPlanAction.Add(0.0, Double.NaN),
            MissionPlanAction.Rename(" "), MissionPlanAction.AddFenceVertex(0.0, 181.0)).forEach {
            vm.act(it)
            assertEquals(MissionDraft(), vm.state.value.draft)
            assertNotNull(vm.state.value.error)
        }
    }

    @Test fun importPreviewCancelConfirmAndBothExports() {
        val storage = MemoryStore()
        val vm = model(storage)
        val imported = MissionDraft("QA & field", listOf(DraftWaypoint("p", -35.0, 149.0)))
        val content = codec.encode(imported, MissionDraftFileFormat.GEOJSON)
        vm.act(MissionPlanAction.ImportContent(content))
        assertEquals(MissionDraft(), vm.state.value.draft)
        assertEquals(imported.name, vm.state.value.pendingImport!!.name)
        vm.act(MissionPlanAction.CancelImport)
        assertNull(vm.state.value.pendingImport)
        vm.act(MissionPlanAction.ImportContent(content))
        vm.act(MissionPlanAction.ConfirmImport)
        assertEquals(imported.name, vm.state.value.draft.name)
        assertEquals(MissionDraft(), storage.saved)
        MissionDraftFileFormat.entries.forEach { format ->
            vm.act(MissionPlanAction.Export(format))
            assertEquals(format, vm.state.value.exportFormat)
            assertTrue(vm.state.value.exportFileName!!.endsWith(".${format.extension}"))
            assertEquals(imported.name, codec.decode(vm.state.value.exportContent!!).name)
            vm.act(MissionPlanAction.ExportFinished())
            assertNull(vm.state.value.exportContent)
        }
        val before = vm.state.value.draft
        vm.act(MissionPlanAction.ImportContent("not a route"))
        assertEquals(before, vm.state.value.draft)
        assertNotNull(vm.state.value.error)
        vm.act(MissionPlanAction.ImportFailed("Provider unavailable"))
        assertEquals("Provider unavailable", vm.state.value.error)
        vm.act(MissionPlanAction.ExportFinished("Disk full"))
        assertEquals("Disk full", vm.state.value.error)
    }

    @Test fun libraryActionsKeepSavedDraftIndependent() {
        val storage = MemoryStore()
        val vm = model(storage)
        vm.act(MissionPlanAction.Add(-35.0, 149.0))
        vm.act(MissionPlanAction.SaveToLibrary("QA copy"))
        await { vm.state.value.library.size == 1 }
        val entry = vm.state.value.library.single()
        vm.act(MissionPlanAction.New)
        vm.act(MissionPlanAction.OpenLibraryEntry(entry.id))
        assertEquals(entry.draft, vm.state.value.draft)
        assertEquals(MissionDraft(), storage.saved)
        vm.act(MissionPlanAction.DeleteLibraryEntry(entry.id))
        await { vm.state.value.library.isEmpty() }
        assertEquals(entry.draft, vm.state.value.draft)
    }

    @Test fun loadFailureBlocksEditingAndRetryRecovers() {
        val storage = MemoryStore().apply { failLoad = true }
        val vm = model(storage)
        await { vm.state.value.loadFailed }
        vm.act(MissionPlanAction.Add(0.0, 0.0))
        assertFalse(vm.state.value.editable)
        assertTrue(vm.state.value.draft.waypoints.isEmpty())
        storage.failLoad = false
        vm.act(MissionPlanAction.RetryLoad)
        await { vm.state.value.editable }
        assertNull(vm.state.value.error)
    }

    @Test fun corruptRecoveryAndLibraryDoNotEraseCommittedDraft() {
        val storage = MemoryStore().apply {
            saved = MissionDraft("Important saved route")
            failRecoveryLoad = true
            failLibrary = true
        }
        val vm = model(storage)
        assertEquals(storage.saved, vm.state.value.draft)
        assertNotNull(vm.state.value.error)
        assertNotNull(vm.state.value.libraryError)
        assertTrue(vm.state.value.editable)
    }

    @Test fun failedSaveRetainsDirtyDraftAndAllowsRetry() {
        val storage = MemoryStore().apply { failSave = true }
        val vm = model(storage)
        vm.act(MissionPlanAction.Rename("Unsaved route"))
        vm.act(MissionPlanAction.Save)
        await { !vm.state.value.saving }
        assertTrue(vm.state.value.dirty)
        assertNotNull(vm.state.value.error)
        assertEquals(MissionDraft(), storage.saved)
        storage.failSave = false
        vm.act(MissionPlanAction.Save)
        await { !vm.state.value.saving }
        assertEquals("Unsaved route", storage.saved.name)
        assertFalse(vm.state.value.dirty)
    }

    @Test fun undoToSavedDraftMustNotResurrectOldRecoveryOnReopen() {
        val storage = MemoryStore()
        val vm = model(storage)
        vm.act(MissionPlanAction.Rename("Temporary edit"))
        await { vm.state.value.recoverySaved }
        vm.act(MissionPlanAction.Rename(storage.saved.name))
        assertFalse(vm.state.value.dirty)
        // Give the real recovery debounce time to settle before recreating the coordinator.
        runBlocking { delay(600) }
        val reopened = model(storage)
        assertEquals("Reopening must retain the latest editor state", storage.saved, reopened.state.value.draft)
        assertFalse(reopened.state.value.recovered)
    }

    @Test fun newImportAndLibraryRestoreClearObsoleteRecovery() {
        listOf("new", "import", "library").forEach { source ->
            val storage = MemoryStore().apply {
                entries = listOf(MissionLibraryEntry("saved-copy", saved, 1))
            }
            val vm = model(storage)
            vm.act(MissionPlanAction.Rename("Discard this"))
            await { vm.state.value.recoverySaved }
            when (source) {
                "new" -> vm.act(MissionPlanAction.New)
                "import" -> {
                    vm.act(MissionPlanAction.ImportContent(codec.encode(storage.saved, MissionDraftFileFormat.GEOJSON)))
                    vm.act(MissionPlanAction.ConfirmImport)
                }
                else -> vm.act(MissionPlanAction.OpenLibraryEntry("saved-copy"))
            }
            await { storage.recovery == null }
            assertFalse(vm.state.value.dirty)
            assertEquals(storage.saved, model(storage).state.value.draft)
        }
    }

    @Test fun failedRecoveryCleanupWarnsAndExplicitSaveRetries() {
        val storage = MemoryStore()
        val vm = model(storage)
        vm.act(MissionPlanAction.Rename("Discard this"))
        await { vm.state.value.recoverySaved }
        storage.failClear = true
        vm.act(MissionPlanAction.Rename(storage.saved.name))
        await { vm.state.value.error != null }
        assertTrue(vm.state.value.error!!.contains("could not be cleared"))
        assertTrue(vm.state.value.recoveryCleanupFailed)
        assertNotNull(storage.recovery)
        vm.act(MissionPlanAction.Save)
        await { !vm.state.value.saving }
        assertNull(storage.recovery)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.recoveryCleanupFailed)
    }

    @Test fun undoWaitsForInFlightRecoveryBeforeClearingIt() {
        val storage = MemoryStore().apply { recoveryWriteDelay = 200 }
        val vm = model(storage)
        vm.act(MissionPlanAction.Rename("Slow write"))
        await { storage.recoveryWriteStarted }
        vm.act(MissionPlanAction.Rename(storage.saved.name))
        await { storage.clearCalls > 0 }
        assertNull(storage.recovery)
        assertEquals(storage.saved, model(storage).state.value.draft)
    }

    @Test fun newEditAfterUndoStillGetsItsOwnRecoveryCopy() {
        val storage = MemoryStore()
        val vm = model(storage)
        vm.act(MissionPlanAction.Rename("First edit"))
        await { vm.state.value.recoverySaved }
        vm.act(MissionPlanAction.Rename(storage.saved.name))
        vm.act(MissionPlanAction.Rename("Latest edit"))
        await { vm.state.value.recoverySaved }
        assertEquals("Latest edit", storage.recovery?.name)
        assertEquals("Latest edit", model(storage).state.value.draft.name)
    }

    private class MemoryStore : MissionDraftRepository {
        var saved = MissionDraft()
        @Volatile var recovery: MissionDraft? = null
        var entries = emptyList<MissionLibraryEntry>()
        var failLoad = false
        var failSave = false
        var failRecoveryLoad = false
        var failLibrary = false
        var failClear = false
        var recoveryWriteDelay = 0L
        @Volatile var recoveryWriteStarted = false
        @Volatile var clearCalls = 0
        override suspend fun load(): MissionDraft { check(!failLoad); return saved }
        override suspend fun save(draft: MissionDraft) { check(!failSave); saved = draft; recovery = null }
        override suspend fun loadRecovery(): MissionDraft? { check(!failRecoveryLoad); return recovery }
        override suspend fun saveRecovery(draft: MissionDraft) {
            recoveryWriteStarted = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                delay(recoveryWriteDelay)
                recovery = draft
            }
        }
        override suspend fun clearRecovery() { check(!failClear); recovery = null; clearCalls++ }
        override suspend fun loadLibrary(): List<MissionLibraryEntry> { check(!failLibrary); return entries }
        override suspend fun saveToLibrary(draft: MissionDraft): MissionLibraryEntry =
            MissionLibraryEntry("copy-${entries.size}", draft, 1).also { entries = entries + it }
        override suspend fun deleteFromLibrary(id: String) { entries = entries.filterNot { it.id == id } }
    }
}
