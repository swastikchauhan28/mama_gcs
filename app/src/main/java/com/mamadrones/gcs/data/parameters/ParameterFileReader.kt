package com.mamadrones.gcs.data.parameters

import com.mamadrones.gcs.domain.model.ParameterEntry
import com.mamadrones.gcs.domain.model.ParameterSnapshot
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Bounded, all-or-nothing import of the two-column ArduPilot text format. */
object ParameterFileReader {
    const val MAX_BYTES = 512 * 1024
    const val MAX_PARAMETERS = 10_000
    private val namePattern = Regex("[A-Z][A-Z0-9_]{0,15}")
    private val decimalPattern = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val hexPattern = Regex("0[xX][0-9a-fA-F]{1,8}")

    /** Always closes the caller-provided stream, including malformed/oversized files. */
    fun read(stream: InputStream): ParameterSnapshot {
        val bytes = stream.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                // Consume no more than one byte beyond the permitted size.
                val count = it.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                if (count < 0) break
                require(count > 0) { "The document provider stopped returning file data. Try a local copy." }
                output.write(buffer, 0, count)
                require(output.size() <= MAX_BYTES) { "Parameter file exceeds 512 KiB." }
            }
            output.toByteArray()
        }
        val content = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Use a UTF-8 text parameter file.")
        }
        require(content.none { it.isISOControl() && it !in "\r\n\t" }) { "The file contains unsupported control characters." }
        val entries = mutableListOf<ParameterEntry>()
        val names = mutableSetOf<String>()
        content.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, original ->
            require(original.length <= 1024) { "Line ${index + 1} is too long (maximum 1024 characters)." }
            val line = original.substringBefore('#').trim()
            if (line.isNotEmpty()) {
                val fields = if (',' in line) line.split(',').map(String::trim) else line.split(Regex("\\s+"))
                require(fields.size == 2) { "Line ${index + 1}: expected NAME,VALUE or NAME VALUE. QGC five-column files are not supported." }
                val (name, value) = fields
                require(namePattern.matches(name)) { "Line ${index + 1}: invalid parameter name (uppercase, up to 16 characters)." }
                require(value.length <= 64 && (hexPattern.matches(value) ||
                    (decimalPattern.matches(value) && value.toDoubleOrNull()?.isFinite() == true))) {
                    "Line ${index + 1}: expected a finite decimal or a 32-bit hexadecimal value."
                }
                require(names.add(name)) { "Line ${index + 1}: duplicate parameter $name. No values were imported." }
                require(entries.size < MAX_PARAMETERS) { "Too many parameters (maximum $MAX_PARAMETERS)." }
                entries += ParameterEntry(name, value, index + 1)
            }
        }
        require(entries.isNotEmpty()) { "No parameters found in the selected file." }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return ParameterSnapshot(entries.sortedBy { it.name }, digest)
    }
}
