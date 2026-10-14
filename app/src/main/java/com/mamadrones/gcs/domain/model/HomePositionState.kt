package com.mamadrones.gcs.domain.model

/** Last HOME_POSITION observation, not a destination, phone position or verified safe location. */
data class HomePositionState(
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMetersMsl: Double?,
    val receivedAtEpochMillis: Long,
) {
    val hasCoordinate: Boolean get() = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

object HomePositionEvidence {
    fun belongsToSession(state: VehicleState, now: Long): Boolean {
        val start = state.mavlinkDiagnostics.startedAtEpochMillis ?: return false
        val time = state.homePosition?.receivedAtEpochMillis ?: return false
        return start >= 0 && time >= start && time <= now
    }

    // Home is retained metadata: no assumed publication interval. Heartbeat still must be fresh.
    fun visibleOnMap(state: VehicleState, now: Long): HomePositionState? {
        val heartbeat = state.lastHeartbeatAtEpochMillis ?: return null
        val start = state.mavlinkDiagnostics.startedAtEpochMillis ?: return null
        return state.homePosition?.takeIf {
            state.connected && state.mavlinkDiagnostics.active && belongsToSession(state, now) &&
                heartbeat >= start && heartbeat <= now && now - heartbeat <= 5_000 && it.hasCoordinate
        }
    }
}
