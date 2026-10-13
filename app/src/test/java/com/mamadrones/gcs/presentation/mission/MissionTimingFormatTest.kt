package com.mamadrones.gcs.presentation.mission

import org.junit.Assert.*
import org.junit.Test

class MissionTimingFormatTest {
    @Test fun acceptsDecimalDotCommaAndBounds() {
        for ((text, expected) in listOf("0.01" to 0.01, "8" to 8.0, "8.00" to 8.0, "2,22" to 2.22,
            ".5" to 0.5, " 4.5 " to 4.5, "1." to 1.0)) {
            assertEquals(expected, MissionTimingFormat.parseSpeed(text)!!, 0.0)
        }
    }

    @Test fun rejectsInvalidOverLimitAndAmbiguousInputsWithoutRounding() {
        for (text in listOf("", " ", "0", "-1", "0.001", "8.01", "8.000000001", "14.4", "1e0", "NaN",
            "Infinity", "1,000", "1.2.3", "1 km/h", "+1", "99999999999999999999999")) {
            assertNull(text, MissionTimingFormat.parseSpeed(text))
        }
    }

    @Test fun durationRoundsUpAndIncludesDaysWithoutWrapping() {
        assertEquals("0s", MissionTimingFormat.duration(0.0))
        assertEquals("1s", MissionTimingFormat.duration(0.01))
        assertEquals("1m 0s", MissionTimingFormat.duration(59.01))
        assertEquals("1h 0m 0s", MissionTimingFormat.duration(3600.0))
        assertEquals("1d 1h 1m 1s", MissionTimingFormat.duration(90061.0))
        assertEquals("20000000d 0h 0m 0s", MissionTimingFormat.duration(20_000_000.0 * 86400))
    }

    @Test fun missingAndInvalidDurationsAreNotZeroOrAnEta() {
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertEquals("NOT ESTIMATED", MissionTimingFormat.duration(value))
        }
    }
}
