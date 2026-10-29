package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.RcPwmPanels
import kotlinx.coroutines.delay

@Composable
fun RcPwmScreen(state: VehicleState, modifier: Modifier = Modifier) {
    val nowElapsed by produceState(android.os.SystemClock.elapsedRealtime()) {
        while (true) { delay(500); value = android.os.SystemClock.elapsedRealtime() }
    }
    RcPwmContent(state, modifier, nowElapsed)
}

@Composable
fun RcPwmContent(state: VehicleState, modifier: Modifier = Modifier, nowElapsedMillis: Long? = null) = ScreenBody(modifier) {
    var selectedPort by rememberSaveable { mutableStateOf<Int?>(null) }
    val ports = state.pwmOutputs.map { it.port }.distinct().sorted()
    val port = selectedPort?.takeIf { it in ports } ?: ports.firstOrNull()
    ScreenHeader("RC & PWM monitor", "Receive-only · no calibration or output commands")
    Notice("OBSERVATION ONLY", "These are autopilot-reported values, not measurements of motor movement or proof of a safe stop. " +
        "Use only a hardware-team-secured test setup. The app cannot stop the rover.")
    RcInputActivityPanel(state, nowElapsedMillis ?: -1L)
    if (port == null) {
        SubsystemCard(RcPwmPanels.rc(state, nowElapsedMillis))
        Notice("PWM OUTPUTS NOT RECEIVED", "No SERVO_OUTPUT_RAW sample has arrived from the selected autopilot in this session.")
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ports.forEach { candidate ->
                FilterChip(selected = port == candidate, onClick = { selectedPort = candidate },
                    label = { Text("Port $candidate") }, modifier = Modifier.testTag("pwm-port-$candidate"))
            }
        }
        CardGrid(listOf(RcPwmPanels.rc(state, nowElapsedMillis), RcPwmPanels.pwm(state, port, nowElapsedMillis)))
    }
    Notice("MONITOR LIMITS", "Up to four reported output ports are retained per session. Channel numbers and port values do not verify MAIN/AUX or left/right wiring. " +
        "Values are hidden after five seconds without a new sample, or when the heartbeat/link is not current. RC display, activity and PWM use elapsed receipt time. " +
        "This is a display timeout, not a failsafe. If messages are absent, ask the hardware team to check the telemetry stream; the app does not request rates or change parameters.")
}
