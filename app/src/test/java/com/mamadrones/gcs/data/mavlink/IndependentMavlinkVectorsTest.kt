package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

/** Fixed PyMAVLink vectors: no production checksum is used to construct test input. */
class IndependentMavlinkVectorsTest {
    private val vectors = requireNotNull(javaClass.getResourceAsStream("/phase109-vectors.txt")).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val parts = line.split('=', limit = 2)
            parts[0] to parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
    private fun decode(name: String): MavlinkParseResult.Message =
        MavlinkParser().feed(vectors.getValue(name)).single() as MavlinkParseResult.Message

    @Test fun independentV1AndV2DecodeExpectedHeartbeatRcAndSystemFields() {
        for ((prefix, version) in listOf("v1" to MavlinkWireVersion.V1, "v2" to MavlinkWireVersion.V2)) {
            val heartbeat = decode("${prefix}_heartbeat")
            assertEquals(version, heartbeat.wireVersion)
            val hb = heartbeat.message as MavlinkMessage.Heartbeat
            assertEquals(42, hb.systemId); assertEquals(1, hb.componentId)
            assertEquals(10, hb.vehicleType); assertEquals(3, hb.autopilotType)
            assertEquals(4L, hb.customMode); assertEquals(0, hb.baseMode); assertFalse(hb.isArmed)
            val rcFrame = decode("${prefix}_rc_channels")
            assertEquals(version, rcFrame.wireVersion)
            val rc = rcFrame.message as MavlinkMessage.RcChannels
            assertEquals(42, rc.systemId); assertEquals(1, rc.componentId); assertEquals(2, rc.channelCount)
            assertEquals(1500, rc.channelsMicroseconds[0]); assertEquals(1700, rc.channelsMicroseconds[1]); assertEquals(200, rc.rssi)
            val statusFrame = decode("${prefix}_sys_status")
            assertEquals(version, statusFrame.wireVersion)
            val status = statusFrame.message as MavlinkMessage.SystemStatus
            assertEquals(42, status.systemId); assertEquals(1, status.componentId)
            assertEquals(7L, status.sensorsPresent); assertEquals(3L, status.sensorsEnabled); assertEquals(1L, status.sensorsHealthy)
            assertEquals(675, status.loadDecipercent); assertEquals(24600, status.voltageMillivolts)
            assertEquals(125, status.currentCentiamps); assertEquals(67, status.batteryRemainingPercent)
            assertEquals(125, status.communicationDropCentipercent); assertEquals(4, status.communicationErrors)
        }
    }

    @Test fun independentlyGeneratedV2TrailingZeroFramesRestoreBaseFields() {
        val rc = decode("v2_rc_zero_rssi")
        assertEquals(MavlinkWireVersion.V2, rc.wireVersion)
        assertEquals(0, (rc.message as MavlinkMessage.RcChannels).rssi)
        val system = decode("v2_sys_zero_remaining")
        assertEquals(MavlinkWireVersion.V2, system.wireVersion)
        assertEquals(0, (system.message as MavlinkMessage.SystemStatus).batteryRemainingPercent)
        assertTrue(vectors.getValue("v2_rc_zero_rssi").size < vectors.getValue("v2_rc_channels").size)
        assertTrue(vectors.getValue("v2_sys_zero_remaining").size < vectors.getValue("v2_sys_status").size)
    }

    @Test fun eachIndependentFrameRejectsCorruptionAndSurvivesEverySplit() {
        for ((_, original) in vectors) {
            val corrupt = original.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            val rejected = MavlinkParser().feed(corrupt)
            assertTrue(rejected.first() is MavlinkParseResult.InvalidChecksum)
            assertFalse(rejected.any { it is MavlinkParseResult.Message })
            for (split in 1 until original.size) {
                val parser = MavlinkParser()
                assertTrue(parser.feed(original.copyOfRange(0, split)).isEmpty())
                assertTrue(parser.feed(original.copyOfRange(split, original.size)).single() is MavlinkParseResult.Message)
            }
        }
    }
}
