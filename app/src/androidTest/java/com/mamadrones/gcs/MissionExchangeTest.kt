package com.mamadrones.gcs

import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.MissionDraftGeoJsonCodec
import com.mamadrones.gcs.data.local.datastore.MissionDraftGpxCodec
import com.mamadrones.gcs.data.local.datastore.MissionDraftRouteCodec
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.repository.MissionDraftFileFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionExchangeTest {
    @Test fun gpxRoundTripsOrderedRoutePointsAndEscapedName() {
        val codec = MissionDraftRouteCodec(MissionDraftGeoJsonCodec(), MissionDraftGpxCodec())
        val source = MissionDraft("North & south <field>", listOf(
            DraftWaypoint("first", -35.3632621, 149.1652374),
            DraftWaypoint("second", -35.364, 149.166),
        ), listOf(
            DraftWaypoint("f1", -35.36, 149.16), DraftWaypoint("f2", -35.37, 149.16),
            DraftWaypoint("f3", -35.37, 149.17),
        ))

        val file = codec.encode(source, MissionDraftFileFormat.GPX)
        val restored = codec.decode(file)

        assertTrue(file.contains("<rtept"))
        assertTrue(file.contains("North &amp; south &lt;field&gt;"))
        assertEquals(source.name, restored.name)
        assertEquals(source.waypoints.size, restored.waypoints.size)
        source.waypoints.zip(restored.waypoints).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 0.00000001)
            assertEquals(expected.longitude, actual.longitude, 0.00000001)
        }
        assertTrue("GPX does not carry the local outline", restored.keepInFence.isEmpty())
    }

    @Test fun gpxRejectsDocumentTypesAndFilesWithoutRoutes() {
        val codec = MissionDraftGpxCodec()
        assertRejected { codec.decode("""<!DOCTYPE gpx [<!ENTITY x "bad">]><gpx/>""") }
        assertRejected { codec.decode("""<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><wpt lat="0" lon="0"/></gpx>""") }
    }

    @Test fun geoJsonRoundTripsNameAndOrderedWgs84Coordinates() {
        val source = MissionDraft("North field", listOf(
            DraftWaypoint("first", -35.3632621, 149.1652374),
            DraftWaypoint("second", -35.364, 149.166)
        ), listOf(
            DraftWaypoint("f1", -35.36, 149.16), DraftWaypoint("f2", -35.37, 149.16),
            DraftWaypoint("f3", -35.37, 149.17), DraftWaypoint("f4", -35.36, 149.17),
        ))
        val file = MissionDraftGeoJsonCodec().encode(source)
        val restored = MissionDraftGeoJsonCodec().decode(file)

        assertTrue(file.contains("FeatureCollection"))
        assertTrue(file.contains("\"coordinates\""))
        assertEquals(source.name, restored.name)
        assertEquals(source.waypoints.size, restored.waypoints.size)
        source.waypoints.zip(restored.waypoints).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 0.00000001)
            assertEquals(expected.longitude, actual.longitude, 0.00000001)
        }
        assertEquals(source.keepInFence.size, restored.keepInFence.size)
        source.keepInFence.zip(restored.keepInFence).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 0.00000001)
            assertEquals(expected.longitude, actual.longitude, 0.00000001)
        }
    }

    @Test fun geoJsonRejectsUnsupportedVersionAndInvalidCoordinates() {
        val codec = MissionDraftGeoJsonCodec()
        assertTrue(codec.decode("""{"type":"FeatureCollection","properties":{"format":"mama-gcs-route","version":1},"features":[]}""").keepInFence.isEmpty())
        assertRejected(codec) {
            """{"type":"FeatureCollection","properties":{"format":"mama-gcs-route","version":3},"features":[]}"""
        }
        assertRejected(codec) {
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[181,0]}}]}"""
        }
    }

    @Test fun recoveryCopyDoesNotReplaceSavedRouteAndCommitClearsIt() = runBlocking {
        val repository = LocalMissionDraftRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val priorSaved = repository.load()
        val priorRecovery = repository.loadRecovery()
        try {
            val committed = MissionDraft("Saved route")
            val working = MissionDraft("Recovered route", listOf(DraftWaypoint("recover-1", -35.0, 149.0)))
            repository.save(committed)
            repository.saveRecovery(working)

            assertEquals(committed, repository.load())
            assertEquals(working, repository.loadRecovery())

            repository.save(working)
            assertEquals(working, repository.load())
            assertNull(repository.loadRecovery())
        } finally {
            repository.save(priorSaved)
            priorRecovery?.let { repository.saveRecovery(it) }
        }
    }

    private fun assertRejected(codec: MissionDraftGeoJsonCodec, content: () -> String) {
        var rejected = false
        try {
            codec.decode(content())
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Expected invalid GeoJSON to be rejected", rejected)
    }

    private fun assertRejected(content: () -> Unit) {
        var rejected = false
        try { content() } catch (_: Exception) { rejected = true }
        assertTrue("Expected invalid GPX to be rejected", rejected)
    }
}
