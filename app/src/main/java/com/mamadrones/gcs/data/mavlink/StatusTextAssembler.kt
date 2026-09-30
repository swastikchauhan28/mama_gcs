package com.mamadrones.gcs.data.mavlink

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Bounded reassembly for MAVLink STATUSTEXT's optional multi-packet chunks. */
internal class StatusTextAssembler(
    private val maxAssemblies: Int = 8,
    private val maxMessageBytes: Int = 1_000,
    private val assemblyTimeoutMillis: Long = 5_000L
) {
    private data class Partial(
        val severity: Int,
        val bytes: ByteArrayOutputStream,
        var nextSequence: Int,
        var updatedAtEpochMillis: Long
    )

    data class Complete(val severity: Int, val text: String)

    private val partials = linkedMapOf<Int, Partial>()

    fun reset() = partials.clear()

    fun append(message: MavlinkMessage.StatusText, nowEpochMillis: Long): Complete? {
        partials.entries.removeAll { nowEpochMillis - it.value.updatedAtEpochMillis > assemblyTimeoutMillis }
        val terminator = message.textChunk.indexOf(0)
        val content = if (terminator >= 0) message.textChunk.copyOfRange(0, terminator) else message.textChunk

        if (message.id == 0) return Complete(message.severity, content.toUtf8())
        if (message.chunkSequence == 0) {
            if (partials.size >= maxAssemblies && message.id !in partials) {
                val oldestId = partials.minByOrNull { it.value.updatedAtEpochMillis }?.key
                if (oldestId != null) partials.remove(oldestId)
            }
            partials[message.id] = Partial(
                severity = message.severity,
                bytes = ByteArrayOutputStream(),
                nextSequence = 0,
                updatedAtEpochMillis = nowEpochMillis
            )
        }

        val partial = partials[message.id] ?: return null
        if (message.chunkSequence != partial.nextSequence || partial.bytes.size() + content.size > maxMessageBytes) {
            partials.remove(message.id)
            return null
        }
        partial.bytes.write(content)
        partial.nextSequence += 1
        partial.updatedAtEpochMillis = nowEpochMillis

        if (terminator < 0) return null
        partials.remove(message.id)
        return Complete(partial.severity, partial.bytes.toByteArray().toUtf8())
    }

    private fun ByteArray.toUtf8(): String = String(this, StandardCharsets.UTF_8)
}
