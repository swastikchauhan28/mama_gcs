package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class IndependentSourceVectorsTest {
    @Test fun independentlyGeneratedSourcesHaveDistinctIdentitiesAndValues() {
        val vectors = requireNotNull(javaClass.getResourceAsStream("/phase111-vectors.txt")).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                val parts = line.split('=', limit = 2)
                parts[0] to parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }
        for ((version, wire) in listOf("v1" to MavlinkWireVersion.V1, "v2" to MavlinkWireVersion.V2)) {
            for ((source, identity) in listOf("other_system" to (99 to 1), "other_component" to (42 to 2))) {
                fun message(name: String): MavlinkMessage {
                    val result = MavlinkParser().feed(vectors.getValue("${version}_${source}_$name")).single() as MavlinkParseResult.Message
                    assertEquals(wire, result.wireVersion)
                    return result.message
                }
                val hb = message("heartbeat") as MavlinkMessage.Heartbeat
                assertEquals(identity.first, hb.systemId); assertEquals(identity.second, hb.componentId)
                val rc = message("rc") as MavlinkMessage.RcChannels
                assertEquals(identity.first, rc.systemId); assertEquals(identity.second, rc.componentId)
                assertEquals(1100, rc.channelsMicroseconds[0]); assertEquals(1900, rc.channelsMicroseconds[1])
                val status = message("status") as MavlinkMessage.SystemStatus
                assertEquals(identity.first, status.systemId); assertEquals(identity.second, status.componentId)
                assertEquals(12000, status.voltageMillivolts); assertEquals(30, status.batteryRemainingPercent)
            }
        }
    }
}
