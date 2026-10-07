package com.mamadrones.gcs.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaypointHitTestTest {
    @Test fun selectsTheClosestWaypointWithinTouchRadius() {
        val selected = hitTestDraftWaypoint(
            tapX = 20f,
            tapY = 20f,
            candidates = listOf(
                WaypointScreenPosition("first", 41f, 20f),
                WaypointScreenPosition("second", 22f, 21f),
            ),
            maxDistancePx = 24f,
        )

        assertEquals("second", selected)
    }

    @Test fun ignoresMapTapsOutsideTouchRadiusAndInvalidInputs() {
        val candidates = listOf(WaypointScreenPosition("first", 0f, 0f))
        assertNull(hitTestDraftWaypoint(40f, 0f, candidates, 24f))
        assertNull(hitTestDraftWaypoint(Float.NaN, 0f, candidates, 24f))
        assertNull(hitTestDraftWaypoint(0f, 0f, candidates, 0f))
        assertNull(hitTestDraftWaypoint(0f, 0f, emptyList(), 24f))
    }
}
