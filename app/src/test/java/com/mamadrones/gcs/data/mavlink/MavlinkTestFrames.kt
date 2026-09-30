package com.mamadrones.gcs.data.mavlink

internal fun mavlinkTestFrame(
    messageId: Int,
    payload: ByteArray,
    crcExtra: Int,
    systemId: Int = 1,
    componentId: Int = 1,
    sequence: Int = 7
): ByteArray {
    val headerAndPayload = byteArrayOf(
        0xFD.toByte(), payload.size.toByte(), 0, 0, sequence.toByte(), systemId.toByte(), componentId.toByte(),
        messageId.toByte(), (messageId shr 8).toByte(), (messageId shr 16).toByte()
    ) + payload
    val checksum = MavlinkChecksum.calculate(headerAndPayload.copyOfRange(1, headerAndPayload.size), crcExtra)
    return headerAndPayload + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
}

internal fun heartbeatFrame(
    customMode: Long = 0,
    baseMode: Int = 0,
    systemId: Int = 1,
    componentId: Int = 1,
    autopilot: Int = 3
): ByteArray {
    val payload = byteArrayOf(
        customMode.toByte(), (customMode shr 8).toByte(), (customMode shr 16).toByte(), (customMode shr 24).toByte(),
        10, autopilot.toByte(), baseMode.toByte(), 4, 3
    )
    return mavlinkTestFrame(MavlinkMessage.HEARTBEAT_MESSAGE_ID, payload, 50, systemId, componentId)
}

internal fun ByteArray.putUInt16(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
}

internal fun ByteArray.putInt16(offset: Int, value: Int) = putUInt16(offset, value)

internal fun ByteArray.putUInt32(offset: Int, value: Long) {
    repeat(4) { index -> this[offset + index] = (value shr (index * 8)).toByte() }
}

internal fun ByteArray.putInt32(offset: Int, value: Int) = putUInt32(offset, value.toLong())

internal fun ByteArray.putFloat32(offset: Int, value: Float) = putInt32(offset, value.toBits())
