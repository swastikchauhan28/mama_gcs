package com.mamadrones.gcs.data.mavlink

/**
 * Incremental MAVLink 2 parser for the Phase 3 HEARTBEAT subset. Unsigned frames are
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
        else -> MavlinkParseResult.UnsupportedMessage(frame.messageId)
    }

    private fun crcExtraFor(messageId: Int): Int? = when (messageId) {
        MavlinkMessage.HEARTBEAT_MESSAGE_ID -> HEARTBEAT_CRC_EXTRA
        else -> null
    }

    private fun findMagic(bytes: ByteArray, start: Int): Int {
        for (index in start until bytes.size) {
            if (bytes[index].unsigned() == MAVLINK_V2_MAGIC) return index
        }
        return -1
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

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
