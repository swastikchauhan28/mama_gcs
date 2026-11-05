package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import com.mamadrones.gcs.core.security.AuthorizationPolicy
import com.mamadrones.gcs.data.mavlink.MavlinkSessionFactory
import com.mamadrones.gcs.data.mavlink.VehicleMavlinkSession
import com.mamadrones.gcs.data.transport.TelemetryLinkGate
import com.mamadrones.gcs.data.transport.tcp.TcpTransport
import com.mamadrones.gcs.domain.model.ConnectionState
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.Permission
import com.mamadrones.gcs.domain.repository.AccessRepository
import com.mamadrones.gcs.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TcpUiState(
    val host: String = "", val port: String = "", val active: Boolean = false,
    val connection: ConnectionState = ConnectionState(), val error: String? = null,
)

@HiltViewModel
class TcpTelemetryViewModel @Inject constructor(
    private val sessionFactory: MavlinkSessionFactory,
    private val gate: TelemetryLinkGate,
    private val accessRepository: AccessRepository,
) : ViewModel(), DefaultLifecycleObserver {
    private val authorizationPolicy = AuthorizationPolicy()
    private val mutableState = MutableStateFlow(TcpUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var operation: Job? = null
    private var observer: Job? = null
    private var transport: TcpTransport? = null
    private var session: VehicleMavlinkSession? = null
    private var lease: TelemetryLinkGate.Lease? = null

    init { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    fun host(value: String) { if (!state.value.active && value.length <= 64) mutableState.update { it.copy(host = value, error = null) } }
    fun port(value: String) { if (!state.value.active && value.length <= 16) mutableState.update { it.copy(port = value, error = null) } }

    fun connect() {
        if (state.value.active) return
        if (!authorizationPolicy.allows(accessRepository.state.value.session, Permission.CONFIGURE, SystemClock.elapsedRealtime())) {
            mutableState.update { it.copy(error = "Sign in with an administrator account to connect to a TCP endpoint.") }
            return
        }
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
            mutableState.update { it.copy(active = true, connection = ConnectionState(TransportStatus.CONNECTING), error = null) }
            operation = viewModelScope.launch {
                try {
                    parser.start()
                    if (token != generation) return@launch
                    observer = viewModelScope.launch {
                        link.connectionState.collect { connection ->
                            if (token == generation) {
                                mutableState.update { it.copy(connection = connection) }
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
        ++generation
        operation?.cancel(); operation = null
        observer?.cancel(); observer = null
        runCatching { session?.close() }; session = null
        transport?.close(); transport = null
        lease?.let(gate::release); lease = null
        mutableState.update { it.copy(active = false, connection = ConnectionState(), error = error) }
    }
    override fun onStop(owner: LifecycleOwner) = disconnect()
    override fun onCleared() { ProcessLifecycleOwner.get().lifecycle.removeObserver(this); disconnect(); super.onCleared() }
}

@Composable
fun TcpTelemetryScreen(vehicle: VehicleState, model: TcpTelemetryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    ScreenBody {
        ScreenHeader("TCP / Wi-Fi", "Receive MAVLink from a TCP server on your Wi-Fi network")
        Notice("CONNECTION", "Connect the phone to the Wi-Fi network of the TCP-to-MAVLink bridge attached to the Cube. Enter the bridge's IPv4 address and TCP server port. Administrator sign-in is required.")
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
        Notice("TELEMETRY", "TCP connection alone does not confirm Cube telemetry or authenticate the vehicle. The app receives data only and closes the link when it goes to the background.")
    }
}
