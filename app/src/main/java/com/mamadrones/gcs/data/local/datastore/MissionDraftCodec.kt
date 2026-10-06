package com.mamadrones.gcs.data.local.datastore

import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.MissionDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned local storage, not a MAVLink/QGC exchange format. Corruption must surface to the UI. */
object MissionDraftCodec {
    fun encode(draft: MissionDraft): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(2)
            out.writeUTF(draft.name)
            out.writeInt(draft.waypoints.size)
            draft.waypoints.forEach { point ->
                out.writeUTF(point.id)
                out.writeDouble(point.latitude)
                out.writeDouble(point.longitude)
            }
            out.writeInt(draft.keepInFence.size)
            draft.keepInFence.forEach { vertex ->
                out.writeUTF(vertex.id)
                out.writeDouble(vertex.latitude)
                out.writeDouble(vertex.longitude)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String): MissionDraft {
        require(encoded.length <= 128_000) { "Draft exceeds local storage bound" }
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            val version = input.readInt()
            require(version in 1..2) { "Unsupported draft version" }
            val name = input.readUTF()
            val count = input.readInt()
            require(count in 0..MissionDraft.MAX_WAYPOINTS)
            val points = List(count) { DraftWaypoint(input.readUTF(), input.readDouble(), input.readDouble()) }
            val fence = if (version >= 2) {
                val vertexCount = input.readInt()
                require(vertexCount in 0..MissionDraft.MAX_FENCE_VERTICES)
                List(vertexCount) { DraftWaypoint(input.readUTF(), input.readDouble(), input.readDouble()) }
            } else emptyList()
            require(input.available() == 0) { "Unexpected draft data" }
            MissionDraft(name, points, fence)
        }
    }
}
