package com.mamadrones.gcs.data.mavlink

/**
 * Incremental MAVLink 2 parser for the Phase 3 HEARTBEAT subset. It accepts arbitrary
 * byte chunks, validates known-message CRC extras, and discards malformed frames safely.
 */
class MavlinkParser {
    private val bufferedBytes = ArrayList<Byte>()

    fun feed(bytes: ByteArray): List<MavlinkParseResult> {
        bufferedBytes.addAll(bytes.toList())
        val results = mutableListOf<MavlinkParseResult>()
        while (true) {
            val magicIndex = bufferedBytes.indexOfFirst { (it.toInt() and 0xFF) == MAVLINK_V2_MAGIC }
            if (magicIndex < 0) {
                bufferedBytes.clear()
                return results
            }
            if (magicIndex > 0) bufferedBytes.subList(0, magicIndex).clear()
            if (bufferedBytes.size < HEADER_SIZE) return results

            val payloadLength = unsigned(1)
            val incompatibilityFlags = unsigned(2)
            val signatureLength = if (incompatibilityFlags and SIGNED_FLAG != 0) SIGNATURE_SIZE else 0
            val frameSize = HEADER_SIZE + payloadLength + CHECKSUM_SIZE + signatureLength
            if (bufferedBytes.size < frameSize) return results

            val rawFrame = ByteArray(frameSize) { bufferedBytes[it] }
            bufferedBytes.subList(0, frameSize).clear()
            parseFrame(rawFrame)?.let(results::add)
        }
    }

    private fun parseFrame(rawFrame: ByteArray): MavlinkParseResult? {
        val payloadLength = rawFrame[1].unsigned()
        val messageId = rawFrame[7].unsigned() or (rawFrame[8].unsigned() shl 8) or (rawFrame[9].unsigned() shl 16)
        val crcExtra = crcExtraFor(messageId) ?: return MavlinkParseResult.UnsupportedMessage(messageId)
        val checksumOffset = HEADER_SIZE + payloadLength
        val expectedChecksum = rawFrame[checksumOffset].unsigned() or (rawFrame[checksumOffset + 1].unsigned() shl 8)
        val calculatedChecksum = MavlinkChecksum.calculate(rawFrame.copyOfRange(1, checksumOffset), crcExtra)
        if (calculatedChecksum != expectedChecksum) return MavlinkParseResult.InvalidChecksum(messageId)

        val frame = MavlinkFrame(
            sequence = rawFrame[4].unsigned(), systemId = rawFrame[5].unsigned(), componentId = rawFrame[6].unsigned(),
            messageId = messageId, payload = rawFrame.copyOfRange(HEADER_SIZE, checksumOffset)
        )
        return decode(frame)
    }

    private fun decode(frame: MavlinkFrame): MavlinkParseResult {
        return when (frame.messageId) {
            MavlinkMessage.HEARTBEAT_MESSAGE_ID -> {
                if (frame.payload.size != HEARTBEAT_PAYLOAD_SIZE) return MavlinkParseResult.MalformedMessage(frame.messageId)
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
            else -> MavlinkParseResult.UnsupportedMessage(frame.messageId)
        }
    }

    private fun crcExtraFor(messageId: Int): Int? = when (messageId) {
        MavlinkMessage.HEARTBEAT_MESSAGE_ID -> HEARTBEAT_CRC_EXTRA
        else -> null
    }

    private fun unsigned(index: Int): Int = bufferedBytes[index].toInt() and 0xFF
    private fun Byte.unsigned(): Int = toInt() and 0xFF
    private fun littleEndianUInt32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].unsigned().toLong()) or (bytes[offset + 1].unsigned().toLong() shl 8) or
            (bytes[offset + 2].unsigned().toLong() shl 16) or (bytes[offset + 3].unsigned().toLong() shl 24)

    private companion object {
        const val MAVLINK_V2_MAGIC = 0xFD
        const val HEADER_SIZE = 10
        const val CHECKSUM_SIZE = 2
        const val SIGNATURE_SIZE = 13
        const val SIGNED_FLAG = 0x01
        const val HEARTBEAT_PAYLOAD_SIZE = 9
        const val HEARTBEAT_CRC_EXTRA = 50
    }
}

sealed interface MavlinkParseResult {
    data class Message(val message: MavlinkMessage) : MavlinkParseResult
    data class InvalidChecksum(val messageId: Int) : MavlinkParseResult
    data class UnsupportedMessage(val messageId: Int) : MavlinkParseResult
    data class MalformedMessage(val messageId: Int) : MavlinkParseResult
}
