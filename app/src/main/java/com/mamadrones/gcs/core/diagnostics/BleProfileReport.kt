package com.mamadrones.gcs.core.diagnostics

import com.mamadrones.gcs.data.transport.bluetooth.BleNotifyCharacteristic
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/** Explicit snapshot of eligible receive options, not a full GATT database or protocol detector. */
object BleProfileReport {
    const val MAX_OPTIONS = 64
    const val MAX_BYTES = 24 * 1024
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun capture(options: List<BleNotifyCharacteristic>, connectionNumber: Long, capturedAt: Long): String = buildString {
        require(connectionNumber > 0)
        appendLine("MAMA GCS - BLE RECEIVE PROFILE v1")
        appendLine("Captured UTC: ${Instant.ofEpochMilli(capturedAt)}")
        appendLine("Process-local connection number: $connectionNumber (not a device identifier)")
        appendLine("Frozen discovery snapshot; it is not current connection status.")
        appendLine("Eligible receive options: ${options.size}; included: ${options.size.coerceAtMost(MAX_OPTIONS)}")
        if (options.size > MAX_OPTIONS) appendLine("TRUNCATED: only the first $MAX_OPTIONS options are included.")
        appendLine("Only notify/indicate characteristics with a notification configuration descriptor are listed.")
        if (options.isEmpty()) appendLine("No eligible receive options discovered. This does not prove that the device has no data interface.")
        options.take(MAX_OPTIONS).forEachIndexed { index, option ->
            appendLine()
            appendLine("OPTION ${index + 1}")
            appendLine("Service UUID: ${safeUuid(option.serviceUuid)}")
            appendLine("Characteristic UUID: ${safeUuid(option.characteristicUuid)}")
            appendLine("Service instance: ${option.serviceInstanceId}; characteristic instance: ${option.characteristicInstanceId}")
            appendLine("App receive method: ${if (option.supportsIndication) "INDICATE" else "NOTIFY"}")
        }
        appendLine()
        appendLine("ASK THE HARDWARE TEAM")
        appendLine("Which exact service/characteristic carries serial telemetry, and is its payload MAVLink or another protocol?")
        appendLine("Does the remote require the R12/Cube to be present before it sends notification data?")
        appendLine("This report does not subscribe, probe payloads, or verify MAVLink, VESC, Cube/R12 reception or rover control.")
        appendLine("Names, Bluetooth addresses, payloads, account data and location are excluded. UUIDs describe the interface and may reveal its vendor/profile; review before sharing.")
        appendLine("Associate this file with the intended remote manually. It cannot establish remote identity, firmware or safety.")
        appendLine("Snapshot is memory-only until explicitly saved. Share manually; no automatic upload.")
    }

    private fun safeUuid(value: String): String = if (uuid.matches(value)) value.lowercase() else "INVALID UUID (omitted)"

    fun write(content: String, openStream: () -> OutputStream?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        (openStream() ?: throw IOException("No document stream")).use { it.write(bytes); it.flush() }
    }
}
