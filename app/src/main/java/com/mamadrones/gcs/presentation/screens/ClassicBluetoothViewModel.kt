package com.mamadrones.gcs.presentation.screens

import androidx.lifecycle.*
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.rtk.MavlinkRtcmEncoder
import com.mamadrones.gcs.data.rtk.NmeaGga
import com.mamadrones.gcs.data.rtk.NtripCasterClient
import com.mamadrones.gcs.data.rtk.NtripScheme
import com.mamadrones.gcs.data.rtk.NtripSettings
import com.mamadrones.gcs.data.rtk.Rtcm3Framer
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.bluetooth.*
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ClassicNtripState(
    val active: Boolean = false,
    val status: String = "Stopped",
    val target: String? = null,
    val sendsGga: Boolean = false,
    val sent: Long = 0,
    val rejected: Long = 0,
)

/** A receipt from an earlier socket or another active link cannot open correction forwarding. */
internal fun freshClassicCorrectionSource(active: Boolean, connection: ConnectionState,
    vehicle: VehicleState, nowElapsedMillis: Long): Boolean {
    val opened = connection.connectedAtElapsedMillis ?: return false
    val heartbeatAt = vehicle.lastHeartbeatAtElapsedMillis ?: return false
    return active && connection.status == TransportStatus.OPEN && vehicle.connected &&
        vehicle.systemId?.let { it in 1..255 } == true && vehicle.mavlinkDiagnostics.active &&
        vehicle.mavlinkDiagnostics.linkKind == TelemetryLinkKind.CLASSIC &&
        heartbeatAt >= opened && nowElapsedMillis - heartbeatAt in 0..5_000
}

data class ClassicBluetoothUiState(
    val peers: List<ClassicBluetoothPeer> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null,
    val link: ClassicLinkState = ClassicLinkState(),
    val ntrip: ClassicNtripState = ClassicNtripState(),
)

@HiltViewModel
class ClassicBluetoothViewModel @Inject constructor(
    private val devices: ClassicBluetoothDevices,
    sessionFactory: MavlinkSessionFactory,
    gate: TelemetryLinkGate,
    private val vehicleRepository: VehicleRepositoryImpl,
) : ViewModel(), DefaultLifecycleObserver {
    private val controller = ClassicTelemetryController(devices::createTransport, sessionFactory, viewModelScope, gate)
    private val discovery = MutableStateFlow(ClassicBluetoothUiState())
    private val ntrip = MutableStateFlow(ClassicNtripState())
    val state = combine(discovery, controller.state, ntrip) { discoveryState, link, corrections ->
        discoveryState.copy(link = link, ntrip = corrections)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClassicBluetoothUiState())
    @Volatile private var ntripAttempt = 0L
    @Volatile private var ntripJob: Job? = null
    @Volatile private var caster: NtripCasterClient? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch {
            controller.state.collect { link ->
                if (!link.active && ntrip.value.active) stopNtrip()
            }
        }
    }

    fun refresh() {
        try {
            discovery.value = ClassicBluetoothUiState(peers = devices.pairedDevices(), loaded = true)
        } catch (error: Exception) {
            discovery.value = ClassicBluetoothUiState(loaded = true,
                error = error.message ?: "Could not list paired Bluetooth devices.")
        }
    }

    fun permissionDenied() {
        discovery.value = ClassicBluetoothUiState(error = "Nearby devices permission was denied. Allow it in Android app settings to use Bluetooth Classic.")
    }

    fun connect(peer: ClassicBluetoothPeer) {
        if (discovery.value.peers.none { it == peer }) return
        discovery.update { it.copy(error = null) }
        try { controller.connect(peer) }
        catch (error: Exception) { discovery.update { it.copy(error = error.message ?: "Cannot open Bluetooth link.") } }
    }

    private fun hasFreshClassicHeartbeat(link: BluetoothTransport): Boolean {
        return controller.currentCorrectionLink() === link && freshClassicCorrectionSource(
            controller.state.value.active, link.connectionState.value,
            vehicleRepository.vehicleState.value, android.os.SystemClock.elapsedRealtime())
    }

    private fun currentGga(link: BluetoothTransport): String? {
        if (!hasFreshClassicHeartbeat(link)) return null
        val gps = vehicleRepository.vehicleState.value.gps
        val opened = link.connectionState.value.connectedAtElapsedMillis ?: return null
        if ((gps.lastUpdatedAtElapsedMillis ?: return null) < opened) return null
        return NmeaGga.fromCube(gps, android.os.SystemClock.elapsedRealtime(), LocalTime.now(ZoneOffset.UTC))
    }

    /** Raw caster URL lives only in the prompt and this call; state holds a credential-free target. */
    fun startNtrip(remoteUrl: String, allowPlainHttp: Boolean, sendGga: Boolean): String? {
        if (ntrip.value.active) return "NTRIP is already active."
        val link = controller.currentCorrectionLink()
            ?: return "Connect Bluetooth Classic and wait for a fresh Cube heartbeat first."
        if (!hasFreshClassicHeartbeat(link)) return "Wait for a fresh Cube heartbeat on this Bluetooth session."
        val settings = try { NtripSettings.parse(remoteUrl) }
            catch (_: Exception) { return "Enter a valid NTRIP URL with scheme, host, port and mountpoint." }
        if (settings.scheme == NtripScheme.HTTP && !allowPlainHttp)
            return "HTTP sends the caster password without encryption. Check the session opt-in to continue."
        val initialGga = if (sendGga) currentGga(link)
            ?: return "Fresh Cube GPS 3D fix is required to send VRS GGA." else null
        val attempt = ++ntripAttempt
        val client = NtripCasterClient(settings, allowPlainHttp)
        caster = client
        ntrip.value = ClassicNtripState(active = true, status = "Connecting to NTRIP caster",
            target = settings.displayTarget, sendsGga = sendGga)
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val framer = Rtcm3Framer()
            val encoder = MavlinkRtcmEncoder()
            try {
                client.open(initialGga).use { input ->
                    if (attempt != ntripAttempt) return@use
                    ntrip.update { if (attempt == ntripAttempt) it.copy(status = "Caster connected; waiting for validated RTCM") else it }
                    val buffer = ByteArray(2048)
                    var windowStarted = android.os.SystemClock.elapsedRealtime()
                    var windowBytes = 0
                    var lastGgaAt = windowStarted
                    while (isActive && attempt == ntripAttempt && hasFreshClassicHeartbeat(link)) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (sendGga && now - lastGgaAt >= 10_000) {
                            client.sendGga(currentGga(link)
                                ?: throw IOException("Fresh Cube GPS position unavailable for VRS GGA."))
                            lastGgaAt = now
                        }
                        val count = try { input.read(buffer) } catch (_: SocketTimeoutException) { continue }
                        if (count < 0) throw IOException("NTRIP caster closed the stream.")
                        val receivedAt = android.os.SystemClock.elapsedRealtime()
                        if (receivedAt - windowStarted >= 60_000) { windowStarted = receivedAt; windowBytes = 0 }
                        windowBytes += count
                        if (windowBytes > MAX_NTRIP_BYTES_PER_MINUTE)
                            throw IOException("NTRIP stream exceeded the 256 KiB/min safety limit.")
                        framer.feed(buffer.copyOf(count)).forEach { frame ->
                            if (attempt != ntripAttempt || !hasFreshClassicHeartbeat(link))
                                throw IOException("Cube heartbeat or Bluetooth link lost.")
                            if (sendGga && currentGga(link) == null)
                                throw IOException("Fresh Cube GPS position unavailable for VRS GGA.")
                            link.sendCorrection(frame, encoder)
                            ntrip.update { if (attempt == ntripAttempt) it.copy(sent = it.sent + 1,
                                status = "Forwarding validated RTCM; Cube acceptance unverified") else it }
                        }
                        ntrip.update { if (attempt == ntripAttempt) it.copy(rejected = framer.rejectedFrames) else it }
                    }
                    if (isActive) throw IOException("Cube heartbeat or Bluetooth link lost.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                ntrip.update { if (attempt == ntripAttempt) it.copy(status =
                    if (error is IOException) error.message ?: "NTRIP stopped." else "NTRIP connection failed.") else it }
            } finally {
                client.close()
                if (caster === client) caster = null
                if (attempt == ntripAttempt) ntripJob = null
                ntrip.update { if (attempt == ntripAttempt) it.copy(active = false) else it }
            }
        }
        ntripJob = job
        job.start()
        return null
    }

    fun stopNtrip() {
        ++ntripAttempt
        ntripJob?.cancel(); ntripJob = null
        caster?.close(); caster = null
        ntrip.update { it.copy(active = false, status = "Stopped", target = null, sendsGga = false) }
    }

    fun disconnect() { stopNtrip(); controller.disconnect() }
    override fun onStop(owner: LifecycleOwner) = disconnect()
    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        disconnect()
    }

    private companion object { const val MAX_NTRIP_BYTES_PER_MINUTE = 256 * 1024 }
}
