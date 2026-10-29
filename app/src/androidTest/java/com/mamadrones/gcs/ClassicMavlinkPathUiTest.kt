package com.mamadrones.gcs

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mamadrones.gcs.data.mavlink.MavlinkChecksum
import com.mamadrones.gcs.data.mavlink.MavlinkMessageRouter
import com.mamadrones.gcs.data.mavlink.MavlinkParser
import com.mamadrones.gcs.data.mavlink.MavlinkSession
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import com.mamadrones.gcs.domain.model.*
import com.mamadrones.gcs.presentation.components.ClassicConnectionCheckPanel
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Offline synthetic frames through the real parser/session/repository/panel. No Bluetooth or hardware. */
class ClassicMavlinkPathUiTest {
    @get:Rule val compose = createAndroidComposeRule<FixtureTestActivity>()

    @Test fun completeClassicReceivePathRejectsOtherSourcesExpiresAndReconnects() {
        val clock = AtomicLong(1_000)
        val displayTime = mutableLongStateOf(1_000)
        val transport = MemoryClassicTransport()
        val repository = VehicleRepositoryImpl()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository,
            heartbeatTimeoutMillis = 30_000, scope = scope, elapsedClock = { clock.get() }, epochClock = { 1_000_000L })
        val link = mutableStateOf(ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 1_000_000,
            connectedAtElapsedMillis = 1_000))
        fun emit(bytes: ByteArray) = runBlocking { transport.emit(bytes) }
        fun waitStatus(text: String) = compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        try {
            runBlocking { session.start() }
            compose.setContent { MamaGcsTheme {
                val vehicle by repository.vehicleState.collectAsState()
                Surface(Modifier.verticalScroll(rememberScrollState())) {
                    ClassicConnectionCheckPanel(ClassicConnectionCheck.assess(true, link.value, vehicle, 1_000_000, displayTime.longValue))
                }
            } }
            waitStatus("SOCKET OPEN · WAITING FOR BYTES")
            val heartbeat = heartbeat(42)
            val bad = heartbeat.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            emit(bad)
            compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.checksumFailures > 0 }
            waitStatus("BYTES ARRIVING · CUBE MAVLINK NOT CONFIRMED")
            emit(heartbeat.copyOfRange(0, 5))
            emit(heartbeat.copyOfRange(5, heartbeat.size))
            waitStatus("HEARTBEAT RECEIVED · WAITING FOR MEASUREMENTS")
            emit(frame(24, 30, 24, 99))
            compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.ignoredOtherSource == 1L }
            compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
            val rc = ByteArray(42).apply { this[4] = 0xdc.toByte(); this[5] = 5; this[40] = 1; this[41] = 255.toByte() }
            val measurements = frame(24,30,24) + frame(33,28,104) + frame(30,28,39) + frame(74,20,20) +
                frame(1,31,124) + frame(65,42,118, payload = rc)
            emit(measurements)
            compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages >= 7L }
            waitStatus("LIVE RECEIVE EVIDENCE")
            compose.onNodeWithTag("classic-check-measurements").performScrollTo().assertTextEquals(
                "5. Measurement messages: GPS, Position, Attitude, Battery, Rover HUD, System status, RC input")
            compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: FRESH")
            capture("phase108-classic-live")
            clock.set(6_001)
            compose.runOnIdle { displayTime.longValue = 6_001 }
            emit(heartbeat)
            compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages >= 8L }
            waitStatus("HEARTBEAT RECEIVED · WAITING FOR MEASUREMENTS")
            compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
            capture("phase108-classic-stale")
            emit(frame(1,31,124) + frame(65,42,118, payload = rc))
            compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages >= 10L }
            compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: Battery, System status, RC input")
            runBlocking { session.stop() }
            clock.set(7_000)
            runBlocking { session.start() }
            compose.runOnIdle { displayTime.longValue = 7_000; link.value = link.value.copy(connectedAtElapsedMillis = 7_000) }
            waitStatus("SOCKET OPEN · WAITING FOR BYTES")
            compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
            assertEquals(0, transport.sendCount.get())
        } finally {
            runBlocking { session.stop() }
            session.close()
            scope.cancel()
        }
    }

    @Test fun independentPymavlinkV1AndV2FramesReachClassicPanel() {
        val vectors = InstrumentationRegistry.getInstrumentation().context.assets.open("phase109-vectors.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                val parts = line.split('=', limit = 2)
                parts[0] to parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }
        val clock = AtomicLong(1_000)
        val displayTime = mutableLongStateOf(1_000)
        val transport = MemoryClassicTransport()
        val repository = VehicleRepositoryImpl()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository,
            heartbeatTimeoutMillis = 30_000, scope = scope, elapsedClock = { clock.get() }, epochClock = { 1_000_000L })
        val link = mutableStateOf(ConnectionState(TransportStatus.OPEN, connectedAtEpochMillis = 1_000_000,
            connectedAtElapsedMillis = 1_000))
        fun emit(bytes: ByteArray) = runBlocking { transport.emit(bytes) }
        try {
            runBlocking { session.start() }
            compose.setContent { MamaGcsTheme {
                val vehicle by repository.vehicleState.collectAsState()
                Surface(Modifier.verticalScroll(rememberScrollState())) {
                    ClassicConnectionCheckPanel(ClassicConnectionCheck.assess(true, link.value, vehicle, 1_000_000, displayTime.longValue))
                }
            } }
            for ((index, prefix) in listOf("v1", "v2").withIndex()) {
                if (index > 0) {
                    runBlocking { session.stop() }
                    clock.set(2_000)
                    runBlocking { session.start() }
                    compose.runOnIdle { displayTime.longValue = 2_000; link.value = link.value.copy(connectedAtElapsedMillis = 2_000) }
                }
                val heartbeat = vectors.getValue("${prefix}_heartbeat")
                val corrupt = heartbeat.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
                emit(corrupt)
                compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.checksumFailures > 0 }
                compose.onNodeWithTag("classic-check-measurements").assertTextEquals("5. Measurement messages: NOT CONFIRMED")
                emit(heartbeat.copyOfRange(0, 5))
                emit(heartbeat.copyOfRange(5, heartbeat.size))
                emit(vectors.getValue("${prefix}_rc_channels") + vectors.getValue("${prefix}_sys_status"))
                compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages >= 3L }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("LIVE RECEIVE EVIDENCE").fetchSemanticsNodes().isNotEmpty() }
                val state = repository.vehicleState.value
                assertEquals(42, state.systemId)
                assertEquals(1500, state.rcInput.channelsMicroseconds[0])
                assertEquals(1700, state.rcInput.channelsMicroseconds[1])
                assertEquals(67, state.battery.percentage)
                assertEquals(24.6, requireNotNull(state.battery.voltage), 0.001)
                assertEquals(if (prefix == "v1") 3L else 0L, state.mavlinkDiagnostics.decodedV1Messages)
                assertEquals(if (prefix == "v2") 3L else 0L, state.mavlinkDiagnostics.decodedV2Messages)
                compose.onNodeWithTag("classic-check-measurements").performScrollTo().assertTextEquals("5. Measurement messages: Battery, System status, RC input")
                compose.onNodeWithTag("classic-check-rc-status").assertTextEquals("6. RC_CHANNELS from selected autopilot: FRESH")
                capture("phase109-independent-$prefix")
                if (prefix == "v2") {
                    emit(vectors.getValue("v2_rc_zero_rssi") + vectors.getValue("v2_sys_zero_remaining"))
                    compose.waitUntil(10_000) { repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages >= 5L }
                    assertEquals(0, repository.vehicleState.value.rcInput.rssiRaw)
                    assertEquals(0, repository.vehicleState.value.battery.percentage)
                }
            }
            assertEquals(0, transport.sendCount.get())
        } finally {
            runBlocking { session.stop() }
            session.close()
            scope.cancel()
        }
    }

    private fun frame(id: Int, length: Int, crc: Int, system: Int = 42, payload: ByteArray = ByteArray(length)): ByteArray {
        val body = byteArrayOf(0xfd.toByte(), payload.size.toByte(), 0, 0, 7, system.toByte(), 1,
            id.toByte(), (id shr 8).toByte(), (id shr 16).toByte()) + payload
        val checksum = MavlinkChecksum.calculate(body.copyOfRange(1, body.size), crc)
        return body + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
    }
    private fun heartbeat(system: Int) = frame(0, 9, 50, system,
        byteArrayOf(0, 0, 0, 0, 10, 3, 0, 4, 3))
    private fun capture(name: String) {
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private class MemoryClassicTransport : VehicleTransport {
        override val linkKind = TelemetryLinkKind.CLASSIC
        override val connectionState = MutableStateFlow(ConnectionState())
        private val packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val sendCount = AtomicInteger(0)
        override suspend fun connect() { connectionState.value = ConnectionState(status = TransportStatus.OPEN) }
        override suspend fun disconnect() { connectionState.value = ConnectionState() }
        override suspend fun send(data: ByteArray) { sendCount.incrementAndGet(); error("Unexpected outbound transport send") }
        override fun receive(): Flow<ByteArray> = packets.asSharedFlow()
        suspend fun emit(bytes: ByteArray) { packets.emit(bytes) }
    }
}
