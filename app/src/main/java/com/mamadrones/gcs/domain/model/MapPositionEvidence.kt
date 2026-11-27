package com.mamadrones.gcs.domain.model

/** Map display evidence only; this is not a GPS-quality or vehicle-safety assessment. */
enum class MapPositionStatus { FRESH, STALE, UNAVAILABLE }

object MapPositionEvidence {
    // App diagnostic window, not a validated vehicle telemetry rate or safety limit.
    const val MAX_POSITION_AGE_MILLIS = 5_000L

    /** A no-fix Cube can emit fresh GLOBAL_POSITION_INT at 0,0; it is not a located rover. */
    fun isZeroPlaceholder(latitude: Double, longitude: Double, fix: GpsFix): Boolean =
        latitude == 0.0 && longitude == 0.0 && (fix == GpsFix.UNKNOWN || fix == GpsFix.NO_FIX)

    fun status(state: VehicleState, nowElapsedMillis: Long): MapPositionStatus {
        val position = state.position
        val lat = position.latitude
        val lon = position.longitude
        val received = position.lastUpdatedAtElapsedMillis
        if (!state.connected || nowElapsedMillis < 0 || received == null || received < 0 || received > nowElapsedMillis ||
            lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
            isZeroPlaceholder(lat, lon, state.gps.fix)
        ) return MapPositionStatus.UNAVAILABLE
        return if (nowElapsedMillis - received <= MAX_POSITION_AGE_MILLIS) MapPositionStatus.FRESH else MapPositionStatus.STALE
    }
}
