package com.mamadrones.gcs.presentation.map

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlanComparisonTest {
    private val selected = OfflineMapRectangle(10.0, 20.0, 11.0, 21.0)

    @Test fun reportsExactOutsideRouteAndOutlineIndicesWithInclusiveEdges() {
        val draft = MissionDraft("Working", waypoints = listOf(
            DraftWaypoint("edge", 10.0, 20.0),
            DraftWaypoint("east", 10.5, 21.001),
            DraftWaypoint("north", 11.001, 20.5)),
            keepInFence = listOf(
                DraftWaypoint("a", 10.2, 20.2),
                DraftWaypoint("b", 11.0, 21.0),
                DraftWaypoint("c", 9.999, 20.5)))
        assertEquals(SavedPlanOutsidePoints(listOf(2, 3), listOf(3)),
            findPlanOutsideSavedArea(draft, selected))
    }

    @Test fun incompleteOutlineIsNotCountedBecauseItIsNotDrawn() {
        val draft = MissionDraft("Working", keepInFence = listOf(
            DraftWaypoint("a", 9.0, 20.0), DraftWaypoint("b", 10.5, 22.0)))
        assertEquals(SavedPlanOutsidePoints(emptyList(), emptyList()),
            findPlanOutsideSavedArea(draft, selected))
    }
}
