package com.mamadrones.gcs.presentation.parameters

import com.mamadrones.gcs.domain.model.ParameterEntry
import com.mamadrones.gcs.domain.model.ParameterSnapshot
import java.math.BigDecimal

data class HardwareFileField(val name: String, val entry: ParameterEntry?, val hint: String?)
data class HardwareFileSection(
    val id: String,
    val title: String,
    val guidance: String,
    val fields: List<HardwareFileField>,
)

/** Presentation of imported file evidence only. Not a vehicle profile or command configuration.
 * Limited reference labels are not a firmware compatibility check. Unknowns remain explicit.
 */
object ParameterHardwareSummary {
    fun sections(snapshot: ParameterSnapshot): List<HardwareFileSection> {
        val entries = snapshot.entries.associateBy { it.name }
        fun field(name: String): HardwareFileField {
            val entry = entries[name]
            return HardwareFileField(name, entry, entry?.let { hint(name, it.rawValue) })
        }
        fun section(id: String, title: String, guidance: String, names: List<String>) =
            HardwareFileSection(id, title, guidance, names.map(::field))
        return buildList {
            add(section("motor", "Motor signal settings",
                "Raw signal settings. PWM trim is not proof of a physical stop; confirm ESC calibration and motor direction.",
                listOf("MOT_PWM_TYPE", "MOT_PWM_FREQ", "MOT_SAFE_DISARM")))
            // Bounded logical-output overview, not a board connector inventory.
            for (index in 1..32) {
                val names = listOf("FUNCTION", "MIN", "TRIM", "MAX", "REVERSED").map { "SERVO${index}_$it" }
                if (names.any(entries::containsKey)) add(section("output-$index", "Logical output $index",
                    "Function labels describe configured roles, not actual left/right wiring. Confirm the board connector and wheel direction separately.", names))
            }
            for (index in 0..9) {
                val names = listOf("PROTOCOL", "BAUD", "OPTIONS").map { "SERIAL${index}_$it" }
                if (names.any(entries::containsKey)) add(section("serial-$index", "Logical serial port $index",
                    "SERIAL numbering does not prove a TELEM connector or receiver wiring. Baud is shown as the file code, not decoded bits/second.", names))
            }
            add(section("inputs", "Remote input mapping",
                "RC inputs are separate from SERVO outputs. These channel numbers do not identify which motor is connected.",
                listOf("RCMAP_ROLL", "RCMAP_THROTTLE", "RCMAP_YAW", "PILOT_STEER_TYPE", "MODE_CH")))
            add(section("failsafes", "Disconnect and arming review",
                "No safe/unsafe verdict. Nonzero settings do not prove a working failsafe. Override expiry may return control to RC; it does not guarantee stopping.",
                listOf("FS_GCS_ENABLE", "FS_GCS_TIMEOUT", "FS_THR_ENABLE", "FS_THR_VALUE", "FS_TIMEOUT",
                    "FS_ACTION", "RC_OVERRIDE_TIME", "RC_OPTIONS", "ARMING_CHECK", "ARMING_REQUIRE", "BRD_SAFETY_DEFLT")))
            add(section("speed", "Speed settings to verify",
                "Raw values only. Targets and estimates are not a universal speed limiter or proof of the reported 8 km/h limit. Verify units and behavior against the installed firmware.",
                listOf("CRUISE_SPEED", "CRUISE_THROTTLE", "WP_SPEED", "SPEED_MAX")))
        }
    }

    private fun hint(name: String, raw: String): String? {
        val value = exactInteger(raw)
        return when {
            name.matches(Regex("SERVO[0-9]+_FUNCTION")) -> when (value) {
                0 -> "No assigned output function (reference label; not proof the output cannot be driven)"
                26 -> "Ground steering (reference label)"
                70 -> "Throttle (reference label)"
                73 -> "Left throttle (reference label)"
                74 -> "Right throttle (reference label)"
                else -> "Function not interpreted by this summary"
            }
            name.matches(Regex("SERVO[0-9]+_REVERSED")) -> when (value) {
                0 -> "Output reversal not set in file"
                1 -> "Output reversal set in file; physical direction unverified"
                else -> "Reversal value not interpreted"
            }
            name.matches(Regex("SERIAL[0-9]+_PROTOCOL")) -> when (value) {
                -1 -> "Disabled (reference label)"
                1 -> "MAVLink 1 (reference label)"
                2 -> "MAVLink 2 (reference label)"
                5 -> "GPS (reference label)"
                11 -> "Lidar360 (reference label)"
                22 -> "SLCAN (reference label)"
                else -> "Protocol not interpreted by this summary"
            }
            name == "FS_GCS_ENABLE" || name == "FS_THR_ENABLE" -> when (value) {
                0 -> "Disabled in file — hardware-team review required"
                else -> "Behavior not verified; check exact firmware and physical loss-of-link test"
            }
            else -> null
        }
    }

    // Never round a fractional enum, overflow into a known code, or use floating-point equality.
    private fun exactInteger(raw: String): Int? = try {
        if (raw.startsWith("0x", ignoreCase = true)) {
            raw.substring(2).toLongOrNull(16)?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        } else BigDecimal(raw).intValueExact()
    } catch (_: NumberFormatException) { null } catch (_: ArithmeticException) { null }
}
