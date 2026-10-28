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
fun Double?.coordinateReading(): String = if (this == null || !isFinite()) "UNKNOWN" else String.format(Locale.US, "%.7f °", this)

object ConsolePanels {
    fun mavlinkDiagnostics(sample: MavlinkDiagnostics, nowEpochMillis: Long = System.currentTimeMillis()): PanelSpec {
        val started = sample.startedAtEpochMillis != null
        fun count(value: Long) = if (started) value.toString() else "—"
        val status = when {
            !started -> "NO MAVLINK SESSION"
            sample.active -> "ACTIVE SESSION · ${sample.linkKind}"
            else -> "LAST SESSION · ${sample.linkKind} · CLOSED"
        }
        return PanelSpec("MAVLink decoder", status, listOf(
            "Input chunks" to count(sample.receivedChunks),
            "Input bytes" to count(sample.receivedBytes),
            "Raw 0xFE bytes (MAVLink 1 start candidates)" to count(sample.mavlinkV1StartBytes),
            "Raw 0xFD bytes (MAVLink 2 start candidates)" to count(sample.mavlinkV2StartBytes),
            "Decoded supported messages" to count(sample.decodedMessages),
            "Decoded MAVLink 1" to count(sample.decodedV1Messages),
            "Decoded MAVLink 2" to count(sample.decodedV2Messages),
            "Validated RADIO_STATUS (radio only)" to count(sample.validatedRadioStatusFrames),
            "Latest radio status" to sampleAge(sample.latestRadioStatus?.receivedAtEpochMillis, nowEpochMillis),
            "Radio reported source" to (sample.latestRadioStatus?.let { "system ${it.systemId} · component ${it.componentId}" } ?: "—"),
            "Radio RSSI / remote RSSI (raw)" to (sample.latestRadioStatus?.let { "${it.rssi} / ${it.remoteRssi}" } ?: "—"),
            "Radio TX buffer free (reported %)" to (sample.latestRadioStatus?.txBufferPercent?.toString() ?: "—"),
            "Radio noise / remote noise (raw)" to (sample.latestRadioStatus?.let { "${it.noise} / ${it.remoteNoise}" } ?: "—"),
            "Radio RX errors / corrected packets (since radio boot)" to
                (sample.latestRadioStatus?.let { "${it.rxErrors} / ${it.fixedPackets}" } ?: "—"),
            "Accepted from selected autopilot" to count(sample.acceptedMessages),
            "Ignored before autopilot selection" to count(sample.ignoredBeforeHeartbeat),
            "Ignored other source" to count(sample.ignoredOtherSource),
            "Parser errors (CRC + payload)" to count(sample.parserErrors),
            "Checksum failures" to count(sample.checksumFailures),
            "Malformed payloads" to count(sample.malformedPayloads),
            "Unverified unsupported-ID candidates" to count(sample.unsupportedMessages),
            "Last unsupported-ID candidate" to (sample.lastUnsupportedMessageId?.toString() ?: "—"),
            "Unsupported flags" to count(sample.unsupportedFlags),
            "Signed packets rejected" to count(sample.signedPacketsRejected),
            "Last input" to sampleAge(sample.lastBytesAtEpochMillis, nowEpochMillis),
            "Last accepted message" to sampleAge(sample.lastAcceptedAtEpochMillis, nowEpochMillis),
        ), note = "RADIO_STATUS is checksum-validated radio-only evidence, not a Cube heartbeat, RC sample, authenticated sender or vehicle-health verdict. Radio RSSI/noise scales are device-specific; error counters may wrap. " +
            "Session totals are not packet-loss rates. Decoded supported messages excludes radio-only frames and includes vehicle messages ignored by source selection. " +
            "Raw start-byte counts are only byte-pattern candidates, not validated frames; arbitrary data can contain them. " +
            "Unsupported/signed candidates are not checksum/authentication verified. Raw noise and incomplete fragments may produce no parser result. " +
            "Counters remain in memory after disconnect and reset on the next session. Prepare telemetry report allows explicit snapshot export; there is no continuous logging.")
    }

    fun mavlinkDiagnosticHints(sample: MavlinkDiagnostics): String = buildList {
        if (sample.startedAtEpochMillis == null) add("Start UDP, BLE or Bluetooth Classic MAVLink receive to collect decoder diagnostics.")
        else if (!sample.active) add("These are the last session's counters, not a live connection. Open a new receive session to retry.")
        if (sample.startedAtEpochMillis != null && sample.receivedBytes == 0L)
            add("No bytes reached the decoder. Check the selected UDP peer, BLE characteristic or Classic serial radio and sender output.")
        if (sample.receivedBytes > 0L && sample.decodedMessages == 0L && sample.validatedRadioStatusFrames == 0L)
            add("Bytes arrived, but no supported message was decoded. The stream may be incomplete, unsupported, or not MAVLink 1/2; confirm the radio route and format.")
        if (sample.validatedRadioStatusFrames > 0L && sample.decodedMessages == 0L)
            add("Checksum-valid RADIO_STATUS arrived from the radio, but no supported Cube message was decoded. Radio status alone cannot confirm a Cube heartbeat, RC values or the failing hardware hop.")
        if (sample.receivedBytes > 0L && sample.decodedMessages == 0L &&
            sample.mavlinkV1StartBytes + sample.mavlinkV2StartBytes == 0L)
            add("No MAVLink 1/2 start bytes (0xFE/0xFD) appeared in this captured stream. Check the serial forwarding format; this does not locate a wiring fault.")
        if (sample.receivedBytes > 0L && sample.decodedMessages == 0L &&
            sample.mavlinkV1StartBytes + sample.mavlinkV2StartBytes > 0L)
            add("MAVLink start-byte patterns appeared, but no supported frame validated. These bytes may occur in other data; inspect the serial route and framing without assuming a valid MAVLink stream.")
        if (sample.checksumFailures > 0L || sample.malformedPayloads > 0L)
            add("Corrupt or incompatible frame candidates were rejected. Check framing, sender settings and the telemetry bridge; these counters do not prove a particular hardware fault.")
        if (sample.signedPacketsRejected > 0L)
            add("Signed candidates were rejected because signing verification is not implemented. Do not disable a live rover's signing to work around this; use an approved test setup.")
        if (sample.unsupportedMessages > 0L || sample.unsupportedFlags > 0L)
            add("Some candidates are outside the supported message/flag subset. This can coexist with normal telemetry.")
        if (sample.ignoredBeforeHeartbeat > 0L)
            add("Messages arrived before a valid autopilot was selected, including any non-autopilot or invalid-source heartbeats. Confirm that the Cube's heartbeat reaches this link.")
        if (sample.ignoredOtherSource > 0L)
            add("Messages from another system/component were ignored. Selection stays pinned until a new session; check the sender routing.")
        if (sample.acceptedMessages > 0L)
            add("Messages have been accepted in this session. Use current heartbeat status and receive ages to judge liveness; acceptance does not authenticate the vehicle or enable commands.")
    }.joinToString("\n\n")

    fun vehicle(state: VehicleState) = state.forDisplay().let { s -> PanelSpec("Vehicle", s.displayName ?: s.systemId?.let { "OBSERVED SYSTEM $it · NOT PAIRED" } ?: "NO LIVE VEHICLE", listOf(
        "Speed" to s.speedMetersPerSecond.reading("m/s"), "Heading" to s.headingDegrees.reading("°"),
        "VFR_HUD ground speed" to s.roverHud.groundSpeedMetersPerSecond.reading("m/s"),
        "VFR_HUD heading" to s.roverHud.headingDegrees.reading("°"),
        "VFR_HUD altitude MSL" to s.roverHud.altitudeMetersMsl.reading("m"),
        "Throttle" to s.roverHud.throttlePercent?.let { "$it %" }.orUnknown(),
        "Climb rate" to s.roverHud.climbRateMetersPerSecond.reading("m/s"),
        "Direction" to s.direction.name, "Mode" to (s.mode ?: "UNKNOWN"),
        "Armed" to (s.armed?.let { if (it) "ARMED" else "DISARMED" } ?: "UNKNOWN")
    ), "${sampleAge(s.kinematicsLastUpdatedAtEpochMillis)} · VFR_HUD ${sampleAge(s.roverHud.lastUpdatedAtEpochMillis)}") }
    fun gps(state: VehicleState) = state.forDisplay().gps.let { s -> PanelSpec("GPS", s.fix.name.replace('_', ' '), listOf(
        "Satellites" to (s.satellites?.toString() ?: "UNKNOWN"), "HDOP" to s.hdop.reading(""),
        "Latitude" to (s.latitude?.toString() ?: "UNKNOWN"), "Longitude" to (s.longitude?.toString() ?: "UNKNOWN")
    ), sampleAge(s.lastUpdatedAtEpochMillis)) }
    fun position(state: VehicleState) = state.forDisplay().position.let { s -> PanelSpec("Global position", if (s.latitude != null && s.longitude != null) "RECEIVED" else "UNKNOWN", listOf(
        "Latitude" to s.latitude.coordinateReading(), "Longitude" to s.longitude.coordinateReading(),
        "Altitude MSL" to s.altitudeMetersMsl.reading("m")
    ), sampleAge(s.lastUpdatedAtEpochMillis)) }
    fun battery(state: VehicleState, nowElapsedMillis: Long): PanelSpec {
        val displayed = state.forDisplay()
        fun status(sample: BatteryState) = BatteryEvidence.freshness(sample, state.connected, nowElapsedMillis)
        fun rows(label: String, sample: BatteryState): List<Pair<String, String>> {
            val freshness = status(sample)
            val current = freshness == BatteryFreshness.FRESH
            return listOf(
                "$label freshness" to freshness.name,
                "$label remaining" to (if (current) sample.percentage?.let { "$it %" } ?: "UNKNOWN" else "UNKNOWN"),
                "$label voltage" to (if (current) sample.voltage.reading("V") else "UNKNOWN"),
                "$label current" to (if (current) sample.currentAmps.reading("A") else "UNKNOWN"),
                "$label temperature" to (if (current) sample.temperatureCelsius.reading("°C") else "UNKNOWN"),
                "$label charge state" to (if (current) chargeStateName(sample.chargeState) else "UNKNOWN")
            )
        }
        return PanelSpec(
            "Battery telemetry", "${displayed.batteries.size} PACKS REPORTED",
            rows("System summary (SYS_STATUS)", displayed.battery) +
                displayed.batteries.flatMap { rows("Pack ${it.batteryId} (BATTERY_STATUS)", it) },
            "Each source expires independently after 10 seconds without receipt. This diagnostic window is not a battery-health or safety assessment; SYS_STATUS is an aggregate and may be ambiguous."
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
    fun motors(state: VehicleState) = PanelSpec("VESC motors", if (state.motors.isEmpty()) "TELEMETRY UNAVAILABLE" else "${state.motors.size} PROVISIONED", listOf(
        "Motor temperature" to "UNKNOWN", "Controller temperature" to "UNKNOWN", "Fault state" to "UNKNOWN"
    ), "Controller inventory and a validated telemetry route are not configured in this build.")
    fun motor(motor: MotorState): PanelSpec {
        val live = motor.connection == SubsystemConnection.CONNECTED && motor.lastUpdatedAtEpochMillis != null
        val sample = if (live) motor else MotorState(motor.id)
        val status = if (!live && motor.connection == SubsystemConnection.CONNECTED) "UNKNOWN" else motor.connection.name.replace('_', ' ')
        return PanelSpec("Motor ${motor.id}", status, listOf(
            "Motor temperature" to sample.temperatureCelsius.reading("°C"), "Controller temperature" to sample.controllerTemperatureCelsius.reading("°C"),
            "Mechanical RPM" to sample.rpm.reading("rpm"), "Electrical RPM" to sample.electricalRpm.reading("erpm"),
            "Motor current" to sample.motorCurrentAmps.reading("A"), "Input current" to sample.inputCurrentAmps.reading("A"),
            "Input voltage" to sample.voltage.reading("V"), "Duty ratio" to sample.dutyCycle.reading(""), "Fault" to (sample.faultCode ?: "UNKNOWN")
        ), if (live) sampleAge(motor.lastUpdatedAtEpochMillis) else "No current VESC measurement · ${sampleAge(motor.lastUpdatedAtEpochMillis)}")
    }
    fun spray(state: VehicleState) = state.forDisplay().spray.let { s -> PanelSpec("Spray system", s.connection.name.replace('_', ' '), listOf(
        "Pump" to s.pump.power.name, "Spraying" to s.spraying.name,
        "Nozzle count" to (s.nozzles?.size?.toString() ?: "UNKNOWN"), "Pressure" to s.pressureBar.reading("bar"),
        "Flow" to s.flowLitersPerMinute.reading("L/min"),
        "Pump fault" to (s.pump.fault ?: "UNKNOWN"), "System fault" to (s.fault ?: "UNKNOWN")
    ) + s.nozzles.orEmpty().map { "Nozzle ${it.id}" to it.power.name }) }
    fun hydraulic(state: VehicleState) = state.forDisplay().hydraulic.let { s -> PanelSpec("Hydraulic system", s.connection.name.replace('_', ' '), listOf(
        "Enabled" to s.enabled.name, "Pump" to s.pump.name, "Valve" to s.valve.name,
        "Pressure" to s.pressureBar.reading("bar"), "Temperature" to s.temperatureCelsius.reading("°C"),
        "Fault" to (s.fault ?: "UNKNOWN")
    )) }
    fun health(state: VehicleState, nowElapsedMillis: Long): PanelSpec {
        val assessment = HealthAssessmentEvaluator.evaluate(state.forDisplay())
        // Unlike live measurements, retained evidence remains explicitly historical after link loss.
        val retainedBatteryEvidence = HealthAssessmentEvaluator.evaluate(state).battery
        val batteryFreshnessRows = listOf(
            "System battery summary freshness" to BatteryEvidence.freshness(state.battery, state.connected, nowElapsedMillis).name
        ) + state.batteries.map { sample ->
            "Battery pack ${sample.batteryId} freshness" to BatteryEvidence.freshness(sample, state.connected, nowElapsedMillis).name
        }
        return PanelSpec("Operational readiness", assessment.readiness.name.replace('_', ' '), listOf(
            "Health profile" to assessment.healthProfile.name.replace('_', ' '),
            "MAVLink heartbeat" to assessment.communication.name.replace('_', ' '),
            "GPS telemetry" to assessment.gps.name.replace('_', ' '),
            "Battery evidence (latest retained)" to retainedBatteryEvidence.name.replace('_', ' '),
        ) + batteryFreshnessRows + listOf(
            "Autopilot sensors" to assessment.autopilotSensors.name.replace('_', ' ')
        ), "Battery evidence is from the latest retained samples, not a fault history or current diagnosis. Freshness means recent receipt only; no health or machine-safety verdict is provided.")
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
