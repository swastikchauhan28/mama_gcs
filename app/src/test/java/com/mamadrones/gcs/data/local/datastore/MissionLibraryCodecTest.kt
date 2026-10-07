package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class MissionLibraryCodecTest {
    private fun entry(index: Int) = MissionLibraryEntry("entry-$index", MissionDraft(
        "Field $index / खेत", listOf(DraftWaypoint("point", -35.3632621, 149.1652374)),
        listOf(DraftWaypoint("outline", -35.0, 149.0)),
    ), index.toLong())

    @Test fun `empty and full libraries roundtrip identities times routes and outlines`() {
        listOf(emptyList(), List(MissionLibraryEntry.MAX_ENTRIES, ::entry)).forEach { entries ->
            assertEquals(entries, MissionLibraryCodec.decode(MissionLibraryCodec.encode(entries)))
        }
    }

    @Test fun `duplicate identities and excessive entries are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { MissionLibraryCodec.encode(listOf(entry(0), entry(0))) }
        assertThrows(IllegalArgumentException::class.java) {
            MissionLibraryCodec.encode(List(MissionLibraryEntry.MAX_ENTRIES + 1, ::entry))
        }
    }

    @Test fun `corruption and unsupported storage never become an empty library`() {
        val bytes = Base64.getDecoder().decode(MissionLibraryCodec.encode(listOf(entry(0))))
        val cases = listOf(
            "?invalid", Base64.getEncoder().encodeToString(bytes.copyOf(5)),
            Base64.getEncoder().encodeToString(bytes + byteArrayOf(0)),
            Base64.getEncoder().encodeToString(bytes.copyOf().apply { this[3] = 2 }),
            Base64.getEncoder().encodeToString(bytes.copyOf().apply { this[7] = 26 }),
        )
        cases.forEach { encoded -> assertThrows(Exception::class.java) { MissionLibraryCodec.decode(encoded) } }
    }
}
