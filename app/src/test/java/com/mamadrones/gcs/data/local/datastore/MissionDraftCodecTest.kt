package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class MissionDraftCodecTest {
    @Test fun `storage roundtrip retains coordinate precision names and order`() {
        val draft = MissionDraft("Field A / खेत", listOf(
            DraftWaypoint("west", -35.3632621, 149.1652374), DraftWaypoint("east", 0.0, 180.0)), listOf(
            DraftWaypoint("f1", -35.36, 149.16), DraftWaypoint("f2", -35.37, 149.16),
            DraftWaypoint("f3", -35.37, 149.17), DraftWaypoint("f4", -35.36, 149.17),
        ))
        assertEquals(draft, MissionDraftCodec.decode(MissionDraftCodec.encode(draft)))
        assertEquals(MissionDraft(), MissionDraftCodec.decode(MissionDraftCodec.encode(MissionDraft())))
    }

    @Test fun `corrupted or unsupported data is never silently replaced by empty draft`() {
        val good = Base64.getDecoder().decode(MissionDraftCodec.encode(MissionDraft()))
        val unsupported = good.copyOf().apply { this[3] = 3 }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(unsupported)) }
        assertThrows(Exception::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(good.copyOf(5))) }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(good + byteArrayOf(0))) }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode("?invalid") }
    }

    @Test fun `version one saved drafts migrate without a fence`() {
        val legacy = Base64.getEncoder().encodeToString(byteArrayOf(
            0, 0, 0, 1, // v1
            0, 5, 'R'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(), 't'.code.toByte(), 'e'.code.toByte(),
            0, 0, 0, 1, // one waypoint
            0, 1, 'a'.code.toByte(),
            *java.nio.ByteBuffer.allocate(16).putDouble(-35.0).putDouble(149.0).array(),
        ))
        val migrated = MissionDraftCodec.decode(legacy)

        assertEquals("Route", migrated.name)
        assertEquals(-35.0, migrated.waypoints.single().latitude, 0.0)
        assertTrue(migrated.keepInFence.isEmpty())
    }
}
