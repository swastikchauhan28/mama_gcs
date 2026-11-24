package com.mamadrones.gcs.presentation.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineViewportCoverageTest {
    private val view = OfflineMapRectangle(0.0, 0.0, 2.0, 2.0)

    @Test fun adjacentEastWestRegionsCoverTheView() {
        assertTrue(OfflineViewportCoverage.contains(view, listOf(
            OfflineMapRectangle(0.0, 0.0, 2.0, 1.0),
            OfflineMapRectangle(0.0, 1.0, 2.0, 2.0))))
    }

    @Test fun adjacentNorthSouthRegionsCoverTheView() {
        assertTrue(OfflineViewportCoverage.contains(view, listOf(
            OfflineMapRectangle(0.0, 0.0, 1.0, 2.0),
            OfflineMapRectangle(1.0, 0.0, 2.0, 2.0))))
    }

    @Test fun narrowGapDoesNotCountAsCovered() {
        assertFalse(OfflineViewportCoverage.contains(view, listOf(
            OfflineMapRectangle(0.0, 0.0, 2.0, 0.999),
            OfflineMapRectangle(0.0, 1.0, 2.0, 2.0))))
    }

    @Test fun missingCornerDoesNotCountAsCovered() {
        assertFalse(OfflineViewportCoverage.contains(view, listOf(
            OfflineMapRectangle(0.0, 0.0, 2.0, 1.0),
            OfflineMapRectangle(0.0, 1.0, 1.0, 2.0))))
    }

    @Test fun viewportOutsideAllRegionsDoesNotCountAsCovered() {
        assertFalse(OfflineViewportCoverage.contains(view, listOf(
            OfflineMapRectangle(3.0, 3.0, 4.0, 4.0))))
    }

    @Test fun invalidOrWrappedViewportDoesNotClaimCoverage() {
        assertFalse(OfflineViewportCoverage.contains(OfflineMapRectangle(0.0, 179.0, 2.0, -179.0),
            listOf(OfflineMapRectangle(0.0, -180.0, 2.0, 180.0))))
    }
}
