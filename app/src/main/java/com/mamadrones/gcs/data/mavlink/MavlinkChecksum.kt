package com.mamadrones.gcs.data.mavlink

/** MAVLink X.25 checksum implementation used for MAVLink 2 frame validation. */
object MavlinkChecksum {
    fun calculate(bytes: ByteArray, crcExtra: Int): Int {
        var crc = 0xFFFF
        bytes.forEach { byte -> crc = accumulate(crc, byte.toInt() and 0xFF) }
        crc = accumulate(crc, crcExtra)
        return crc and 0xFFFF
    }

    private fun accumulate(crc: Int, value: Int): Int {
        var tmp = (value xor (crc and 0xFF)) and 0xFF
        tmp = (tmp xor (tmp shl 4)) and 0xFF
        return ((crc shr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp shr 4)) and 0xFFFF
    }
}
