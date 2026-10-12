package com.mamadrones.gcs.presentation.parameters

import com.mamadrones.gcs.data.parameters.ParameterFileReader
import org.junit.Assert.*
import org.junit.Test

class ParameterHardwareSummaryTest {
    private fun summary(text: String) = ParameterHardwareSummary.sections(ParameterFileReader.read(text.byteInputStream()))

    @Test fun outputRolesFollowFileRatherThanAssumedWiring() {
        val sections = summary("SERVO1_FUNCTION,74\nSERVO2_FUNCTION,73\nSERVO1_TRIM,1500\nSERVO1_REVERSED,1")
        val output = sections.single { it.id == "output-1" }
        assertEquals("Right throttle (reference label)", output.fields.first().hint)
        assertEquals("Left throttle (reference label)", sections.single { it.id == "output-2" }.fields.first().hint)
        assertEquals("1500", output.fields.single { it.name.endsWith("_TRIM") }.entry!!.rawValue)
        assertEquals(3, output.fields.single { it.name.endsWith("_TRIM") }.entry!!.line)
        assertTrue(output.fields.last().hint!!.contains("physical direction unverified"))
        assertNull(output.fields.single { it.name.endsWith("_MIN") }.entry)
    }

    @Test fun missingFieldsDoNotBecomeDefaultsOrDisabled() {
        val sections = summary("UNRELATED,1")
        assertEquals(listOf("motor", "inputs", "failsafes", "speed"), sections.map { it.id })
        assertTrue(sections.flatMap { it.fields }.all { it.entry == null && it.hint == null })
    }

    @Test fun preservesUnknownEnumsWithoutRounding() {
        for (raw in listOf("73.1", "73.000000000000001", "4294967369", "0xFFFFFFFF", "1e-999999999")) {
            val field = summary("SERVO1_FUNCTION,$raw").single { it.id == "output-1" }.fields.first()
            assertEquals(raw, field.entry!!.rawValue)
            assertEquals("Function not interpreted by this summary", field.hint)
        }
    }

    @Test fun exactDecimalExponentAndHexLabelsPreserveRawEvidence() {
        for (raw in listOf("73", "73.0", "7.3e1", "0x49")) {
            val field = summary("SERVO1_FUNCTION,$raw").single { it.id == "output-1" }.fields.first()
            assertEquals(raw, field.entry!!.rawValue)
            assertEquals("Left throttle (reference label)", field.hint)
        }
    }

    @Test fun serialSummaryDoesNotInferPhysicalPortOrDecodeBaud() {
        val sections = summary("SERIAL1_PROTOCOL,2\nSERIAL1_BAUD,57\nSERIAL2_PROTOCOL,11\nSERIAL5_PROTOCOL,999")
        val serial = sections.single { it.id == "serial-1" }
        assertEquals("Logical serial port 1", serial.title)
        assertEquals("MAVLink 2 (reference label)", serial.fields.first().hint)
        assertEquals("57", serial.fields[1].entry!!.rawValue)
        assertNull(serial.fields[1].hint)
        assertEquals("Lidar360 (reference label)", sections.single { it.id == "serial-2" }.fields.first().hint)
        assertEquals("Protocol not interpreted by this summary", sections.single { it.id == "serial-5" }.fields.first().hint)
    }

    @Test fun failsafeLabelsNeverAssertSuccessfulStop() {
        val section = summary("FS_GCS_ENABLE,0\nFS_THR_ENABLE,1\nRC_OVERRIDE_TIME,3").single { it.id == "failsafes" }
        assertEquals("Disabled in file — hardware-team review required", section.fields.first().hint)
        assertTrue(section.fields.single { it.name == "FS_THR_ENABLE" }.hint!!.startsWith("Behavior not verified"))
        assertTrue(section.guidance.contains("does not guarantee stopping"))
        assertNull(section.fields.single { it.name == "RC_OVERRIDE_TIME" }.hint)
    }

    @Test fun boundsAndNumericOrderAreExplicit() {
        val sections = summary("SERVO32_FUNCTION,73\nSERVO2_FUNCTION,74\nSERVO33_FUNCTION,73\nSERIAL9_BAUD,57\nSERIAL10_BAUD,57")
        assertEquals(listOf("output-2", "output-32"), sections.filter { it.id.startsWith("output-") }.map { it.id })
        assertEquals(listOf("serial-9"), sections.filter { it.id.startsWith("serial-") }.map { it.id })
        assertNull(sections.single { it.id == "serial-9" }.fields.first().entry)
    }

    @Test fun repeatedSummariesDoNotRetainPriorFileValuesOrChangeSnapshot() {
        val snapshot = ParameterFileReader.read("SERVO1_FUNCTION,73\nCRUISE_SPEED,4".byteInputStream())
        val original = snapshot.copy(entries = snapshot.entries.toList())
        val first = ParameterHardwareSummary.sections(snapshot)
        val second = summary("SERVO1_FUNCTION,74")
        assertEquals(original, snapshot)
        assertNotEquals(first.single { it.id == "output-1" }, second.single { it.id == "output-1" })
        assertNull(second.single { it.id == "speed" }.fields.first().entry)
        assertEquals("4", first.single { it.id == "speed" }.fields.first().entry!!.rawValue)
    }
}
