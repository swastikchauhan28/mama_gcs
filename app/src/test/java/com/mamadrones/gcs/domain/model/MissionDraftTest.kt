package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class MissionDraftTest {
    @Test fun `editing reordering and deleting preserve stable waypoint identities`() {
        val a = DraftWaypoint("a", -35.0, 149.0)
        val b = DraftWaypoint("b", -35.1, 149.1)
        val original = MissionDraft().add(a).add(b)
        val edited = original.edit(a.copy(latitude = -35.2)).move("a", 1)
        assertEquals(listOf("b", "a"), edited.waypoints.map { it.id })
        assertEquals(-35.2, edited.waypoints[1].latitude, 0.0)
        assertEquals(original.waypoints, listOf(a, b))
        assertEquals(listOf(b), edited.remove("a").waypoints)
        assertEquals(original, original.move("a", -1))
    }

    @Test fun `invalid coordinates duplicates and oversized drafts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DraftWaypoint("a", Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { DraftWaypoint("a", 90.1, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { DraftWaypoint("a", 0.0, 180.1) }
        val a = DraftWaypoint("a", 0.0, 0.0)
        assertThrows(IllegalArgumentException::class.java) { MissionDraft(waypoints = listOf(a, a)) }
        assertThrows(IllegalArgumentException::class.java) {
            MissionDraft(waypoints = List(MissionDraft.MAX_WAYPOINTS + 1) { a.copy(id = "$it") })
        }
        assertThrows(IllegalArgumentException::class.java) { MissionDraft(name = " ") }
        assertThrows(IllegalArgumentException::class.java) {
            MissionDraft(keepInFence = List(MissionDraft.MAX_FENCE_VERTICES + 1) { DraftWaypoint("f$it", 0.0, it / 100.0) })
        }
    }

    @Test fun `distance handles identical points and date line crossings`() {
        val a = DraftWaypoint("a", 0.0, 179.9)
        assertEquals(0.0, MissionDraft().distanceMeters, 0.0)
        assertEquals(0.0, MissionDraft(waypoints = listOf(a, a.copy(id = "b"))).distanceMeters, 0.0)
        val crossing = MissionDraft(waypoints = listOf(a, a.copy(id = "b", longitude = -179.9)))
        assertEquals(22_239.0, crossing.distanceMeters, 2.0)
    }
}
