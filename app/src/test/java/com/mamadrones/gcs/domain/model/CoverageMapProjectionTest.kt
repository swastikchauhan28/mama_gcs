package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class CoverageMapProjectionTest {
    private fun field(latitude: Double, longitude: Double, vararg xy: Pair<Double, Double>) = xy.mapIndexed { i, (x, y) ->
        DraftWaypoint("m$i", latitude + Math.toDegrees(y / 6_371_000.0),
            longitude + Math.toDegrees(x / (6_371_000.0 * cos(Math.toRadians(latitude)))))
    }

    @Test fun inversePreservesPassEndpointsAcrossShapesLatitudesAndDirections() {
        val shapes = listOf(
            arrayOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 20.0, 0.0 to 20.0),
            arrayOf(0.0 to 0.0, 12.0 to 0.0, 0.0 to 12.0),
            arrayOf(0.0 to 0.0, 12.0 to 0.0, 12.0 to 12.0, 8.0 to 12.0, 8.0 to 4.0, 4.0 to 4.0, 4.0 to 12.0, 0.0 to 12.0),
        )
        for (latitude in listOf(0.0, 45.0, -70.0)) for (shape in shapes) for (eastWest in listOf(false, true)) {
            val outline = field(latitude, 77.0, *shape)
            val preview = (FieldCoveragePlanner.calculate(outline, 1.0, 10.0, eastWest, 0.5) as CoverageCalculation.Ready).preview
            val projected = CoverageMapProjection.project(preview, outline) as CoverageMapProjectionResult.Ready
            assertEquals(preview.passes.size, projected.lines.size)
            preview.passes.zip(projected.lines).forEach { (pass, line) ->
                for ((local, geo) in listOf(pass.start to line.start, pass.end to line.end)) {
                    val x = Math.toRadians(geo.longitude - outline.first().longitude) * 6_371_000.0 * cos(Math.toRadians(latitude))
                    val y = Math.toRadians(geo.latitude - outline.first().latitude) * 6_371_000.0
                    assertEquals(local.x, x, 0.00001)
                    assertEquals(local.y, y, 0.00001)
                }
            }
        }
    }

    @Test fun crossingDateLineIsRejectedWithoutWorldSpanningSegments() {
        val outline = listOf(DraftWaypoint("a", 0.0, 179.9999), DraftWaypoint("b", 0.0, -179.9999),
            DraftWaypoint("c", 0.0001, -179.9999), DraftWaypoint("d", 0.0001, 179.9999))
        val preview = (FieldCoveragePlanner.calculate(outline, 2.0, 0.0) as CoverageCalculation.Ready).preview
        val result = CoverageMapProjection.project(preview, outline) as CoverageMapProjectionResult.Unavailable
        assertTrue(result.reason.contains("date-line"))
    }
}
