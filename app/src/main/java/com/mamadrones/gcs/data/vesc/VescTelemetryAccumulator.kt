package com.mamadrones.gcs.data.vesc

import com.mamadrones.gcs.domain.model.MotorState
import com.mamadrones.gcs.domain.model.SubsystemConnection

/** A vehicle-specific, reviewed inventory and expected telemetry interval, not an operator discovery result. */
data class VescTelemetryProfile(
    val controllerIds: List<String>,
    val staleAfterMillis: Long
) {
    init {
        require(controllerIds.isNotEmpty() && controllerIds.all { it.isNotBlank() })
        require(controllerIds.size == controllerIds.distinct().size)
        require(staleAfterMillis > 0)
    }
}

/** Decoded, source-validated measurements. A transport adapter must establish provenance and units. */
data class VescTelemetryReading(
    val controllerId: String,
    val motorTemperatureCelsius: Double? = null,
    val controllerTemperatureCelsius: Double? = null,
    val mechanicalRpm: Double? = null,
    val electricalRpm: Double? = null,
    val motorCurrentAmps: Double? = null,
    val inputCurrentAmps: Double? = null,
    val inputVoltage: Double? = null,
    val dutyCycle: Double? = null,
    /** Null means not reported; adapters must not invent a no-fault reading. */
    val faultCode: String? = null
)

/** Pure read-only reducer. Unknown IDs never expand the provisioned controller inventory. */
class VescTelemetryAccumulator(private val profile: VescTelemetryProfile) {
    private val controllerIds = profile.controllerIds.toList()
    private val readings = mutableMapOf<String, MotorState>()

    fun accept(reading: VescTelemetryReading, receivedAtEpochMillis: Long): Boolean {
        if (reading.controllerId !in controllerIds || receivedAtEpochMillis < 0) return false
        val measurements = listOfNotNull(
            reading.motorTemperatureCelsius, reading.controllerTemperatureCelsius,
            reading.mechanicalRpm, reading.electricalRpm, reading.motorCurrentAmps,
            reading.inputCurrentAmps, reading.inputVoltage, reading.dutyCycle
        )
        if (measurements.isEmpty() && reading.faultCode.isNullOrBlank()) return false
        if (measurements.any { !it.isFinite() }) return false
        if (reading.inputVoltage != null && reading.inputVoltage < 0) return false
        if (reading.dutyCycle != null && reading.dutyCycle !in -1.0..1.0) return false
        val previousTime = readings[reading.controllerId]?.lastUpdatedAtEpochMillis
        if (previousTime != null && receivedAtEpochMillis < previousTime) return false

        readings[reading.controllerId] = MotorState(
            id = reading.controllerId,
            connection = SubsystemConnection.CONNECTED,
            temperatureCelsius = reading.motorTemperatureCelsius,
            controllerTemperatureCelsius = reading.controllerTemperatureCelsius,
            rpm = reading.mechanicalRpm,
            electricalRpm = reading.electricalRpm,
            motorCurrentAmps = reading.motorCurrentAmps,
            inputCurrentAmps = reading.inputCurrentAmps,
            voltage = reading.inputVoltage,
            dutyCycle = reading.dutyCycle,
            faultCode = reading.faultCode?.takeIf { it.isNotBlank() },
            lastUpdatedAtEpochMillis = receivedAtEpochMillis
        )
        return true
    }

    fun snapshot(nowEpochMillis: Long): List<MotorState> = controllerIds.map { id ->
        val latest = readings[id] ?: return@map MotorState(id)
        val age = nowEpochMillis - requireNotNull(latest.lastUpdatedAtEpochMillis)
        if (age < 0 || age > profile.staleAfterMillis) {
            MotorState(id, connection = SubsystemConnection.STALE, lastUpdatedAtEpochMillis = latest.lastUpdatedAtEpochMillis)
        } else latest
    }

    fun clear() = readings.clear()
}
