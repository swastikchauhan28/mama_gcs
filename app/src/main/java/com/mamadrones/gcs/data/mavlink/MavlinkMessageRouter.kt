package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import javax.inject.Inject
import javax.inject.Singleton

/** Routes decoded MAVLink messages to domain state without exposing frames upstream. */
@Singleton
class MavlinkMessageRouter @Inject constructor(
    private val vehicleRepository: VehicleRepositoryImpl
) {
    fun route(result: MavlinkParseResult, receivedAtEpochMillis: Long) {
        val message = (result as? MavlinkParseResult.Message)?.message ?: return
        when (message) {
            is MavlinkMessage.Heartbeat -> vehicleRepository.onHeartbeat(message, receivedAtEpochMillis)
        }
    }
}
