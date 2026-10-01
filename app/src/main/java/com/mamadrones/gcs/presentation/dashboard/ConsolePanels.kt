package com.mamadrones.gcs.presentation.dashboard

import com.mamadrones.gcs.core.health.HealthAssessmentEvaluator
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
    ), sampleAge(s.kinematicsLastUpdatedAtEpochMillis)) }
    fun gps(state: VehicleState) = state.forDisplay().gps.let { s -> PanelSpec("GPS", s.fix.name.replace('_', ' '), listOf(
        "Satellites" to (s.satellites?.toString() ?: "UNKNOWN"), "HDOP" to s.hdop.reading(""),
        "Latitude" to (s.latitude?.toString() ?: "UNKNOWN"), "Longitude" to (s.longitude?.toString() ?: "UNKNOWN")
    ), sampleAge(s.lastUpdatedAtEpochMillis)) }
    fun position(state: VehicleState) = state.forDisplay().position.let { s -> PanelSpec("Global position", if (s.latitude != null && s.longitude != null) "RECEIVED" else "UNKNOWN", listOf(
        "Latitude" to (s.latitude?.reading("°") ?: "UNKNOWN"), "Longitude" to (s.longitude?.reading("°") ?: "UNKNOWN"),
        "Altitude MSL" to s.altitudeMetersMsl.reading("m")
    ), sampleAge(s.lastUpdatedAtEpochMillis)) }
    fun battery(state: VehicleState): PanelSpec {
        val displayed = state.forDisplay()
        val packs = displayed.batteries
        if (packs.size > 1) return PanelSpec(
            "Battery packs", "${packs.size} REPORTED",
            packs.flatMap { pack ->
                listOf(
                    "Pack ${pack.batteryId} remaining" to (pack.percentage?.let { "$it %" } ?: "UNKNOWN"),
                    "Pack ${pack.batteryId} voltage" to pack.voltage.reading("V"),
                    "Pack ${pack.batteryId} current" to pack.currentAmps.reading("A"),
                    "Pack ${pack.batteryId} temperature" to pack.temperatureCelsius.reading("°C"),
                    "Pack ${pack.batteryId} charge state" to chargeStateName(pack.chargeState)
                )
            },
            "Per-pack values from BATTERY_STATUS; ${sampleAge(packs.mapNotNull { it.lastUpdatedAtEpochMillis }.maxOrNull())}."
        )
        val sample = packs.singleOrNull() ?: displayed.battery
        val source = if (packs.isEmpty()) "SYS_STATUS system summary" else "BATTERY_STATUS pack ${sample.batteryId}"
        return PanelSpec(
            if (sample.batteryId == null) "Battery" else "Battery ${sample.batteryId}",
            sample.percentage?.let { "$it %" } ?: "UNKNOWN",
            listOf(
                "Voltage" to sample.voltage.reading("V"), "Current" to sample.currentAmps.reading("A"),
                "Temperature" to sample.temperatureCelsius.reading("°C"),
                "Charge state" to chargeStateName(sample.chargeState)
            ),
            "$source · ${sampleAge(sample.lastUpdatedAtEpochMillis)}${if (packs.isEmpty()) " · SYS_STATUS is aggregate and may be ambiguous on multi-battery systems" else ""}"
        )
    }
    fun attitude(state: VehicleState): PanelSpec {
        val sample = state.forDisplay().attitude
        return PanelSpec("Attitude", if (sample.lastUpdatedAtEpochMillis == null) "UNKNOWN" else "RECEIVED", listOf(
            "Roll" to sample.rollRadians?.let { Math.toDegrees(it).reading("°") }.orUnknown(),
            "Pitch" to sample.pitchRadians?.let { Math.toDegrees(it).reading("°") }.orUnknown(),
            "Yaw" to sample.yawRadians?.let { Math.toDegrees(it).reading("°") }.orUnknown()
        ), sampleAge(sample.lastUpdatedAtEpochMillis))
    }
    fun systemStatus(state: VehicleState): PanelSpec {
        val sample = state.forDisplay().systemStatus
        return PanelSpec("Autopilot SYS_STATUS", if (sample.lastUpdatedAtEpochMillis == null) "UNKNOWN" else "RECEIVED", listOf(
            "Sensor bits present" to sample.sensorsPresent.hexMask(),
            "Sensor bits enabled" to sample.sensorsEnabled.hexMask(),
            "Sensor bits healthy" to sample.sensorsHealthy.hexMask(),
            "CPU load" to sample.cpuLoadPercent.reading("%"),
            "Comm drop rate" to sample.communicationDropPercent.reading("%"),
            "Comm errors" to (sample.communicationErrors?.toString() ?: "UNKNOWN")
        ), sampleAge(sample.lastUpdatedAtEpochMillis))
    }
    fun statusTexts(state: VehicleState) = state.forDisplay().statusTexts.let { messages -> PanelSpec(
        "Autopilot messages", messages.firstOrNull()?.let { severityName(it.severity) } ?: "NO MESSAGES",
        messages.take(5).mapIndexed { index, message ->
            val age = sampleAge(message.receivedAtEpochMillis)
            "${severityName(message.severity)} · ${index + 1}" to "${message.text} ($age)"
        },
        "STATUSTEXT is informational; severity is shown as reported and does not drive a health verdict."
    ) }
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
    fun health(state: VehicleState): PanelSpec {
        val displayed = state.forDisplay()
        val assessment = HealthAssessmentEvaluator.evaluate(displayed)
        return PanelSpec("Operational readiness", assessment.readiness.name.replace('_', ' '), listOf(
            "Health profile" to assessment.healthProfile.name.replace('_', ' '),
            "MAVLink heartbeat" to assessment.communication.name.replace('_', ' '),
            "GPS telemetry" to assessment.gps.name.replace('_', ' '),
            "Battery telemetry" to assessment.battery.name.replace('_', ' '),
            "Autopilot sensors" to assessment.autopilotSensors.name.replace('_', ' ')
        ), "Telemetry evidence is not a health or machine-safety verdict.")
    }

    fun healthBlockers(state: VehicleState): PanelSpec {
        val blockers = HealthAssessmentEvaluator.evaluate(state.forDisplay()).blockers
        return PanelSpec("Readiness gate", "BLOCKED", blockers.mapIndexed { index, blocker ->
            "Requirement ${index + 1}" to blocker
        }, "Configure and validate these inputs before implementing a health verdict.")
    }
}

fun sampleAge(timestamp: Long?, nowEpochMillis: Long = System.currentTimeMillis()): String {
    if (timestamp == null) return "No sample received"
    val seconds = ((nowEpochMillis - timestamp).coerceAtLeast(0L) / 1_000L)
    return "Received ${seconds}s ago"
}

private fun String?.orUnknown(): String = this ?: "UNKNOWN"

private fun Long?.hexMask(): String = this?.let { "0x${it.toString(16).uppercase().padStart(8, '0')}" } ?: "UNKNOWN"

private fun severityName(severity: Int): String = when (severity) {
    0 -> "EMERGENCY"
    1 -> "ALERT"
    2 -> "CRITICAL"
    3 -> "ERROR"
    4 -> "WARNING"
    5 -> "NOTICE"
    6 -> "INFO"
    7 -> "DEBUG"
    else -> "SEVERITY $severity"
}

private fun chargeStateName(chargeState: Int?): String = when (chargeState) {
    0 -> "UNDEFINED"
    1 -> "OK"
    2 -> "LOW"
    3 -> "CRITICAL"
    4 -> "EMERGENCY"
    5 -> "FAILED"
    6 -> "UNHEALTHY"
    7 -> "CHARGING"
    null -> "UNKNOWN"
    else -> "UNKNOWN"
}
