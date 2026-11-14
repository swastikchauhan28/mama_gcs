package com.mamadrones.gcs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionDraftReviewTest {
    @Test fun `empty and single waypoint drafts are structurally incomplete`() {
        assertFalse(MissionDraftReview.inspect(MissionDraft()).hasRouteGeometry)
        assertFalse(MissionDraftReview.inspect(MissionDraft(waypoints = listOf(point("a", 1.0, 2.0)))).hasRouteGeometry)
        assertNull(MissionDraftReview.inspect(MissionDraft()).firstRouteFixWaypointNumber)
    }

    @Test fun `two distinct points define route geometry but not safety`() {
        val review = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("a", 1.0, 2.0), point("b", 1.0, 2.1),
        )))

        assertTrue(review.hasRouteGeometry)
        assertTrue(review.zeroLengthLegs.isEmpty())
        assertTrue(review.readinessIssues.isEmpty())
        assertNull(review.firstRouteFixWaypointNumber)
    }

    @Test fun `readiness lists actionable local issues without assuming an outline`() {
        val incomplete = MissionDraftReview.inspect(MissionDraft())
        assertEquals(listOf("Add at least two route waypoints."), incomplete.readinessIssues)
        val duplicate = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("a", 1.0, 2.0), point("b", 1.0, 2.0),
        )))
        assertTrue(duplicate.readinessIssues.single().contains("legs 1"))
    }

    @Test fun `identical consecutive coordinates report one based leg numbers`() {
        val review = MissionDraftReview.inspect(MissionDraft(waypoints = listOf(
            point("a", 1.0, 2.0), point("b", 1.0, 2.0), point("c", 1.2, 2.2),
            point("d", 1.2, 2.2),
        )))

        assertTrue(review.hasRouteGeometry)
        assertEquals(listOf(1, 3), review.zeroLengthLegs)
        assertEquals(listOf("Edit or remove identical consecutive points at legs 1, 3."), review.readinessIssues)
        assertEquals(2, review.firstRouteFixWaypointNumber)
        assertEquals(MissionReviewIssue(review.readinessIssues.single(), MissionReviewDestination.ROUTE, listOf(
            MissionReviewTarget("Leg 1 → waypoint 2", 2), MissionReviewTarget("Leg 3 → waypoint 4", 4),
        )), review.issues.single())
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
        assertTrue(review.readinessIssues.contains("Move route points inside the outline: 2."))
        assertEquals(2, review.firstRouteFixWaypointNumber)
        assertEquals(2, review.issues.first { it.message.startsWith("Move route points") }.waypointNumber)
        assertEquals(1, review.issues.first { it.message.startsWith("Revise straight legs") }.waypointNumber)
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
        assertEquals(listOf("Revise straight legs leaving the outline: 1."), review.readinessIssues)
        assertEquals(1, review.firstRouteFixWaypointNumber)
        assertEquals(MissionReviewDestination.ROUTE, review.issues.single().destination)
    }

    @Test fun `self crossing fence is rejected for containment review`() {
        val bowTie = listOf(
            point("f1", 0.0, 0.0), point("f2", 3.0, 3.0),
            point("f3", 2.0, 0.0), point("f4", 0.0, 2.0),
        )
        val review = MissionDraftReview.inspect(MissionDraft(keepInFence = bowTie))

        assertFalse(review.hasUsableKeepInOutline)
        assertEquals("OUTLINE CROSSES ITSELF", review.keepInOutlineIssue)
        assertTrue(review.readinessIssues.contains("Fix the local outline: OUTLINE CROSSES ITSELF."))
        assertEquals(MissionReviewDestination.FIELD_OUTLINE, review.issues.first { it.message.startsWith("Fix the local outline") }.destination)
    }

    @Test fun `each outside point and crossing leg has its own focus target`() {
        val uShapedFence = listOf(
            point("f1", 0.0, 0.0), point("f2", 4.0, 0.0),
            point("f3", 4.0, 1.0), point("f4", 1.0, 1.0),
            point("f5", 1.0, 3.0), point("f6", 4.0, 3.0),
            point("f7", 4.0, 4.0), point("f8", 0.0, 4.0),
        )
        val review = MissionDraftReview.inspect(MissionDraft(
            waypoints = listOf(point("left", 2.0, 0.5), point("right", 2.0, 3.5), point("left2", 2.0, 0.5)),
            keepInFence = uShapedFence,
        ))

        assertEquals(listOf(1, 2), review.legsOutsideKeepIn)
        assertEquals(listOf(MissionReviewTarget("Leg 1 → waypoint 1", 1),
            MissionReviewTarget("Leg 2 → waypoint 2", 2)), review.issues.single().targets)

        val square = listOf(point("s1", 0.0, 0.0), point("s2", 0.0, 4.0),
            point("s3", 4.0, 4.0), point("s4", 4.0, 0.0))
        val pointReview = MissionDraftReview.inspect(MissionDraft(
            waypoints = listOf(point("in1", 2.0, 2.0), point("out1", -1.0, 2.0),
                point("in2", 2.0, 2.0), point("out2", 5.0, 2.0)),
            keepInFence = square,
        ))
        assertEquals(listOf(2, 4), pointReview.waypointsOutsideKeepIn)
        assertEquals(listOf(MissionReviewTarget("Waypoint 2", 2), MissionReviewTarget("Waypoint 4", 4)),
            pointReview.issues.first { it.message.startsWith("Move route points") }.targets)
    }

    @Test fun `maximum length draft retains bounded warning rows and all leg targets`() {
        val review = MissionDraftReview.inspect(MissionDraft(
            waypoints = List(MissionDraft.MAX_WAYPOINTS) { point("p$it", 1.0, 2.0) },
        ))

        assertEquals(1, review.issues.size)
        assertEquals(MissionDraft.MAX_WAYPOINTS - 1, review.issues.single().targets.size)
        assertEquals(2, review.issues.single().targets.first().waypointNumber)
        assertEquals(MissionDraft.MAX_WAYPOINTS, review.issues.single().targets.last().waypointNumber)
    }

    private fun point(id: String, latitude: Double, longitude: Double) = DraftWaypoint(id, latitude, longitude)
}
