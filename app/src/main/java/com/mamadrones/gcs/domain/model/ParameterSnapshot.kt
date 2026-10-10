package com.mamadrones.gcs.domain.model

/** File evidence only. Never used by the vehicle repository or command admission policy. */
data class ParameterSnapshot(val entries: List<ParameterEntry>, val sha256: String)

data class ParameterEntry(val name: String, val rawValue: String, val line: Int) {
    val group: ParameterGroup get() = ParameterGroup.forName(name)
}

enum class ParameterGroup(val title: String, val guidance: String) {
    OUTPUTS("Motor outputs", "Compare SERVO functions, ranges and reversals with the actual wiring. Remote stick values are not ESC calibration."),
    INPUTS("Remote inputs", "Review RC channel mapping and input calibration separately from motor outputs."),
    SERIAL("Telemetry ports", "Review protocol and baud settings. Logical SERIAL numbers must be checked against the board and wiring; they do not prove the VESC data route."),
    SAFETY("Safety & failsafes", "These are file values, not a tested disconnect response or emergency stop. A hardware-team test is still required."),
    SPEED("Speed & steering", "Speed targets and tuning values do not prove enforcement of the reported 8 km/h limit."),
    OTHER("Other", "Values are displayed without firmware-specific interpretation. Supply the exact installed firmware version separately.");

    companion object {
        fun forName(name: String): ParameterGroup = when {
            name.startsWith("SERVO") || name.startsWith("MOT_") -> OUTPUTS
            name.startsWith("RC") -> INPUTS
            name.startsWith("SERIAL") || Regex("SR\\d+_.*").matches(name) || name.startsWith("BRD_SER") -> SERIAL
            name.startsWith("FS_") || name.startsWith("ARMING_") || name.startsWith("BRD_SAFETY") -> SAFETY
            name.startsWith("CRUISE_") || name.startsWith("WP_") || name.startsWith("ATC_") || name.startsWith("PIVOT_") -> SPEED
            else -> OTHER
        }
    }
}
