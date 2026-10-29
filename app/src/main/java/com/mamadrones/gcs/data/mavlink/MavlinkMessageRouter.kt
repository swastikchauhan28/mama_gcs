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

    fun route(result: MavlinkParseResult, receivedAtEpochMillis: Long, receivedAtElapsedMillis: Long? = null) {
        val message = (result as? MavlinkParseResult.Message)?.message ?: return
        when (message) {
            is MavlinkMessage.HomePosition -> vehicleRepository.onHomePosition(message, receivedAtEpochMillis)
            is MavlinkMessage.AutopilotVersion -> vehicleRepository.onAutopilotVersion(message, receivedAtEpochMillis)
            is MavlinkMessage.RcChannels -> vehicleRepository.onRcChannels(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.ServoOutputRaw -> vehicleRepository.onServoOutputRaw(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.Heartbeat -> vehicleRepository.onHeartbeat(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.GpsRawInt -> vehicleRepository.onGpsRawInt(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.GlobalPositionInt -> vehicleRepository.onGlobalPositionInt(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.Attitude -> vehicleRepository.onAttitude(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.VfrHud -> vehicleRepository.onVfrHud(message, receivedAtEpochMillis)
            is MavlinkMessage.SystemStatus -> vehicleRepository.onSystemStatus(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.BatteryStatus -> vehicleRepository.onBatteryStatus(message, receivedAtEpochMillis, receivedAtElapsedMillis)
            is MavlinkMessage.StatusText -> statusTextAssembler.append(message, receivedAtEpochMillis)?.let {
                vehicleRepository.onStatusText(it.severity, it.text, receivedAtEpochMillis)
            }
        }
    }
}
