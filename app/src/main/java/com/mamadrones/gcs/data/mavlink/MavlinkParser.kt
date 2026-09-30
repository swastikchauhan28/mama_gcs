package com.mamadrones.gcs.data.mavlink

/**
 * Incremental MAVLink 2 parser for the receive-only telemetry subset. Unsigned frames are
 * accepted after CRC validation. Signed frames are rejected because this application
 * does not yet provision or verify MAVLink signing keys.
 */
class MavlinkParser {
    private var pending = ByteArray(0)

    fun feed(bytes: ByteArray): List<MavlinkParseResult> {
        if (bytes.isEmpty()) return emptyList()
        val input = pending + bytes
        val results = mutableListOf<MavlinkParseResult>()
        var cursor = 0

        while (cursor < input.size) {
            val magicIndex = findMagic(input, cursor)
            if (magicIndex < 0) {
                cursor = input.size
                break
            }
            cursor = magicIndex
            if (input.size - cursor < HEADER_SIZE) break

            val payloadLength = input[cursor + 1].unsigned()
            val incompatibilityFlags = input[cursor + 2].unsigned()
            val signed = incompatibilityFlags and SIGNED_FLAG != 0
            val frameSize = HEADER_SIZE + payloadLength + CHECKSUM_SIZE + if (signed) SIGNATURE_SIZE else 0
            if (input.size - cursor < frameSize) break

            val unsupportedFlags = incompatibilityFlags and
                SUPPORTED_INCOMPATIBILITY_FLAGS.inv() and SIGNED_FLAG.inv()
            if (unsupportedFlags != 0) {
                results += MavlinkParseResult.UnsupportedIncompatibilityFlags(unsupportedFlags)
                cursor += frameSize
                continue
            }
            if (signed) {
                results += MavlinkParseResult.SignedPacketRejected
                cursor += frameSize
                continue
            }

            val rawFrame = input.copyOfRange(cursor, cursor + frameSize)
            when (val result = parseFrame(rawFrame)) {
                is MavlinkParseResult.InvalidChecksum -> {
                    results += result
                    // Rescan from the next byte so a corrupt length cannot consume a later frame.
                    cursor++
                }
                else -> {
                    results += result
                    cursor += frameSize
                }
            }
        }

        pending = input.copyOfRange(cursor, input.size)
        // The pending suffix is at most one incomplete MAVLink 2 frame (280 bytes).
        check(pending.size <= MAX_FRAME_SIZE)
        return results
    }

    fun reset() {
        pending = ByteArray(0)
    }

    private fun parseFrame(rawFrame: ByteArray): MavlinkParseResult {
        val payloadLength = rawFrame[1].unsigned()
        val messageId = rawFrame[7].unsigned() or
            (rawFrame[8].unsigned() shl 8) or
            (rawFrame[9].unsigned() shl 16)
        val crcExtra = crcExtraFor(messageId)
            ?: return MavlinkParseResult.UnsupportedMessage(messageId)
        val checksumOffset = HEADER_SIZE + payloadLength
        val expectedChecksum = rawFrame[checksumOffset].unsigned() or
            (rawFrame[checksumOffset + 1].unsigned() shl 8)
        val calculatedChecksum = MavlinkChecksum.calculate(
            rawFrame.copyOfRange(1, checksumOffset),
            crcExtra
        )
        if (calculatedChecksum != expectedChecksum) {
            return MavlinkParseResult.InvalidChecksum(messageId)
        }

        val frame = MavlinkFrame(
            sequence = rawFrame[4].unsigned(),
            systemId = rawFrame[5].unsigned(),
            componentId = rawFrame[6].unsigned(),
            messageId = messageId,
            payload = rawFrame.copyOfRange(HEADER_SIZE, checksumOffset)
        )
        return decode(frame)
    }

    private fun decode(frame: MavlinkFrame): MavlinkParseResult = when (frame.messageId) {
        MavlinkMessage.HEARTBEAT_MESSAGE_ID -> {
            if (frame.payload.size != HEARTBEAT_PAYLOAD_SIZE) {
                MavlinkParseResult.MalformedMessage(frame.messageId)
            } else {
                MavlinkParseResult.Message(
                    MavlinkMessage.Heartbeat(
                        systemId = frame.systemId,
                        componentId = frame.componentId,
                        customMode = littleEndianUInt32(frame.payload, 0),
                        vehicleType = frame.payload[4].unsigned(),
                        autopilotType = frame.payload[5].unsigned(),
                        baseMode = frame.payload[6].unsigned(),
                        systemStatus = frame.payload[7].unsigned(),
                        mavlinkVersion = frame.payload[8].unsigned()
                    )
                )
            }
        }
        MavlinkMessage.SYS_STATUS_MESSAGE_ID -> withPayloadLength(frame, SYS_STATUS_LENGTH, SYS_STATUS_LENGTH) {
            MavlinkMessage.SystemStatus(
                systemId = frame.systemId,
                componentId = frame.componentId,
                sensorsPresent = uint32(frame.payload, 0),
                sensorsEnabled = uint32(frame.payload, 4),
                sensorsHealthy = uint32(frame.payload, 8),
                loadDecipercent = uint16(frame.payload, 12),
                voltageMillivolts = uint16(frame.payload, 14),
                currentCentiamps = int16(frame.payload, 16),
                communicationDropCentipercent = uint16(frame.payload, 18),
                communicationErrors = uint16(frame.payload, 20),
                batteryRemainingPercent = frame.payload[30].toInt()
            )
        }
        MavlinkMessage.GPS_RAW_INT_MESSAGE_ID -> withPayloadLength(frame, GPS_RAW_INT_MIN_LENGTH, GPS_RAW_INT_MAX_LENGTH) {
            MavlinkMessage.GpsRawInt(
                systemId = frame.systemId,
                componentId = frame.componentId,
                latitudeE7 = int32(frame.payload, 8),
                longitudeE7 = int32(frame.payload, 12),
                altitudeMillimetersMsl = int32(frame.payload, 16),
                eph = uint16(frame.payload, 20),
                fixType = uint8(frame.payload[28]),
                satellitesVisible = uint8(frame.payload[29])
            )
        }
        MavlinkMessage.ATTITUDE_MESSAGE_ID -> withPayloadLength(frame, ATTITUDE_LENGTH, ATTITUDE_LENGTH) {
            MavlinkMessage.Attitude(
                systemId = frame.systemId,
                componentId = frame.componentId,
                rollRadians = float32(frame.payload, 4),
                pitchRadians = float32(frame.payload, 8),
                yawRadians = float32(frame.payload, 12),
                rollRateRadiansPerSecond = float32(frame.payload, 16),
                pitchRateRadiansPerSecond = float32(frame.payload, 20),
                yawRateRadiansPerSecond = float32(frame.payload, 24)
            )
        }
        MavlinkMessage.GLOBAL_POSITION_INT_MESSAGE_ID -> withPayloadLength(frame, GLOBAL_POSITION_INT_LENGTH, GLOBAL_POSITION_INT_LENGTH) {
            MavlinkMessage.GlobalPositionInt(
                systemId = frame.systemId,
                componentId = frame.componentId,
                latitudeE7 = int32(frame.payload, 4),
                longitudeE7 = int32(frame.payload, 8),
                altitudeMillimetersMsl = int32(frame.payload, 12),
                vxCentimetersPerSecond = int16(frame.payload, 20),
                vyCentimetersPerSecond = int16(frame.payload, 22),
                vzCentimetersPerSecond = int16(frame.payload, 24),
                headingCentidegrees = uint16(frame.payload, 26)
            )
        }
        MavlinkMessage.BATTERY_STATUS_MESSAGE_ID -> withPayloadLength(frame, BATTERY_STATUS_MIN_LENGTH, BATTERY_STATUS_MAX_LENGTH) {
            val voltages = (0 until BATTERY_CELL_COUNT).map { cell -> uint16(frame.payload, BATTERY_VOLTAGES_OFFSET + cell * 2) } +
                (0 until BATTERY_EXT_CELL_COUNT).mapNotNull { cell ->
                    val offset = BATTERY_EXT_VOLTAGES_OFFSET + cell * 2
                    if (offset + 2 <= frame.payload.size) {
                        uint16(frame.payload, offset).takeUnless { it == 0 } ?: UINT16_MAX_VALUE
                    } else null
                }
            MavlinkMessage.BatteryStatus(
                systemId = frame.systemId,
                componentId = frame.componentId,
                batteryId = uint8(frame.payload[32]),
                temperatureCentidegreesCelsius = int16(frame.payload, 8),
                cellVoltagesMillivolts = voltages,
                currentCentiamps = int16(frame.payload, 30),
                remainingPercent = frame.payload[35].toInt(),
                chargeState = if (frame.payload.size > BATTERY_CHARGE_STATE_OFFSET) {
                    uint8(frame.payload[BATTERY_CHARGE_STATE_OFFSET]).takeIf { it in BATTERY_CHARGE_STATE_MIN..BATTERY_CHARGE_STATE_MAX }
                } else null
            )
        }
        MavlinkMessage.STATUSTEXT_MESSAGE_ID -> withPayloadLength(frame, STATUSTEXT_MIN_LENGTH, STATUSTEXT_MAX_LENGTH) {
            MavlinkMessage.StatusText(
                systemId = frame.systemId,
                componentId = frame.componentId,
                severity = uint8(frame.payload[0]),
                textChunk = frame.payload.copyOfRange(1, 51),
                id = if (frame.payload.size >= STATUSTEXT_ID_END) uint16(frame.payload, 51) else 0,
                chunkSequence = if (frame.payload.size >= STATUSTEXT_CHUNK_END) uint8(frame.payload[53]) else 0
            )
        }
        else -> MavlinkParseResult.UnsupportedMessage(frame.messageId)
    }

    private inline fun withPayloadLength(
        frame: MavlinkFrame,
        minimum: Int,
        maximum: Int,
        decode: () -> MavlinkMessage
    ): MavlinkParseResult = if (frame.payload.size !in minimum..maximum) {
        MavlinkParseResult.MalformedMessage(frame.messageId)
    } else {
        MavlinkParseResult.Message(decode())
    }

    private fun crcExtraFor(messageId: Int): Int? = when (messageId) {
        MavlinkMessage.HEARTBEAT_MESSAGE_ID -> HEARTBEAT_CRC_EXTRA
        MavlinkMessage.SYS_STATUS_MESSAGE_ID -> SYS_STATUS_CRC_EXTRA
        MavlinkMessage.GPS_RAW_INT_MESSAGE_ID -> GPS_RAW_INT_CRC_EXTRA
        MavlinkMessage.ATTITUDE_MESSAGE_ID -> ATTITUDE_CRC_EXTRA
        MavlinkMessage.GLOBAL_POSITION_INT_MESSAGE_ID -> GLOBAL_POSITION_INT_CRC_EXTRA
        MavlinkMessage.BATTERY_STATUS_MESSAGE_ID -> BATTERY_STATUS_CRC_EXTRA
        MavlinkMessage.STATUSTEXT_MESSAGE_ID -> STATUSTEXT_CRC_EXTRA
        else -> null
    }

    private fun findMagic(bytes: ByteArray, start: Int): Int {
        for (index in start until bytes.size) {
            if (bytes[index].unsigned() == MAVLINK_V2_MAGIC) return index
        }
        return -1
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun uint8(byte: Byte): Int = byte.unsigned()

    private fun uint16(bytes: ByteArray, offset: Int): Int =
        bytes[offset].unsigned() or (bytes[offset + 1].unsigned() shl 8)

    private fun int16(bytes: ByteArray, offset: Int): Int =
        (uint16(bytes, offset).toShort()).toInt()

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        bytes[offset].unsigned().toLong() or
            (bytes[offset + 1].unsigned().toLong() shl 8) or
            (bytes[offset + 2].unsigned().toLong() shl 16) or
            (bytes[offset + 3].unsigned().toLong() shl 24)

    private fun int32(bytes: ByteArray, offset: Int): Int = uint32(bytes, offset).toInt()

    private fun float32(bytes: ByteArray, offset: Int): Float = Float.fromBits(int32(bytes, offset))

    private fun littleEndianUInt32(bytes: ByteArray, offset: Int): Long =
        bytes[offset].unsigned().toLong() or
            (bytes[offset + 1].unsigned().toLong() shl 8) or
            (bytes[offset + 2].unsigned().toLong() shl 16) or
            (bytes[offset + 3].unsigned().toLong() shl 24)

    private companion object {
        const val MAVLINK_V2_MAGIC = 0xFD
        const val HEADER_SIZE = 10
        const val CHECKSUM_SIZE = 2
        const val SIGNATURE_SIZE = 13
        const val SIGNED_FLAG = 0x01
        const val SUPPORTED_INCOMPATIBILITY_FLAGS = 0
        const val HEARTBEAT_PAYLOAD_SIZE = 9
        const val HEARTBEAT_CRC_EXTRA = 50
        const val SYS_STATUS_LENGTH = 31
        const val SYS_STATUS_CRC_EXTRA = 124
        const val GPS_RAW_INT_MIN_LENGTH = 30
        const val GPS_RAW_INT_MAX_LENGTH = 52
        const val GPS_RAW_INT_CRC_EXTRA = 24
        const val ATTITUDE_LENGTH = 28
        const val ATTITUDE_CRC_EXTRA = 39
        const val GLOBAL_POSITION_INT_LENGTH = 28
        const val GLOBAL_POSITION_INT_CRC_EXTRA = 104
        const val BATTERY_STATUS_MIN_LENGTH = 36
        const val BATTERY_STATUS_MAX_LENGTH = 54
        const val BATTERY_STATUS_CRC_EXTRA = 154
        const val BATTERY_CELL_COUNT = 10
        const val BATTERY_VOLTAGES_OFFSET = 10
        const val BATTERY_EXT_CELL_COUNT = 4
        const val BATTERY_EXT_VOLTAGES_OFFSET = 41
        const val BATTERY_CHARGE_STATE_OFFSET = 40
        const val BATTERY_CHARGE_STATE_MIN = 0
        const val BATTERY_CHARGE_STATE_MAX = 7
        const val UINT16_MAX_VALUE = 65_535
        const val STATUSTEXT_MIN_LENGTH = 51
        const val STATUSTEXT_MAX_LENGTH = 54
        const val STATUSTEXT_CRC_EXTRA = 83
        const val STATUSTEXT_ID_END = 53
        const val STATUSTEXT_CHUNK_END = 54
        const val MAX_FRAME_SIZE = HEADER_SIZE + 255 + CHECKSUM_SIZE + SIGNATURE_SIZE
    }
}

sealed interface MavlinkParseResult {
    data class Message(val message: MavlinkMessage) : MavlinkParseResult
    data class InvalidChecksum(val messageId: Int) : MavlinkParseResult
    data class UnsupportedMessage(val messageId: Int) : MavlinkParseResult
    data class UnsupportedIncompatibilityFlags(val flags: Int) : MavlinkParseResult
    data object SignedPacketRejected : MavlinkParseResult
    data class MalformedMessage(val messageId: Int) : MavlinkParseResult
}
