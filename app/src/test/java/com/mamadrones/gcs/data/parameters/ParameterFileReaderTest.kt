package com.mamadrones.gcs.data.parameters

import com.mamadrones.gcs.domain.model.ParameterGroup
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class ParameterFileReaderTest {
    private fun read(text: String) = ParameterFileReader.read(text.byteInputStream())
    private fun rejects(text: String) {
        assertThrows(IllegalArgumentException::class.java) { read(text) }
    }

    @Test fun importsCommaSpaceTabCommentsBomAndCrLf() {
        val result = read("\uFEFF# Export\r\nSERVO2_FUNCTION,73\r\nSERVO1_FUNCTION 74 # right\r\nRC1_TRIM\t1500\r\n")
        assertEquals(listOf("RC1_TRIM", "SERVO1_FUNCTION", "SERVO2_FUNCTION"), result.entries.map { it.name })
        assertEquals("74", result.entries[1].rawValue)
        assertEquals(3, result.entries[1].line)
    }

    @Test fun preservesNumericTextWithoutApplyingUnitsOrDefaults() {
        val result = read("A -1.20e-3\nB +1500\nC 0xFFFFFFFF\nD .5\nE 2.")
        assertEquals(listOf("-1.20e-3", "+1500", "0xFFFFFFFF", ".5", "2."), result.entries.map { it.rawValue })
    }

    @Test fun rejectsAllDuplicatesEvenWhenValuesAgree() {
        rejects("SERVO1_FUNCTION 74\nSERVO1_FUNCTION 73")
        rejects("SERVO1_FUNCTION 74\nSERVO1_FUNCTION 74")
    }

    @Test fun rejectsNonFiniteAndInvalidValues() {
        listOf("NaN", "Infinity", "1e999", "0x100000000", "1,5", "1.0f", "", "two").forEach { rejects("A $it") }
    }

    @Test fun rejectsExtraColumnsAndQgcFormat() {
        rejects("1\t1\tSERVO1_FUNCTION\t74\t9")
        rejects("SERVO1_FUNCTION,74,9")
        rejects("A,,1")
        rejects("A 1 B")
    }

    @Test fun rejectsInvalidNamesEmptyAndBinary() {
        listOf("", "# comment only", "lower 1", "123 1", "ABCDEFGHIJKLMNOPQ 1", "A-B 1", "A 1\u0000").forEach(::rejects)
        assertThrows(IllegalArgumentException::class.java) {
            ParameterFileReader.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
    }

    @Test fun groupsRelevantValuesWithoutAssessingSafety() {
        val expected = mapOf("SERVO2_FUNCTION" to ParameterGroup.OUTPUTS, "MOT_PWM_TYPE" to ParameterGroup.OUTPUTS,
            "RCMAP_THROTTLE" to ParameterGroup.INPUTS, "SERIAL2_PROTOCOL" to ParameterGroup.SERIAL,
            "BRD_SER2_RTSCTS" to ParameterGroup.SERIAL, "SR2_POSITION" to ParameterGroup.SERIAL,
            "FS_ACTION" to ParameterGroup.SAFETY, "ARMING_CHECK" to ParameterGroup.SAFETY,
            "BRD_SAFETY_DEFLT" to ParameterGroup.SAFETY, "CRUISE_SPEED" to ParameterGroup.SPEED,
            "WP_SPEED" to ParameterGroup.SPEED, "SYSID_THISMAV" to ParameterGroup.OTHER)
        expected.forEach { (name, group) -> assertEquals(name, group, ParameterGroup.forName(name)) }
    }

    @Test fun rejectsOversizeAndClosesStream() {
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(ParameterFileReader.MAX_BYTES + 1) { 32 }) {
            override fun close() { closed = true; super.close() }
        }
        assertThrows(IllegalArgumentException::class.java) { ParameterFileReader.read(stream) }
        assertTrue(closed)
    }

    @Test fun acceptsByteLimitAndClosesSuccessfulStream() {
        val text = "A 1\n" + "\n".repeat(ParameterFileReader.MAX_BYTES - 4)
        var closed = false
        val stream = object : ByteArrayInputStream(text.toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        assertEquals(1, ParameterFileReader.read(stream).entries.size)
        assertTrue(closed)
    }

    @Test fun limitsLinesAndEntryCount() {
        rejects("#" + "a".repeat(1024))
        val maximum = (1..ParameterFileReader.MAX_PARAMETERS).joinToString("\n") { "P$it 1" }
        assertEquals(ParameterFileReader.MAX_PARAMETERS, read(maximum).entries.size)
        rejects("$maximum\nEXTRA 1")
    }

    @Test fun fingerprintTracksExactBytes() {
        assertEquals(64, read("A 1").sha256.length)
        assertEquals(read("A 1").sha256, read("A 1").sha256)
        assertNotEquals(read("A 1").sha256, read("A 1\n").sha256)
    }
}
