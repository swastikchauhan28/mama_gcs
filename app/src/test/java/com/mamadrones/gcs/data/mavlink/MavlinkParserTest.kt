package com.mamadrones.gcs.data.mavlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkParserTest {
    @Test
    fun `parses a valid MAVLink 2 armed Rover heartbeat across chunks`() {
        val parser = MavlinkParser()
        val frame = heartbeatFrame(customMode = 10, baseMode = 128)

        assertTrue(parser.feed(frame.copyOfRange(0, 6)).isEmpty())
        val result = parser.feed(frame.copyOfRange(6, frame.size)).single() as MavlinkParseResult.Message
        val heartbeat = result.message as MavlinkMessage.Heartbeat

        assertEquals(1, heartbeat.systemId)
        assertEquals(1, heartbeat.componentId)
        assertEquals(10L, heartbeat.customMode)
        assertTrue(heartbeat.isArmed)
    }

    @Test
    fun `rejects a heartbeat with an invalid checksum`() {
        val frame = heartbeatFrame(customMode = 0, baseMode = 0)
        frame[frame.lastIndex] = (frame.last().toInt() xor 0x01).toByte()

        val result = MavlinkParser().feed(frame).single()

        assertEquals(MavlinkParseResult.InvalidChecksum(0), result)
    }

    @Test
    fun `rejects signed frames when signature verification is not configured`() {
        val unsigned = heartbeatFrame(customMode = 0, baseMode = 0)
        unsigned[2] = 1
        val crcStart = 1
        val crcEndExclusive = unsigned.size - 2
        val checksum = MavlinkChecksum.calculate(unsigned.copyOfRange(crcStart, crcEndExclusive), 50)
        unsigned[unsigned.lastIndex - 1] = checksum.toByte()
        unsigned[unsigned.lastIndex] = (checksum shr 8).toByte()
        val signed = unsigned + ByteArray(13)

        assertEquals(MavlinkParseResult.SignedPacketRejected, MavlinkParser().feed(signed).single())
    }

    @Test
    fun `rejects unknown incompatibility flags`() {
        val frame = heartbeatFrame(customMode = 0, baseMode = 0)
        frame[2] = 0x02

        assertEquals(MavlinkParseResult.UnsupportedIncompatibilityFlags(0x02), MavlinkParser().feed(frame).single())
    }

    @Test
    fun `does not let a corrupt length consume a following valid heartbeat`() {
        val oversizedCandidate = byteArrayOf(0xFD.toByte(), 0xFF.toByte(), 0, 0, 0, 1, 1, 0, 0, 0)
        val valid = heartbeatFrame(customMode = 3, baseMode = 0)
        val bytes = oversizedCandidate + valid + ByteArray(280)

        val result = MavlinkParser().feed(bytes).filterIsInstance<MavlinkParseResult.Message>().single()

        assertEquals(3L, (result.message as MavlinkMessage.Heartbeat).customMode)
    }

    @Test
    fun `reset discards an incomplete frame between sessions`() {
        val parser = MavlinkParser()
        val heartbeat = heartbeatFrame(customMode = 0, baseMode = 0)
        parser.feed(heartbeat.copyOfRange(0, 5))
        parser.reset()

        assertTrue(parser.feed(heartbeat.copyOfRange(5, heartbeat.size)).isEmpty())
        assertFalse(parser.feed(heartbeat).isEmpty())
    }

    @Test
    fun `decodes GPS raw fields and source identity`() {
        val payload = ByteArray(30)
        payload.putInt32(8, 451_234_567)
        payload.putInt32(12, -734_567_890)
        payload.putInt32(16, 123_456)
        payload.putUInt16(20, 125)
        payload.putUInt16(22, 65_535)
        payload[28] = 3
        payload[29] = 12

        val result = MavlinkParser().feed(mavlinkTestFrame(24, payload, 24, systemId = 42, componentId = 1)).single()
        val gps = (result as MavlinkParseResult.Message).message as MavlinkMessage.GpsRawInt

        assertEquals(42, gps.systemId)
        assertEquals(3, gps.fixType)
        assertEquals(12, gps.satellitesVisible)
        assertEquals(125, gps.eph)
    }

    @Test
    fun `decodes global position signed velocity and unknown heading`() {
        val payload = ByteArray(28)
        payload.putInt32(4, -338_654_321)
        payload.putInt32(8, 1_512_345_678)
        payload.putInt32(12, 12_345)
        payload.putInt16(20, -125)
        payload.putInt16(22, 250)
        payload.putUInt16(26, 65_535)

        val result = MavlinkParser().feed(mavlinkTestFrame(33, payload, 104)).single()
        val position = (result as MavlinkParseResult.Message).message as MavlinkMessage.GlobalPositionInt

        assertEquals(-338_654_321, position.latitudeE7)
        assertEquals(-125, position.vxCentimetersPerSecond)
        assertEquals(250, position.vyCentimetersPerSecond)
        assertEquals(65_535, position.headingCentidegrees)
    }

    @Test
    fun `decodes attitude float fields`() {
        val payload = ByteArray(28)
        payload.putFloat32(4, 0.5f)
        payload.putFloat32(8, -0.25f)
        payload.putFloat32(12, 1.0f)

        val message = (MavlinkParser().feed(mavlinkTestFrame(30, payload, 39)).single() as MavlinkParseResult.Message).message
            as MavlinkMessage.Attitude

        assertEquals(0.5f, message.rollRadians)
        assertEquals(-0.25f, message.pitchRadians)
        assertEquals(1.0f, message.yawRadians)
    }

    @Test
    fun `decodes Rover VFR HUD telemetry`() {
        val payload = ByteArray(20)
        payload.putFloat32(0, 0f) // airspeed is not used for a ground rover
        payload.putFloat32(4, 2.75f)
        // Wire order from the generated MAVLink common/vfr_hud definition (not XML order).
        payload.putFloat32(8, 584.25f)
        payload.putFloat32(12, -0.6f)
        payload.putInt16(16, 271)
        payload.putUInt16(18, 42)

        val message = MavlinkParser().feed(mavlinkTestFrame(74, payload, 20)).single()
            .let { (it as MavlinkParseResult.Message).message as MavlinkMessage.VfrHud }

        assertEquals(2.75f, message.groundSpeedMetersPerSecond)
        assertEquals(271, message.headingDegrees)
        assertEquals(42, message.throttlePercent)
        assertEquals(584.25f, message.altitudeMetersMsl)
        assertEquals(-0.6f, message.climbRateMetersPerSecond)
    }

    @Test
    fun `rejects malformed Rover VFR HUD payload length`() {
        val result = MavlinkParser().feed(mavlinkTestFrame(74, ByteArray(21), 20)).single()

        assertEquals(MavlinkParseResult.MalformedMessage(74), result)
    }

    @Test
    fun `decodes system status sentinel battery fields`() {
        val payload = ByteArray(31)
        payload.putUInt32(0, 0xFFFF_FFFFL)
        payload.putUInt32(4, 0x0000_0001L)
        payload.putUInt32(8, 0x0000_0001L)
        payload.putUInt16(12, 675)
        payload.putUInt16(14, 24_600)
        payload.putInt16(16, -1)
        payload.putUInt16(18, 125)
        payload.putUInt16(20, 4)
        payload[30] = 76

        val message = (MavlinkParser().feed(mavlinkTestFrame(1, payload, 124)).single() as MavlinkParseResult.Message).message
            as MavlinkMessage.SystemStatus

        assertEquals(0xFFFF_FFFFL, message.sensorsPresent)
        assertEquals(675, message.loadDecipercent)
        assertEquals(24_600, message.voltageMillivolts)
        assertEquals(-1, message.currentCentiamps)
        assertEquals(76, message.batteryRemainingPercent)
    }

    @Test
    fun `decodes multi-cell battery status and statustext`() {
        val batteryPayload = ByteArray(54)
        batteryPayload.putInt16(8, 2_500)
        batteryPayload.putUInt16(10, 12_000)
        batteryPayload.putUInt16(12, 12_100)
        for (offset in 14..28 step 2) batteryPayload.putUInt16(offset, 65_535)
        batteryPayload.putInt16(30, 325)
        batteryPayload[32] = 2
        batteryPayload[35] = 81
        batteryPayload[40] = 7
        for (offset in 41..47 step 2) batteryPayload.putUInt16(offset, 0)

        val battery = (MavlinkParser().feed(mavlinkTestFrame(147, batteryPayload, 154)).single() as MavlinkParseResult.Message).message
            as MavlinkMessage.BatteryStatus
        assertEquals(2, battery.batteryId)
        assertEquals(2_500, battery.temperatureCentidegreesCelsius)
        assertEquals(12_100, battery.cellVoltagesMillivolts[1])
        assertEquals(81, battery.remainingPercent)
        assertEquals(7, battery.chargeState)

        val textPayload = ByteArray(51)
        textPayload[0] = 4
        "LOW BATTERY".encodeToByteArray().copyInto(textPayload, 1)
        val text = (MavlinkParser().feed(mavlinkTestFrame(253, textPayload, 83)).single() as MavlinkParseResult.Message).message
            as MavlinkMessage.StatusText
        assertEquals(4, text.severity)
        assertEquals("LOW BATTERY", text.textChunk.copyOfRange(0, 11).decodeToString())
        assertEquals(0, text.id)
    }

    @Test
    fun `rejects oversized known message payload`() {
        val result = MavlinkParser().feed(mavlinkTestFrame(33, ByteArray(29), 104)).single()
        assertEquals(MavlinkParseResult.MalformedMessage(33), result)
    }

    @Test fun `restores MAVLink 2 trailing zeros for every supported base payload`() {
        listOf(0 to 50, 1 to 124, 24 to 24, 30 to 39, 33 to 104, 74 to 20, 147 to 154, 253 to 83).forEach { (id, crc) ->
            val result = MavlinkParser().feed(mavlinkTestFrame(id, byteArrayOf(0), crc)).single()
            assertTrue("message $id", result is MavlinkParseResult.Message)
            assertEquals(MavlinkParseResult.MalformedMessage(id),
                MavlinkParser().feed(mavlinkTestFrame(id, byteArrayOf(), crc)).single())
        }
    }

    @Test fun `decodes canonical wire VFR HUD fixture across every BLE split`() {
        // airspeed 0, groundspeed 2.75, altitude 584.25, climb -0.5, heading 271, throttle 42.
        // The last zero byte of uint16 throttle is omitted by MAVLink 2 serialization.
        val payload = "000000000000304000101244000000BF0F012A"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val bytes = mavlinkTestFrame(74, payload, 20)
        for (split in 1 until bytes.size) {
            val parser = MavlinkParser()
            assertTrue(parser.feed(bytes.copyOfRange(0, split)).isEmpty())
            val message = (parser.feed(bytes.copyOfRange(split, bytes.size)).single() as MavlinkParseResult.Message).message as MavlinkMessage.VfrHud
            assertEquals(2.75f, message.groundSpeedMetersPerSecond)
            assertEquals(584.25f, message.altitudeMetersMsl)
            assertEquals(-0.5f, message.climbRateMetersPerSecond)
            assertEquals(271, message.headingDegrees)
            assertEquals(42, message.throttlePercent)
        }
        bytes[11] = 1 // A truncated frame must still pass its original wire CRC.
        assertTrue(MavlinkParser().feed(bytes).first() is MavlinkParseResult.InvalidChecksum)
    }

    @Test fun `decodes short status text and partially transmitted extension id`() {
        val text = byteArrayOf(4) + "READY".encodeToByteArray()
        val short = (MavlinkParser().feed(mavlinkTestFrame(253, text, 83)).single() as MavlinkParseResult.Message).message as MavlinkMessage.StatusText
        assertEquals("READY", short.textChunk.takeWhile { it != 0.toByte() }.toByteArray().decodeToString())
        val extended = text.copyOf(52).also { it[51] = 7 }
        val message = (MavlinkParser().feed(mavlinkTestFrame(253, extended, 83)).single() as MavlinkParseResult.Message).message as MavlinkMessage.StatusText
        assertEquals(7, message.id)
        assertEquals(0, message.chunkSequence)
    }

    @Test fun `restores high zero byte in partially transmitted battery extension cell`() {
        val payload = ByteArray(42).also { it[41] = 100 }
        val message = (MavlinkParser().feed(mavlinkTestFrame(147, payload, 154)).single() as MavlinkParseResult.Message).message as MavlinkMessage.BatteryStatus
        assertEquals(100, message.cellVoltagesMillivolts[10])
    }
}
