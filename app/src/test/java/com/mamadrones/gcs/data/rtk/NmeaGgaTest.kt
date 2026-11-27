package com.mamadrones.gcs.data.rtk

import com.mamadrones.gcs.domain.model.GpsFix
import com.mamadrones.gcs.domain.model.GpsState
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class NmeaGgaTest {
    private val gps = GpsState(fix = GpsFix.FIX_3D, satellites = 8, hdop = 0.9,
        latitude = 48.1173, longitude = 11.5166666667, altitudeMeters = 545.4,
        lastUpdatedAtElapsedMillis = 1_000)

    @Test fun freshCubeFixFormatsAValidGgaChecksum() {
        val sentence = NmeaGga.fromCube(gps, 2_000, LocalTime.of(12, 35, 19))!!
        assertTrue(sentence.startsWith("\$GPGGA,123519.00,4807.0380,N,01131.0000,E,1,08,0.9,545.40,M,,M,,*"))
        val body = sentence.substring(1, sentence.indexOf('*'))
        val checksum = body.fold(0) { value, char -> value xor char.code }
        assertTrue(sentence.endsWith("*${"%02X".format(checksum)}\r\n"))
    }

    @Test fun staleInvalidOrTwoDimensionalGpsNeverProducesGga() {
        val utc = LocalTime.NOON
        assertNull(NmeaGga.fromCube(gps, 6_001, utc))
        assertNull(NmeaGga.fromCube(gps.copy(fix = GpsFix.FIX_2D), 2_000, utc))
        assertNull(NmeaGga.fromCube(gps.copy(latitude = null), 2_000, utc))
        assertNull(NmeaGga.fromCube(gps.copy(altitudeMeters = null), 2_000, utc))
        assertNull(NmeaGga.fromCube(gps.copy(lastUpdatedAtElapsedMillis = null), 2_000, utc))
        assertTrue(NmeaGga.fromCube(gps.copy(fix = GpsFix.RTK_FIXED), 2_000, utc)!!.contains(",4,08,"))
    }
}
