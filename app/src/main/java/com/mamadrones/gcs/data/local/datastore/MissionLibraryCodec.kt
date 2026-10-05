package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.MissionDraft
import com.mamadrones.gcs.domain.model.MissionLibraryEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned device-local library encoding, independent of vehicle mission protocols. */
object MissionLibraryCodec {
    fun encode(entries: List<MissionLibraryEntry>): String {
        require(entries.size <= MissionLibraryEntry.MAX_ENTRIES)
        require(entries.map { it.id }.distinct().size == entries.size)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            entries.forEach { entry ->
                out.writeUTF(entry.id)
                out.writeLong(entry.savedAtEpochMillis)
                out.writeUTF(MissionDraftCodec.encode(entry.draft))
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String): List<MissionLibraryEntry> {
        require(encoded.length <= MAX_ENCODED_CHARS) { "Mission library exceeds storage bounds" }
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            require(input.readInt() == VERSION) { "Unsupported mission library version" }
            val count = input.readInt()
            require(count in 0..MissionLibraryEntry.MAX_ENTRIES) { "Invalid mission library size" }
            val entries = List(count) {
                val id = input.readUTF()
                val savedAt = input.readLong()
                val draft = MissionDraftCodec.decode(input.readUTF())
                MissionLibraryEntry(id, draft, savedAt)
            }
            require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate library entry ID" }
            require(input.available() == 0) { "Unexpected mission library data" }
            entries
        }
    }

    private const val VERSION = 1
    private const val MAX_ENCODED_CHARS = 4_000_000
}
