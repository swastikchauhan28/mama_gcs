package com.mamadrones.gcs.data.rtk

import com.mamadrones.gcs.data.mavlink.MavlinkChecksum

/** The sole outbound MAVLink encoder: GPS_RTCM_DATA (233), MAVLink 2, CRC extra 35. */
class MavlinkRtcmEncoder {
    private var packetSequence = 0
    private var correctionSequence = 0

    fun encode(frame: Rtcm3Frame): List<ByteArray> {
        val bytes = frame.bytes
        require(bytes.isNotEmpty() && bytes.size <= 720)
        val fragmented = bytes.size > 180
        val sequence = correctionSequence++ and 31
        return bytes.asList().chunked(180).mapIndexed { index, slice ->
            val flags = if (fragmented) 1 or (index shl 1) or (sequence shl 3) else 0
            val packet = ByteArray(194)
            packet[0] = 0xfd.toByte()
            packet[1] = 182.toByte()
            packet[4] = (packetSequence++ and 0xff).toByte()
            packet[5] = 255.toByte() // Ground station system ID; never impersonate the vehicle.
            packet[6] = 190.toByte() // MAV_COMP_ID_MISSIONPLANNER.
            packet[7] = 233.toByte()
            packet[10] = flags.toByte()
            packet[11] = slice.size.toByte()
            slice.forEachIndexed { offset, value -> packet[12 + offset] = value }
            val crc = MavlinkChecksum.calculate(packet.copyOfRange(1, 192), 35)
            packet[192] = crc.toByte()
            packet[193] = (crc shr 8).toByte()
            packet
        }
    }
}
