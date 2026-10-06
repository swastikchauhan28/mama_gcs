package com.mamadrones.gcs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionDraftReviewTest {
    @Test fun `empty and single waypoint drafts are structurally incomplete`() {
        assertFalse(MissionDraftReview.inspect(MissionDraft()).hasRouteGeometry)
        assertFalse(MissionDraftReview.inspect(MissionDraft(waypoints = listOf(point("a", 1.0, 2.0)))).hasRouteGeometry)
    }

    @Test fun `two distinct points define route geometry but not safety`() {
        val review = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("a", 1.0, 2.0), point("b", 1.0, 2.1),
        )))

        assertTrue(review.hasRouteGeometry)
        assertTrue(review.zeroLengthLegs.isEmpty())
    }

    @Test fun `identical consecutive coordinates report one based leg numbers`() {
        val review = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("a", 1.0, 2.0), point("b", 1.0, 2.0), point("c", 1.2, 2.2),
            point("d", 1.2, 2.2),
        )))

        assertTrue(review.hasRouteGeometry)
        assertEquals(listOf(1, 3), review.zeroLengthLegs)
    }

    @Test fun `keep in outline classifies waypoint positions with boundary treated as inside`() {
        val fence = listOf(
            point("f1", 0.0, 0.0), point("f2", 0.0, 10.0),
            point("f3", 10.0, 10.0), point("f4", 10.0, 0.0),
        )
        val review = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("inside", 5.0, 5.0), point("outside", -1.0, 5.0), point("edge", 0.0, 5.0),
        ), keepInFence = fence))

        assertTrue(review.hasUsableKeepInOutline)
        assertEquals(listOf(2), review.waypointsOutsideKeepIn)
    }

    @Test fun `short or coordinate-degenerate outlines do not claim a containment check`() {
        val onlyTwo = listOf(point("f1", 0.0, 0.0), point("f2", 0.0, 1.0))
        val repeatedCoordinate = listOf(
            point("f1", 0.0, 0.0), point("f2", 0.0, 1.0), point("f3", 0.0, 1.0),
        )

        assertFalse(MissionDraftReview.inspect(MissionDraft(keepInFence = onlyTwo)).hasUsableKeepInOutline)
        assertFalse(MissionDraftReview.inspect(MissionDraft(keepInFence = repeatedCoordinate)).hasUsableKeepInOutline)
    }

    @Test fun `straight route leg leaving a concave outline is reported even when endpoints are inside`() {
        val uShapedFence = listOf(
            point("f1", 0.0, 0.0), point("f2", 4.0, 0.0),
            point("f3", 4.0, 1.0), point("f4", 1.0, 1.0),
            point("f5", 1.0, 3.0), point("f6", 4.0, 3.0),
            point("f7", 4.0, 4.0), point("f8", 0.0, 4.0),
        )
        val review = MissionDraftReview.inspect(MissionDraft(
            waypoints = listOf(point("left", 2.0, 0.5), point("right", 2.0, 3.5)),
            keepInFence = uShapedFence,
        ))

        assertTrue(review.hasUsableKeepInOutline)
        assertTrue(review.waypointsOutsideKeepIn.isEmpty())
        assertEquals(listOf(1), review.legsOutsideKeepIn)
    }

    @Test fun `self crossing fence is rejected for containment review`() {
        val bowTie = listOf(
            point("f1", 0.0, 0.0), point("f2", 3.0, 3.0),
            point("f3", 2.0, 0.0), point("f4", 0.0, 2.0),
        )
        val review = MissionDraftReview.inspect(MissionDraft(keepInFence = bowTie))

        assertFalse(review.hasUsableKeepInOutline)
        assertEquals("OUTLINE CROSSES ITSELF", review.keepInOutlineIssue)
    }

    private fun point(id: String, latitude: Double, longitude: Double) = DraftWaypoint(id, latitude, longitude)
}
