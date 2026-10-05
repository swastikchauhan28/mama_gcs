package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class MissionDraftCodecTest {
    @Test fun `storage roundtrip retains coordinate precision names and order`() {
        val draft = MissionDraft("Field A / खेत", listOf(
            DraftWaypoint("west", -35.3632621, 149.1652374), DraftWaypoint("east", 0.0, 180.0)))
        assertEquals(draft, MissionDraftCodec.decode(MissionDraftCodec.encode(draft)))
        assertEquals(MissionDraft(), MissionDraftCodec.decode(MissionDraftCodec.encode(MissionDraft())))
    }

    @Test fun `corrupted or unsupported data is never silently replaced by empty draft`() {
        val good = Base64.getDecoder().decode(MissionDraftCodec.encode(MissionDraft()))
        val unsupported = good.copyOf().apply { this[3] = 2 }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(unsupported)) }
        assertThrows(Exception::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(good.copyOf(5))) }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode(Base64.getEncoder().encodeToString(good + byteArrayOf(0))) }
        assertThrows(IllegalArgumentException::class.java) { MissionDraftCodec.decode("?invalid") }
    }
}
