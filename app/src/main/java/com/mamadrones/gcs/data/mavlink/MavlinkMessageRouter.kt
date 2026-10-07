package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import javax.inject.Inject
import javax.inject.Singleton

/** Routes decoded MAVLink messages to domain state without exposing frames upstream. */
@Singleton
class MavlinkMessageRouter @Inject constructor(
    private val vehicleRepository: VehicleRepositoryImpl
) {
    private val statusTextAssembler = StatusTextAssembler()

    fun reset() = statusTextAssembler.reset()

    fun route(result: MavlinkParseResult, receivedAtEpochMillis: Long) {
        val message = (result as? MavlinkParseResult.Message)?.message ?: return
        when (message) {
            is MavlinkMessage.Heartbeat -> vehicleRepository.onHeartbeat(message, receivedAtEpochMillis)
            is MavlinkMessage.GpsRawInt -> vehicleRepository.onGpsRawInt(message, receivedAtEpochMillis)
            is MavlinkMessage.GlobalPositionInt -> vehicleRepository.onGlobalPositionInt(message, receivedAtEpochMillis)
            is MavlinkMessage.Attitude -> vehicleRepository.onAttitude(message, receivedAtEpochMillis)
            is MavlinkMessage.VfrHud -> vehicleRepository.onVfrHud(message, receivedAtEpochMillis)
            is MavlinkMessage.SystemStatus -> vehicleRepository.onSystemStatus(message, receivedAtEpochMillis)
            is MavlinkMessage.BatteryStatus -> vehicleRepository.onBatteryStatus(message, receivedAtEpochMillis)
            is MavlinkMessage.StatusText -> statusTextAssembler.append(message, receivedAtEpochMillis)?.let {
                vehicleRepository.onStatusText(it.severity, it.text, receivedAtEpochMillis)
            }
        }
    }
}
