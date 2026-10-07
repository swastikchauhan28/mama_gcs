package com.mamadrones.gcs

import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real on-device storage tests. No radio/socket is opened; existing user data is retained. */
class LocalRepositoryTest {
    @Test fun udpEndpointPersistsAndCanBeClearedWithoutChangingTheme() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalSettingsRepository(context)
        val previous = repository.preferences.first()
        try {
            val endpoint = UdpEndpoint("127.0.0.1", 14551, 14550)
            repository.setUdpEndpoint(endpoint)
            assertEquals(endpoint, LocalSettingsRepository(context).preferences.first().udpEndpoint)
            assertEquals(previous.theme, repository.preferences.first().theme)
            repository.setUdpEndpoint(null)
            assertNull(LocalSettingsRepository(context).preferences.first().udpEndpoint)
            assertEquals(previous.theme, repository.preferences.first().theme)
        } finally {
            repository.setUdpEndpoint(previous.udpEndpoint)
        }
    }

    @Test fun libraryPersistsIndependentCopiesRejectsDuplicateNamesAndDeletesOnlySelectedEntry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LocalMissionDraftRepository(context)
        val priorEntries = repository.loadLibrary()
        val priorDraft = repository.load()
        val priorRecovery = repository.loadRecovery()
        val createdIds = mutableListOf<String>()
        // Never remove existing routes to make room for a test.
        org.junit.Assume.assumeTrue(priorEntries.size <= MissionLibraryEntry.MAX_ENTRIES - 2)
        try {
            val name = "QA-${UUID.randomUUID()}"
            val draft = MissionDraft(" $name ", listOf(DraftWaypoint("point", -35.3632621, 149.1652374)))
            val first = repository.saveToLibrary(draft).also { createdIds += it.id }
            assertEquals(name, first.draft.name)
            assertEquals(draft.waypoints, first.draft.waypoints)
            assertTrue(LocalMissionDraftRepository(context).loadLibrary().contains(first))
            var rejected = false
            try { repository.saveToLibrary(draft.copy(name = name.lowercase())) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Case-insensitive duplicate name must be rejected", rejected)
            assertEquals(priorEntries.size + 1, repository.loadLibrary().size)

            val second = repository.saveToLibrary(draft.copy(name = "$name-copy"))
                .also { createdIds += it.id }
            repository.deleteFromLibrary(first.id)
            assertEquals(priorEntries + second, LocalMissionDraftRepository(context).loadLibrary())
            assertEquals(priorDraft, repository.load())
            assertEquals(priorRecovery, repository.loadRecovery())
        } finally {
            createdIds.forEach { repository.deleteFromLibrary(it) }
        }
        assertEquals(priorEntries, repository.loadLibrary())
    }
}
