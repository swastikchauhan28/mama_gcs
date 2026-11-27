package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.rtk.MavlinkRtcmEncoder
import com.mamadrones.gcs.data.rtk.NmeaGga
import com.mamadrones.gcs.data.rtk.NtripCasterClient
import com.mamadrones.gcs.data.rtk.NtripScheme
import com.mamadrones.gcs.data.rtk.NtripSettings
import com.mamadrones.gcs.data.rtk.Rtcm3Framer
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.tcp.TcpTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TcpSocketTimeline
import com.mamadrones.gcs.domain.model.TcpSocketTransition
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TcpUiState(
    val host: String = "", val port: String = "", val active: Boolean = false,
    val connection: ConnectionState = ConnectionState(), val error: String? = null,
    val socketTransitions: List<TcpSocketTransition> = emptyList(),
    val ntripTarget: String? = null, val ntripSendsGga: Boolean = false,
    val ntripActive: Boolean = false, val ntripStatus: String = "Stopped",
    val correctionsSent: Long = 0, val correctionsRejected: Long = 0,
)

internal fun TcpUiState.afterDisconnect(error: String?, atElapsedMillis: Long): TcpUiState {
    // A normal remote close may have explanatory detail; it is not a socket ERROR event.
    val withError = if (active && error != null && connection.status != TransportStatus.DISCONNECTED)
        TcpSocketTimeline.record(socketTransitions, TransportStatus.ERROR, atElapsedMillis) else socketTransitions
    return copy(active = false, connection = ConnectionState(), error = error,
        socketTransitions = if (active) TcpSocketTimeline.record(withError, TransportStatus.DISCONNECTED, atElapsedMillis)
            else withError)
}

@HiltViewModel
class TcpTelemetryViewModel @Inject constructor(
    private val sessionFactory: MavlinkSessionFactory,
    private val gate: TelemetryLinkGate,
    private val vehicleRepository: VehicleRepositoryImpl,
) : ViewModel(), DefaultLifecycleObserver {
    private val mutableState = MutableStateFlow(TcpUiState())
    val state = mutableState.asStateFlow()
    @Volatile private var generation = 0L
    @Volatile private var ntripAttempt = 0L
    private var operation: Job? = null
    private var observer: Job? = null
    private var transport: TcpTransport? = null
    private var session: VehicleMavlinkSession? = null
    private var lease: TelemetryLinkGate.Lease? = null
    @Volatile private var ntripJob: Job? = null
    @Volatile private var caster: NtripCasterClient? = null

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    fun host(value: String) { if (!state.value.active && value.length <= 64) mutableState.update { it.copy(host = value, error = null) } }
    fun port(value: String) { if (!state.value.active && value.length <= 16) mutableState.update { it.copy(port = value, error = null) } }
    private fun hasFreshTcpHeartbeat(): Boolean {
        val vehicle = vehicleRepository.vehicleState.value
        val age = vehicle.lastHeartbeatAtElapsedMillis?.let { android.os.SystemClock.elapsedRealtime() - it }
        return state.value.active && state.value.connection.status == TransportStatus.OPEN &&
            vehicle.connected && vehicle.systemId?.let { it in 1..255 } == true && vehicle.mavlinkDiagnostics.active &&
            vehicle.mavlinkDiagnostics.linkKind == TelemetryLinkKind.TCP && age != null && age in 0..5_000
    }

    private fun updateNtripIfCurrent(token: Long, attempt: Long, change: (TcpUiState) -> TcpUiState) {
        mutableState.update { current ->
            if (token == generation && attempt == ntripAttempt) change(current) else current
        }
    }

    /** Returns a safe, credential-free validation message; the raw URL is never retained. */
    fun startNtrip(remoteUrl: String, allowPlainHttp: Boolean, sendGga: Boolean): String? {
        if (state.value.ntripActive) return "NTRIP is already active."
        if (!hasFreshTcpHeartbeat()) {
            return "Connect TCP and wait for a fresh Cube heartbeat first."
        }
        val settings = try {
            NtripSettings.parse(remoteUrl)
        } catch (_: Exception) {
            return "Enter a valid NTRIP URL with scheme, host, port and mountpoint."
        }
        if (settings.scheme == NtripScheme.HTTP && !allowPlainHttp)
            return "HTTP sends the caster password without encryption. Check the session opt-in to continue."
        val initialGga = if (sendGga) currentGga() ?: return "Fresh Cube GPS 3D fix is required to send VRS GGA." else null
        val link = transport ?: return "TCP link unavailable."
        val token = generation
        val attempt = ++ntripAttempt
        val client = NtripCasterClient(settings, allowPlainHttp)
        caster = client
        mutableState.update { it.copy(ntripActive = true, ntripStatus = "Connecting to NTRIP caster",
            ntripTarget = settings.displayTarget, ntripSendsGga = sendGga,
            correctionsSent = 0, correctionsRejected = 0) }
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val framer = Rtcm3Framer()
            val encoder = MavlinkRtcmEncoder()
            try {
                client.open(initialGga).use { input ->
                    if (token != generation || attempt != ntripAttempt) return@use
                    updateNtripIfCurrent(token, attempt) { it.copy(ntripStatus = "Caster connected; waiting for validated RTCM") }
                    val buffer = ByteArray(2048)
                    var windowStarted = android.os.SystemClock.elapsedRealtime()
                    var windowBytes = 0
                    var lastGgaAt = windowStarted
                    while (isActive && token == generation && attempt == ntripAttempt && hasFreshTcpHeartbeat()) {
                        val ggaNow = android.os.SystemClock.elapsedRealtime()
                        if (sendGga && ggaNow - lastGgaAt >= 10_000) {
                            client.sendGga(currentGga() ?: throw IOException("Fresh Cube GPS position unavailable for VRS GGA."))
                            lastGgaAt = ggaNow
                        }
                        val count = try { input.read(buffer) } catch (_: SocketTimeoutException) { continue }
                        if (count < 0) throw IOException("NTRIP caster closed the stream.")
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - windowStarted >= 60_000) { windowStarted = now; windowBytes = 0 }
                        windowBytes += count
                        if (windowBytes > MAX_NTRIP_BYTES_PER_MINUTE)
                            throw IOException("NTRIP stream exceeded the 256 KiB/min safety limit.")
                        framer.feed(buffer.copyOf(count)).forEach { frame ->
                            if (!hasFreshTcpHeartbeat() || token != generation || attempt != ntripAttempt)
                                throw IOException("Cube heartbeat or TCP link lost.")
                            if (sendGga && currentGga() == null)
                                throw IOException("Fresh Cube GPS position unavailable for VRS GGA.")
                            link.sendCorrection(frame, encoder)
                            updateNtripIfCurrent(token, attempt) {
                                it.copy(correctionsSent = it.correctionsSent + 1,
                                    ntripStatus = "Forwarding validated RTCM; Cube acceptance unverified") }
                        }
                        updateNtripIfCurrent(token, attempt) { it.copy(correctionsRejected = framer.rejectedFrames) }
                    }
                    if (isActive) throw IOException("Cube heartbeat or TCP link lost.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                updateNtripIfCurrent(token, attempt) { it.copy(ntripStatus =
                    if (error is IOException) error.message ?: "NTRIP stopped." else "NTRIP connection failed.") }
            } finally {
                client.close()
                if (caster === client) caster = null
                if (token == generation && attempt == ntripAttempt) ntripJob = null
                updateNtripIfCurrent(token, attempt) { it.copy(ntripActive = false) }
            }
        }
        ntripJob = job
        job.start()
        return null
    }

    private fun currentGga(): String? = NmeaGga.fromCube(vehicleRepository.vehicleState.value.gps,
        android.os.SystemClock.elapsedRealtime(), LocalTime.now(ZoneOffset.UTC))

    private companion object { const val MAX_NTRIP_BYTES_PER_MINUTE = 256 * 1024 }

    fun stopNtrip() {
        ++ntripAttempt // A finishing old worker must not overwrite a later connection attempt.
        ntripJob?.cancel(); ntripJob = null
        caster?.close(); caster = null
        mutableState.update { it.copy(ntripActive = false, ntripStatus = "Stopped", ntripTarget = null,
            ntripSendsGga = false) }
    }

    fun connect() {
        if (state.value.active) return
        val host = state.value.host.trim()
        val parts = host.split('.')
        val validIp = parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.length <= 3 &&
            part.all { it in '0'..'9' } && (part.toIntOrNull() ?: -1) in 0..255 }
        val port = state.value.port.toIntOrNull()
        if (!validIp || port == null || port !in 1..65535) {
            mutableState.update { it.copy(error = "Enter an IPv4 address and a port from 1 to 65535.") }
            return
        }
        try {
            lease = gate.acquire("TCP")
            val token = ++generation
            val link = TcpTransport(host, port)
            transport = link
            val parser = sessionFactory.create(link, viewModelScope)
            session = parser
            mutableState.update { it.copy(active = true, connection = ConnectionState(TransportStatus.CONNECTING), error = null,
                socketTransitions = TcpSocketTimeline.record(emptyList(), TransportStatus.CONNECTING,
                    android.os.SystemClock.elapsedRealtime())) }
            operation = viewModelScope.launch {
                try {
                    parser.start()
                    if (token != generation) return@launch
                    observer = viewModelScope.launch {
                        link.connectionState.collect { connection ->
                            if (token == generation) {
                                mutableState.update { it.copy(connection = connection,
                                    socketTransitions = TcpSocketTimeline.record(it.socketTransitions, connection.status,
                                        connection.connectedAtElapsedMillis ?: android.os.SystemClock.elapsedRealtime())) }
                                if (connection.status == TransportStatus.ERROR || connection.status == TransportStatus.DISCONNECTED) {
                                    disconnect(connection.detail ?: "TCP connection ended.")
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (token == generation) disconnect()
                    throw cancelled
                } catch (error: Exception) {
                    if (token == generation) disconnect(error.message ?: "TCP connection failed.")
                }
            }
        } catch (error: Exception) { disconnect(error.message ?: "Cannot open TCP connection.") }
    }

    fun disconnect(error: String? = null) {
        stopNtrip()
        val now = android.os.SystemClock.elapsedRealtime()
        ++generation
        operation?.cancel(); operation = null
        observer?.cancel(); observer = null
        runCatching { session?.close() }; session = null
        transport?.close(); transport = null
        lease?.let(gate::release); lease = null
        mutableState.update { it.afterDisconnect(error, now) }
    }
    override fun onStop(owner: LifecycleOwner) = disconnect()
    override fun onCleared() { ProcessLifecycleOwner.get().lifecycle.removeObserver(this); disconnect(); super.onCleared() }
}

@Composable
fun TcpTelemetryScreen(vehicle: VehicleState, model: TcpTelemetryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var showNtripPrompt by remember { mutableStateOf(false) }
    ScreenBody {
        ScreenHeader("TCP / Wi-Fi", "Receive MAVLink and optionally forward RTK corrections")
        Notice("CONNECTION", "Connect the phone to the Wi-Fi network of the TCP-to-MAVLink bridge attached to the Cube. Enter the bridge's IPv4 address and TCP server port.")
        OutlinedTextField(value = state.host, onValueChange = model::host, label = { Text("IP address") },
            singleLine = true, enabled = !state.active, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().testTag("tcp-host"))
        OutlinedTextField(value = state.port, onValueChange = model::port, label = { Text("TCP port") },
            singleLine = true, enabled = !state.active, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("tcp-port"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::connect, enabled = !state.active, modifier = Modifier.testTag("tcp-connect")) { Text("Connect") }
            OutlinedButton(onClick = { model.disconnect() }, enabled = state.active, modifier = Modifier.testTag("tcp-disconnect")) { Text("Disconnect") }
        }
        Text("TCP: ${state.connection.status}", modifier = Modifier.testTag("tcp-status"))
        state.connection.detail?.let { Text(it) }
        state.error?.let { Notice("TCP CONNECTION", it) }
        Text(if (state.active && vehicle.connected && vehicle.mavlinkDiagnostics.active &&
            vehicle.mavlinkDiagnostics.linkKind == TelemetryLinkKind.TCP) "MAVLink heartbeat received"
            else "Waiting for a valid autopilot heartbeat", modifier = Modifier.testTag("tcp-heartbeat"))
        Notice("TELEMETRY", "TCP connection alone does not confirm Cube telemetry or authenticate the vehicle. The link closes when the app goes to the background.")
        HorizontalDivider()
        Text("RTK / GPS inject · NTRIP", style = MaterialTheme.typography.titleMedium)
        Notice("CORRECTIONS", "Enter one caster URL when prompted. RTCM corrections go only to the active TCP link after a fresh Cube heartbeat. Sent does not mean accepted or RTK fixed.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showNtripPrompt = true }, enabled = !state.ntripActive,
                modifier = Modifier.testTag("ntrip-start")) { Text("Connect NTRIP") }
            OutlinedButton(onClick = model::stopNtrip, enabled = state.ntripActive,
                modifier = Modifier.testTag("ntrip-stop")) { Text("Stop") }
        }
        state.ntripTarget?.let { Text("Caster: $it", modifier = Modifier.testTag("ntrip-target")) }
        if (state.ntripActive) Text(if (state.ntripSendsGga) "VRS GGA: on" else "VRS GGA: off")
        Text("NTRIP: ${state.ntripStatus}", modifier = Modifier.testTag("ntrip-status"))
        Text("Validated RTCM sent: ${state.correctionsSent} · Rejected: ${state.correctionsRejected}",
            modifier = Modifier.testTag("ntrip-counters"))
        Text("Cube-reported GPS fix: ${vehicle.gps.fix.name.replace('_', ' ')}",
            modifier = Modifier.testTag("ntrip-gps-fix"))
    }
    if (showNtripPrompt) {
        var remoteUrl by remember { mutableStateOf("") }
        var allowHttp by remember { mutableStateOf(false) }
        var sendGga by remember { mutableStateOf(false) }
        var promptError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNtripPrompt = false },
            title = { Text("NTRIP remote host") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the caster URL, including username, password, host, port and mountpoint. The URL is used for this session only.")
                    OutlinedTextField(remoteUrl, { if (it.length <= 512) { remoteUrl = it; promptError = null } },
                        label = { Text("NTRIP URL") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("ntrip-url"))
                    if (remoteUrl.startsWith("http://", ignoreCase = true)) {
                        Text("HTTP is unencrypted. Anyone on the network path can read the caster password and any GGA position you choose to send.",
                            color = MaterialTheme.colorScheme.error)
                        Row { Checkbox(allowHttp, { allowHttp = it }, modifier = Modifier.testTag("ntrip-http-opt-in"))
                            Text("Allow unencrypted HTTP for this session") }
                    }
                    Row { Checkbox(sendGga, { sendGga = it }, modifier = Modifier.testTag("ntrip-gga"))
                        Text("Send fresh Cube GPS position as VRS GGA") }
                    Text("GGA shares rover position with the caster. Requires a fresh 3D or better Cube GPS fix.")
                    promptError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ntrip-prompt-error")) }
                }
            },
            confirmButton = { TextButton(onClick = {
                val error = model.startNtrip(remoteUrl, allowHttp, sendGga)
                if (error == null) showNtripPrompt = false else promptError = error
            }, modifier = Modifier.testTag("ntrip-confirm")) { Text("Connect") } },
            dismissButton = { TextButton(onClick = { showNtripPrompt = false }) { Text("Cancel") } },
        )
    }
}
