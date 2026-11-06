package com.mamadrones.gcs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.MavlinkDiagnostics
import com.mamadrones.gcs.domain.model.TelemetryLinkKind
import com.mamadrones.gcs.domain.model.TransportStatus
import com.mamadrones.gcs.domain.model.VehicleConnectionState
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.SubsystemCard
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.navigation.StationShell
import com.mamadrones.gcs.presentation.theme.MamaGcsTheme

/**
 * Disposable, in-memory UI fixture for the TCP/MAVLink link-health states.
 * It never opens a socket, reads device data, persists a draft, or sends a command.
 */
internal data class LinkHealthScenario(
    val name: String,
    val vehicleStatus: VehicleConnectionState,
    val diagnostics: MavlinkDiagnostics,
    val nowElapsedMillis: Long,
    val tcpStatus: TransportStatus?,
)

internal object LinkHealthScenarios {
    val steadyHeartbeat = LinkHealthScenario(
        name = "Steady accepted heartbeat",
        vehicleStatus = VehicleConnectionState.CONNECTED,
        diagnostics = MavlinkDiagnostics(
            active = true,
            linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100,
            timingHeartbeatAtElapsedMillis = 6_000,
            lastBytesAtElapsedMillis = 6_000,
        ),
        nowElapsedMillis = 6_001,
        tcpStatus = TransportStatus.OPEN,
    )

    val delayedHeartbeat = LinkHealthScenario(
        name = "Delayed heartbeat with incoming bytes",
        vehicleStatus = VehicleConnectionState.DEGRADED,
        diagnostics = MavlinkDiagnostics(
            active = true,
            linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100,
            timingHeartbeatAtElapsedMillis = 100,
            lastBytesAtElapsedMillis = 5_102,
        ),
        nowElapsedMillis = 5_102,
        tcpStatus = TransportStatus.OPEN,
    )

    val recoveredLongGap = LinkHealthScenario(
        name = "Recovered heartbeat after long gap",
        vehicleStatus = VehicleConnectionState.CONNECTED,
        diagnostics = MavlinkDiagnostics(
            active = true,
            linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100,
            timingHeartbeatAtElapsedMillis = 6_200,
            lastBytesAtElapsedMillis = 6_200,
            completedHeartbeatGapsOverFiveSeconds = 1,
            completedGapsWithInputBeforeRecovery = 1,
            longestCompletedHeartbeatIntervalMillis = 6_100,
            lastCompletedHeartbeatIntervalMillis = 6_100,
            lastLongGapHadInputBeforeRecovery = true,
        ),
        nowElapsedMillis = 6_201,
        tcpStatus = TransportStatus.OPEN,
    )

    val socketLoss = LinkHealthScenario(
        name = "Socket closed",
        vehicleStatus = VehicleConnectionState.DISCONNECTED,
        diagnostics = MavlinkDiagnostics(
            active = false,
            linkKind = TelemetryLinkKind.TCP,
            startedAtElapsedMillis = 100,
            timingHeartbeatAtElapsedMillis = 6_000,
        ),
        nowElapsedMillis = 6_001,
        tcpStatus = TransportStatus.DISCONNECTED,
    )
}

@Composable
internal fun LinkHealthScenarioFixture(scenario: LinkHealthScenario) {
    MamaGcsTheme {
        StationShell(
            vehicle = VehicleState(
                displayName = "SIMULATED TCP",
                connectionStatus = scenario.vehicleStatus,
                mavlinkDiagnostics = scenario.diagnostics,
            ),
            route = "diagnostics",
            onNavigate = {},
            onVehicleDetails = {},
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("SIMULATED LINK-HEALTH FIXTURE", style = MaterialTheme.typography.titleMedium)
                Text("${scenario.name} · no socket, saved data, or vehicle command", style = MaterialTheme.typography.bodySmall)
                SubsystemCard(ConsolePanels.heartbeatTiming(
                    sample = scenario.diagnostics,
                    nowElapsedMillis = scenario.nowElapsedMillis,
                    tcpStatus = scenario.tcpStatus,
                ))
            }
        }
    }
}
