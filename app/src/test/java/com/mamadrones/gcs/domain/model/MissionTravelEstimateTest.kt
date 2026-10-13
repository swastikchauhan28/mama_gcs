package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class MissionTravelEstimateTest {
    private fun point(id: String, lat: Double, lon: Double) = DraftWaypoint(id, lat, lon)
    private val route = MissionDraft(waypoints = listOf(point("a", 0.0, 0.0), point("b", 0.0, 0.001), point("c", 0.001, 0.001)))

    @Test fun knownDistanceUsesKilometersPerHourToMetersPerSecond() {
        val result = MissionTravelEstimate.calculate(route, 3.6)
        assertEquals(111.1949266, result.legs[0].distanceMeters, 0.000001)
        assertEquals(result.totalDistanceMeters, result.totalTravelSeconds!!, 0.000001)
        assertEquals(route.distanceMeters, result.totalDistanceMeters, 0.0)
        assertEquals(listOf(1, 2), result.legs.map { it.fromIndex })
        assertEquals(listOf(2, 3), result.legs.map { it.toIndex })
    }

    @Test fun doublingSpeedHalvesTimeWithoutChangingDistance() {
        val slow = MissionTravelEstimate.calculate(route, 4.0)
        val fast = MissionTravelEstimate.calculate(route, 8.0)
        assertEquals(slow.totalTravelSeconds!! / 2, fast.totalTravelSeconds!!, 0.000001)
        assertEquals(slow.totalDistanceMeters, fast.totalDistanceMeters, 0.0)
    }

    @Test fun rejectsOutOfRangeAndNonFiniteSpeedsRatherThanClamping() {
        for (value in listOf(0.0, -1.0, 0.009, 8.000001, 14.4, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            try { MissionTravelEstimate.calculate(route, value); fail("Accepted $value") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun emptyOrSingleWaypointHasNoTravelTime() {
        for (draft in listOf(MissionDraft(), route.copy(waypoints = route.waypoints.take(1)))) {
            val estimate = MissionTravelEstimate.calculate(draft, 8.0)
            assertTrue(estimate.legs.isEmpty())
            assertNull(estimate.totalTravelSeconds)
        }
    }

    @Test fun repeatedCoordinatesRetainZeroLengthLegAndDoNotInventTime() {
        val repeated = MissionDraft(waypoints = listOf(point("a", 0.0, 0.0), point("b", 0.0, 0.0)))
        val result = MissionTravelEstimate.calculate(repeated, 1.0)
        assertEquals(0.0, result.totalTravelSeconds!!, 0.0)
        assertEquals(1, result.legs.size)
    }

    @Test fun dateLineCrossingTakesShortSurfaceArc() {
        val draft = MissionDraft(waypoints = listOf(point("a", 0.0, 179.999), point("b", 0.0, -179.999)))
        val result = MissionTravelEstimate.calculate(draft, 3.6)
        assertEquals(222.3898533, result.totalDistanceMeters, 0.00001)
        assertTrue(result.totalTravelSeconds!!.isFinite())
    }

    @Test fun polarAndAntipodalCoordinatesStayFinite() {
        val draft = MissionDraft(waypoints = listOf(point("a", 90.0, 0.0), point("b", -90.0, 0.0)))
        val result = MissionTravelEstimate.calculate(draft, 0.01)
        assertEquals(Math.PI * 6_371_000, result.totalDistanceMeters, 0.001)
        assertTrue(result.totalTravelSeconds!!.isFinite())
    }

    @Test fun fullEditorBoundRemainsFiniteAtSmallestSpeed() {
        val draft = MissionDraft(waypoints = (0 until MissionDraft.MAX_WAYPOINTS).map {
            point("p$it", if (it % 2 == 0) 90.0 else -90.0, 0.0)
        })
        val result = MissionTravelEstimate.calculate(draft, 0.01)
        assertEquals(249, result.legs.size)
        assertTrue(result.totalTravelSeconds!!.isFinite())
        assertTrue(result.totalTravelSeconds!! < Long.MAX_VALUE.toDouble())
    }

    @Test fun editsAndReorderingRecomputeWithoutMutatingDraftOrUsingOutline() {
        val original = route.copy(keepInFence = listOf(point("f", 20.0, 30.0)))
        val saved = original.copy(waypoints = original.waypoints.toList())
        val first = MissionTravelEstimate.calculate(original, 4.0)
        val moved = original.move("c", -1)
        val second = MissionTravelEstimate.calculate(moved, 4.0)
        assertEquals(saved, original)
        assertNotEquals(first.totalDistanceMeters, second.totalDistanceMeters)
        assertEquals(MissionTravelEstimate.calculate(route, 4.0), first)
        assertEquals(first.legs.take(1), MissionTravelEstimate.calculate(original.remove("c"), 4.0).legs)
    }
}
