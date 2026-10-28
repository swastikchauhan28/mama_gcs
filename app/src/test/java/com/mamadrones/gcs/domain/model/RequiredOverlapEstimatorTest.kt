package com.mamadrones.gcs.domain.model

import org.junit.Assert.*
import org.junit.Test

class RequiredOverlapEstimatorTest {
    @Test fun calculatesExampleAndDoesNotRoundDown() {
        assertEquals("10.00", RequiredOverlapEstimator.calculate(2.0, 0.05, 0.1)!!.percentRoundedUp.toPlainString())
        assertEquals("3.34", RequiredOverlapEstimator.calculate(3.0, 0.0, 0.1)!!.percentRoundedUp.toPlainString())
        assertEquals("0.00", RequiredOverlapEstimator.calculate(2.0, 0.0, 0.0)!!.percentRoundedUp.toPlainString())
    }

    @Test fun distinguishesPlannerLimitAndAbove() {
        assertFalse(RequiredOverlapEstimator.calculate(2.0, 0.75, 0.1)!!.exceedsPlannerLimit)
        assertTrue(RequiredOverlapEstimator.calculate(2.0, 0.8, 0.1)!!.exceedsPlannerLimit)
        assertTrue(RequiredOverlapEstimator.calculate(2.0, 0.750001, 0.1)!!.exceedsPlannerLimit)
    }

    @Test fun rejectsUnknownInvalidAndNonfiniteInputs() {
        for (value in listOf(null, -0.1, 50.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(RequiredOverlapEstimator.calculate(2.0, value, 0.1))
            assertNull(RequiredOverlapEstimator.calculate(2.0, 0.1, value))
        }
        assertNull(RequiredOverlapEstimator.calculate(0.0, 0.1, 0.1))
        assertNull(RequiredOverlapEstimator.calculate(null, 0.1, 0.1))
        assertNotNull(RequiredOverlapEstimator.calculate(50.0, 50.0, 50.0))
    }
}
