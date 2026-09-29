package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.PanelSpec
import java.util.Locale

/** Presentation projection: stale values cannot be mistaken for current machine state. */
fun VehicleState.forDisplay(): VehicleState = if (connected) this else VehicleState(
    vehicleId = vehicleId, displayName = displayName, connectionStatus = connectionStatus
)

fun Double?.reading(unit: String): String = if (this == null || !isFinite()) "UNKNOWN" else String.format(Locale.US, "%.1f %s", this, unit)

object ConsolePanels {
    fun vehicle(state: VehicleState) = state.forDisplay().let { s -> PanelSpec("Vehicle", s.displayName ?: "NO VEHICLE SELECTED", listOf(
        "Speed" to s.speedMetersPerSecond.reading("m/s"), "Heading" to s.headingDegrees.reading("°"),
        "Direction" to s.direction.name, "Mode" to (s.mode ?: "UNKNOWN"),
        "Armed" to (s.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "UNKNOWN")
    )) }
    fun gps(state: VehicleState) = state.forDisplay().gps.let { s -> PanelSpec("GPS", s.fix.name.replace('_', ' '), listOf(
        "Satellites" to (s.satellites?.toString() ?: "UNKNOWN"), "HDOP" to s.hdop.reading(""),
        "Latitude" to (s.latitude?.toString() ?: "UNKNOWN"), "Longitude" to (s.longitude?.toString() ?: "UNKNOWN")
    )) }
    fun battery(state: VehicleState) = state.forDisplay().battery.let { s -> PanelSpec("Battery", s.percentage?.let { "$it %" } ?: "UNKNOWN", listOf(
        "Voltage" to s.voltage.reading("V"), "Current" to s.currentAmps.reading("A"), "Temperature" to s.temperatureCelsius.reading("°C")
    )) }
    fun motors(state: VehicleState) = state.forDisplay().motors.let { motors -> PanelSpec("VESC motors", if (motors.isEmpty()) "TELEMETRY UNAVAILABLE" else "${motors.size} CONTROLLERS", listOf(
        "Motor temperature" to "UNKNOWN", "Controller temperature" to "UNKNOWN", "Fault state" to "UNKNOWN"
    ), "Controller count and telemetry route are not configured.") }
    fun motor(motor: MotorState) = PanelSpec("Motor ${motor.id}", motor.connection.name.replace('_', ' '), listOf(
        "Motor temperature" to motor.temperatureCelsius.reading("°C"), "Controller temperature" to motor.controllerTemperatureCelsius.reading("°C"),
        "RPM" to motor.rpm.reading("rpm"), "Electrical RPM" to motor.electricalRpm.reading("erpm"),
        "Motor current" to motor.motorCurrentAmps.reading("A"), "Input current" to motor.inputCurrentAmps.reading("A"),
        "Voltage" to motor.voltage.reading("V"), "Fault" to (motor.faultCode ?: "UNKNOWN")
    ))
    fun spray(state: VehicleState) = state.forDisplay().spray.let { s -> PanelSpec("Spray system", s.connection.name.replace('_', ' '), listOf(
        "Pump" to s.pump.power.name, "Spraying" to s.spraying.name,
        "Nozzle count" to (s.nozzles?.size?.toString() ?: "UNKNOWN"), "Pressure" to s.pressureBar.reading("bar"),
        "Flow" to s.flowLitersPerMinute.reading("L/min")
    )) }
    fun hydraulic(state: VehicleState) = state.forDisplay().hydraulic.let { s -> PanelSpec("Hydraulic system", s.connection.name.replace('_', ' '), listOf(
        "Enabled" to s.enabled.name, "Pump" to s.pump.name, "Valve" to s.valve.name,
        "Pressure" to s.pressureBar.reading("bar"), "Temperature" to s.temperatureCelsius.reading("°C")
    )) }
    fun health(state: VehicleState) = state.forDisplay().health.let { s -> PanelSpec("Vehicle health", s.overall.name, listOf(
        "Communication" to s.communication.name, "GPS" to s.gps.name, "Battery" to s.battery.name,
        "Motors" to s.motors.name, "Spray" to s.spray.name, "Hydraulic" to s.hydraulic.name
    ), "Health evaluation is not yet available.") }
}
