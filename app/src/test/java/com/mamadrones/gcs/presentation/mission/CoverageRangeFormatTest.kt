package com.mamadrones.gcs.presentation.mission

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageRangeFormatTest {
    @Test fun displayedRangesRoundOutwardEvenOnDecimalBoundaries() {
        assertEquals("0.0–0.0", coverageRange(0.0, 0.0))
        assertEquals("1.2–2.4", coverageRange(1.234, 2.345))
        assertEquals("0.9–1.1", coverageRange(1.0, 1.0))
        assertEquals("0.0–0.1", coverageRange(0.000001, 0.000002))
    }
}
