package com.mamadrones.gcs.data.rtk

import com.mamadrones.gcs.domain.model.GpsFix
import com.mamadrones.gcs.domain.model.GpsState
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A VRS position sentence from fresh, selected-source Cube GPS; never from a map or saved plan. */
object NmeaGga {
    fun fromCube(gps: GpsState, nowElapsedMillis: Long, utc: LocalTime): String? {
        val age = gps.lastUpdatedAtElapsedMillis?.let { nowElapsedMillis - it } ?: return null
        if (age !in 0..5_000) return null
        val quality = when (gps.fix) {
            GpsFix.FIX_3D -> 1
            GpsFix.DGPS -> 2
            GpsFix.RTK_FIXED -> 4
            GpsFix.RTK_FLOAT -> 5
            else -> return null
        }
        val lat = gps.latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val lon = gps.longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val altitude = gps.altitudeMeters?.takeIf { it.isFinite() && it in -1_000.0..100_000.0 } ?: return null
        val satellites = gps.satellites?.takeIf { it in 1..99 } ?: return null
        val hdop = gps.hdop?.takeIf { it.isFinite() && it in 0.0..99.9 } ?: return null
        val body = String.format(Locale.US,
            "GPGGA,%02d%02d%02d.00,%s,%s,%s,%s,%d,%02d,%.1f,%.2f,M,,M,,",
            utc.hour, utc.minute, utc.second,
            coordinate(lat, 2), if (lat < 0) "S" else "N",
            coordinate(lon, 3), if (lon < 0) "W" else "E",
            quality, satellites, hdop, altitude)
        val checksum = body.fold(0) { crc, char -> crc xor char.code }
        return "\$$body*${String.format(Locale.US, "%02X", checksum)}\r\n"
    }

    private fun coordinate(degrees: Double, degreeDigits: Int): String {
        val minuteUnits = (abs(degrees) * 60.0 * 10_000.0).roundToInt()
        val wholeDegrees = minuteUnits / 600_000
        val remaining = minuteUnits % 600_000
        return String.format(Locale.US, "%0${degreeDigits}d%02d.%04d",
            wholeDegrees, remaining / 10_000, remaining % 10_000)
    }
}
