package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class SidewaysOverlapEstimatorTest {
    @Test fun opposingErrorsReduceNominalOverlap() {
        val estimate = SidewaysOverlapEstimator.calculate(2.0, 10.0, 0.05)!!
        assertEquals(0.2, estimate.nominalMeters, 1e-12)
        assertEquals(0.1, estimate.marginMeters, 1e-12)
        assertEquals(0.05, estimate.perPassErrorMeters, 0.0)
    }

    @Test fun zeroMarginAndNegativeMarginStayDistinct() {
        assertEquals(0.0, SidewaysOverlapEstimator.calculate(2.0, 10.0, 0.1)!!.marginMeters, 1e-12)
        assertEquals(-0.1, SidewaysOverlapEstimator.calculate(2.0, 10.0, 0.15)!!.marginMeters, 1e-12)
        assertEquals(0.0, SidewaysOverlapEstimator.calculate(2.0, 0.0, 0.0)!!.marginMeters, 0.0)
    }

    @Test fun unknownInvalidAndNonfiniteInputsAreUnavailable() {
        for (error in listOf(null, -0.1, 50.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(SidewaysOverlapEstimator.calculate(2.0, 10.0, error))
        }
        assertNull(SidewaysOverlapEstimator.calculate(null, 10.0, 0.1))
        assertNull(SidewaysOverlapEstimator.calculate(2.0, Double.NaN, 0.1))
        assertNull(SidewaysOverlapEstimator.calculate(0.0, 10.0, 0.1))
        assertNotNull(SidewaysOverlapEstimator.calculate(50.0, 80.0, 50.0))
    }
}
