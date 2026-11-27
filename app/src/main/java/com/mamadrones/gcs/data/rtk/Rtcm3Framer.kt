package com.mamadrones.gcs.data.rtk

/** An immutable, CRC-checked complete RTCM3 frame. Raw bytes are never persisted. */
class Rtcm3Frame private constructor(private val content: ByteArray) {
    val bytes: ByteArray get() = content.copyOf()

    companion object {
        internal fun validated(bytes: ByteArray): Rtcm3Frame {
            require(bytes.size in 6..720 && (bytes[0].toInt() and 0xff) == 0xd3)
            require((bytes[1].toInt() and 0xfc) == 0)
            require(((((bytes[1].toInt() and 3) shl 8) or (bytes[2].toInt() and 0xff)) + 6) == bytes.size)
            val actual = ((bytes[bytes.size - 3].toInt() and 0xff) shl 16) or
                ((bytes[bytes.size - 2].toInt() and 0xff) shl 8) or (bytes.last().toInt() and 0xff)
            require(Rtcm3Framer.crc24q(bytes, bytes.size - 3) == actual)
            return Rtcm3Frame(bytes.copyOf())
        }
    }
}

/** Incremental RTCM3 framing with a fixed 1029-byte upper bound and byte-wise resync. */
class Rtcm3Framer {
    private val pending = ByteArray(1029)
    private var size = 0
    var rejectedFrames: Long = 0
        private set

    fun feed(chunk: ByteArray): List<Rtcm3Frame> {
        val found = ArrayList<Rtcm3Frame>()
        for (byte in chunk) {
            if (size == pending.size) discardFirst()
            pending[size++] = byte
            while (size >= 3) {
                if ((pending[0].toInt() and 0xff) != 0xd3 || (pending[1].toInt() and 0xfc) != 0) {
                    discardFirst()
                    continue
                }
                val payloadLength = ((pending[1].toInt() and 3) shl 8) or (pending[2].toInt() and 0xff)
                val frameLength = payloadLength + 6
                if (size < frameLength) break
                val expected = ((pending[frameLength - 3].toInt() and 0xff) shl 16) or
                    ((pending[frameLength - 2].toInt() and 0xff) shl 8) or
                    (pending[frameLength - 1].toInt() and 0xff)
                if (crc24q(pending, frameLength - 3) == expected && frameLength <= 720) {
                    found += Rtcm3Frame.validated(pending.copyOf(frameLength))
                    pending.copyInto(pending, 0, frameLength, size)
                    size -= frameLength
                } else {
                    rejectedFrames++
                    // A failed declared frame cannot be forwarded; skip it in full rather than
                    // treating a D3 byte inside its payload as a new unbounded candidate.
                    pending.copyInto(pending, 0, frameLength, size)
                    size -= frameLength
                }
            }
        }
        return found
    }

    private fun discardFirst() {
        pending.copyInto(pending, 0, 1, size)
        size--
    }

    companion object {
        fun crc24q(bytes: ByteArray, length: Int = bytes.size): Int {
            var crc = 0
            for (index in 0 until length) {
                crc = crc xor ((bytes[index].toInt() and 0xff) shl 16)
                repeat(8) {
                    crc = ((crc shl 1) xor (if (crc and 0x800000 != 0) 0x864cfb else 0)) and 0xffffff
                }
            }
            return crc
        }
    }
}
