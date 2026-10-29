package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.*
import org.junit.Test

class IndependentRadioStatusVectorsTest {
    private val vectors = requireNotNull(javaClass.getResourceAsStream("/phase110-vectors.txt")).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val parts = line.split('=', limit = 2)
            parts[0] to parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    @Test fun independentlyEncodedRadioFramesDecodeAndRejectCorruption() {
        for ((prefix, version) in listOf("v1" to MavlinkWireVersion.V1, "v2" to MavlinkWireVersion.V2)) {
            val frame = vectors.getValue("${prefix}_radio_status")
            val result = MavlinkParser().feed(frame).single() as MavlinkParseResult.RadioStatus
            assertEquals(version, result.wireVersion)
            assertEquals(68, result.systemId); assertEquals(68, result.componentId)
            assertEquals(17, result.rxErrors); assertEquals(23, result.fixedPackets)
            assertEquals(95, result.rssi); assertEquals(100, result.remoteRssi)
            assertEquals(80, result.txBufferPercent); assertEquals(4, result.noise); assertEquals(5, result.remoteNoise)
            val bad = frame.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            val results = MavlinkParser().feed(bad)
            assertTrue(results.first() is MavlinkParseResult.InvalidChecksum)
            assertFalse(results.any { it is MavlinkParseResult.RadioStatus || it is MavlinkParseResult.Message })
            for (split in 1 until frame.size) {
                val parser = MavlinkParser()
                assertTrue(parser.feed(frame.copyOfRange(0, split)).isEmpty())
                assertTrue(parser.feed(frame.copyOfRange(split, frame.size)).single() is MavlinkParseResult.RadioStatus)
            }
        }
    }
}
