package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import com.mamadrones.gcs.core.security.DriveSafetyGate
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.domain.model.AccessState
import com.mamadrones.gcs.domain.model.AccessSetupState
import com.mamadrones.gcs.domain.model.AuditRecord
import com.mamadrones.gcs.domain.model.UserRole
import com.mamadrones.gcs.presentation.components.*
import com.mamadrones.gcs.presentation.dashboard.ConsolePanels
import com.mamadrones.gcs.presentation.dashboard.forDisplay
import com.mamadrones.gcs.presentation.dashboard.reading
import com.mamadrones.gcs.presentation.dashboard.sampleAge
import com.mamadrones.gcs.presentation.map.VehicleMap
import com.mamadrones.gcs.presentation.settings.ConnectionUiState

@Composable
fun MapScreen(state: VehicleState, onNavigate: (String) -> Unit = {}, modifier: Modifier = Modifier) =
    DashboardScreen(state, onNavigate, modifier)

@Composable
fun ControlScreen(state: VehicleState, modifier: Modifier = Modifier) {
    val assessment = DriveSafetyGate.assess()
    var showChecks by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wideDrive = maxWidth >= 760.dp && maxHeight >= 480.dp
        if (wideDrive) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenHeader("Rover operations", "Live map and drive telemetry · commands locked")
                    Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        VehicleMap(state.forDisplay(), Modifier.fillMaxSize())
                    }
                    Text("Map position follows the received rover telemetry. It is not a command or navigation target.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.widthIn(min = 300.dp, max = 380.dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmergencyStopButton(Modifier.fillMaxWidth())
                    DriveStatusPanel(state)
                    DriveLockPanel(assessment.blockers.size)
                    UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
                    SafetyGatePanel(assessment.checks)
                }
            }
        } else {
            ScreenBody(Modifier.fillMaxSize()) {
                ScreenHeader("Rover operations", "Forward / reverse · ArduPilot Rover")
                EmergencyStopButton(Modifier.fillMaxWidth())
                DriveLockPanel(assessment.blockers.size)
                UnavailableActions("Forward", "Reverse", "Stop", "Arm", "Disarm", "Set mode", "Set speed")
                TextButton(onClick = { showChecks = !showChecks }, modifier = Modifier.heightIn(min = 48.dp).testTag("drive-safety-details")) {
                    Text(if (showChecks) "Hide safety requirements" else "Why locked? · ${assessment.blockers.size} unverified requirements")
                }
                if (showChecks) SafetyGatePanel(assessment.checks)
                DriveStatusPanel(state)
                VehicleMap(state.forDisplay(), Modifier.fillMaxWidth().height(300.dp))
            }
        }
    }
}

@Composable
private fun DriveLockPanel(unverifiedRequirements: Int) {
    Notice(
        "DRIVE LOCKED · MONITORING ONLY",
        "Command transmission and deadman control are not implemented. $unverifiedRequirements drive-safety requirements remain unverified. The app cannot stop the vehicle; use its independent physical safety system.",
    )
}

@Composable
private fun DriveStatusPanel(state: VehicleState) {
    val live = state.forDisplay()
    val base = ConsolePanels.vehicle(live)
    SubsystemCard(base.copy(
        title = "Rover status",
        rows = listOf(
            "Ground speed" to live.speedMetersPerSecond.reading("m/s"),
            "Heading" to live.headingDegrees.reading("°"),
        ) + base.rows,
        note = "${if (live.connected) "Live telemetry" else "No live telemetry"} · ${sampleAge(live.kinematicsLastUpdatedAtEpochMillis)}. Observed state only; no control path is active.",
    ))
}

@Composable
private fun SafetyGatePanel(checks: List<com.mamadrones.gcs.core.security.DriveSafetyCheck>) {
    SubsystemCard(PanelSpec(
        title = "DRIVE SAFETY GATE",
        status = "LOCKED · ${checks.count { !it.satisfied }} REQUIRED",
        rows = checks.map { it.requirement.description to if (it.satisfied) "VERIFIED" else "REQUIRED" },
        note = "These checks are not user-overridable. No trusted evidence provider or command route is connected.",
    ))
}

@Composable
fun HealthScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Vehicle health", "Telemetry evidence and readiness gate")
    CardGrid(listOf(ConsolePanels.health(state), ConsolePanels.healthBlockers(state), ConsolePanels.gps(state), ConsolePanels.battery(state), ConsolePanels.systemStatus(state)))
    Notice("READINESS NOT ASSESSED", "Received MAVLink data is shown as evidence only. Hardware-specific limits, expected telemetry rates, and validated subsystem routes are required before health or safety readiness can be assessed.")
}

@Composable
fun MotorScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Motors", "VESC drive system")
    Notice("HARDWARE INTEGRATION REQUIRED", "A read-only VESC telemetry reducer is ready, but controller models, identities, wiring, protocol and trusted telemetry route must be confirmed. No VESC input or motor commands are connected.")
    // VESC may use an independent route; MAVLink heartbeat loss must not rewrite its status.
    val motors = state.motors
    CardGrid(if (motors.isEmpty()) listOf(ConsolePanels.motors(state)) else motors.map { ConsolePanels.motor(it) })
}

@Composable
fun SprayScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Spray", "Pump, nozzles and application flow")
    SubsystemCard(ConsolePanels.spray(state))
    Notice("HARDWARE INTEGRATION REQUIRED", "Pump output mapping, nozzle addressing, pressure/flow sensors and safety interlocks are unconfirmed. Spray controls are unavailable.")
    UnavailableActions("Start spray", "Stop spray", "Nozzle control")
}

@Composable
fun HydraulicScreen(state: VehicleState, modifier: Modifier = Modifier) = ScreenBody(modifier) {
    ScreenHeader("Hydraulic", "Pump, valves and pressure")
    SubsystemCard(ConsolePanels.hydraulic(state))
    Notice("HARDWARE INTEGRATION REQUIRED", "Controller interface, valve mapping, sensors and mechanical interlocks are unconfirmed. Hydraulic controls are unavailable.")
    UnavailableActions("Enable hydraulic", "Disable hydraulic", "Valve control")
}

@Composable
fun DiagnosticsScreen(state: VehicleState, modifier: Modifier = Modifier, connection: ConnectionUiState = ConnectionUiState()) = ScreenBody(modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            value = System.currentTimeMillis()
        }
    }
    ScreenHeader("Diagnostics", "Vehicle and communication inspection")
    val link = connection.session.connection
    SubsystemCard(ConsolePanels.mavlinkDiagnostics(state.mavlinkDiagnostics, now))
    Notice("DECODER GUIDANCE", ConsolePanels.mavlinkDiagnosticHints(state.mavlinkDiagnostics))
    CardGrid(listOf(
        PanelSpec("UDP socket counters", link.status.name, listOf(
            "RX packets" to link.packetStatistics.receivedPackets.toString(),
            "TX packets" to link.packetStatistics.transmittedPackets.toString(),
            "Last packet" to sampleAge(link.packetStatistics.lastReceivedAtEpochMillis),
            "Transport" to if (connection.savedEndpoint != null) "UDP" else "UNCONFIGURED",
        ), note = "UDP only; BLE raw counters are on the BLE MAVLink screen. Shared decoder counters are shown above. Socket counts do not prove vehicle liveness or command capability."),
        ConsolePanels.vehicle(state), ConsolePanels.gps(state), ConsolePanels.position(state),
        ConsolePanels.battery(state), ConsolePanels.attitude(state), ConsolePanels.systemStatus(state),
        ConsolePanels.statusTexts(state)
    ))
    Notice("PERSISTENT LOGGING NOT IMPLEMENTED", "The latest in-memory STATUSTEXT messages and telemetry receive ages are shown above. They are not saved or exported, and do not constitute a health assessment.")
    UnavailableActions("Export logs")
}

@Composable
fun AdminScreen(
    access: AccessState,
    onInitializeAdmin: (String, CharArray) -> Unit,
    onSignIn: (String, CharArray) -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: (CharArray, CharArray) -> Unit,
    onCreateAccount: (String, CharArray, UserRole) -> Unit,
    onAccountEnabled: (String, Boolean) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) = ScreenBody(modifier) {
    ScreenHeader("Admin", "Device accounts and local access history")
    if (access.busy && access.setup != AccessSetupState.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
    when (access.setup) {
        AccessSetupState.LOADING -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Opening encrypted account store…")
        }
        AccessSetupState.STORAGE_UNAVAILABLE -> Notice(
            "SECURE ACCOUNT STORE UNAVAILABLE",
            access.error ?: "The account database could not be read. Existing data has not been reset.",
        )
        AccessSetupState.ADMIN_REQUIRED -> {
            Notice("FIRST TIME SETUP", "Create the first local administrator. This account is stored on this device and is not a vehicle pairing.")
            access.error?.let { Notice("SETUP ERROR", it) }
            access.message?.let { Notice("ACCOUNT READY", it) }
            AccessCredentialForm(
                title = "Create administrator",
                confirmPassword = true,
                actionLabel = "Create administrator",
                onSubmit = onInitializeAdmin,
                enabled = !access.busy,
            )
        }
        AccessSetupState.READY -> {
            access.session?.let { session ->
                CardGrid(listOf(PanelSpec("Signed in", session.role.name,
                    listOf("Username" to (access.accounts.firstOrNull { it.id == session.userId }?.username ?: "UNKNOWN"),
                        "Session" to "EXPIRES AFTER 15 MINUTES"))))
                Button(onClick = onSignOut, enabled = !access.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sign out") }
                key(session.sessionId) {
                    PasswordChangeForm(enabled = !access.busy, onSubmit = onChangePassword)
                }
                access.message?.let { Notice("ACCESS", it) }
                access.error?.let { Notice("ACCESS ERROR", it) }
                if (session.role == UserRole.ADMIN) {
                    AccountManagement(
                        access = access,
                        onCreateAccount = onCreateAccount,
                        onAccountEnabled = onAccountEnabled,
                        onClearMessage = onClearMessage,
                    )
                    AuditHistory(access.audit)
                } else {
                    PermissionSummary(session.role)
                }
            } ?: run {
                Notice("LOCAL SIGN IN", "Sign-in expires after 15 minutes and when the app process restarts. Read only monitoring remains available.")
                access.error?.let { Notice("SIGN IN FAILED", it) }
                access.message?.let { Notice("ACCESS", it) }
                AccessCredentialForm(
                    title = "Sign in",
                    confirmPassword = false,
                    actionLabel = "Sign in",
                    onSubmit = onSignIn,
                    enabled = !access.busy,
                )
                Notice(
                    "VEHICLE PAIRING UNAVAILABLE",
                    "A local account does not authenticate an ArduPilot system. A signed MAVLink peer and the hardware team's vehicle identity details are still required.",
                )
                access.audit.takeIf { it.isNotEmpty() }?.let {
                    Notice("AUDIT HISTORY", "Sign in with an administrator account to review local account events.")
                }
            }
        }
    }
}

@Composable
private fun AccessCredentialForm(
    title: String,
    confirmPassword: Boolean,
    actionLabel: String,
    onSubmit: (String, CharArray) -> Unit,
    enabled: Boolean,
) {
    var username by rememberSaveable(title) { mutableStateOf("") }
    var password by remember(title) { mutableStateOf("") }
    var confirmation by remember(title) { mutableStateOf("") }
    var localError by remember(title) { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(64); localError = null },
                label = { Text("Username") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("access-username"),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128); localError = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("access-password"),
                enabled = enabled,
            )
            if (confirmPassword) OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it.take(128); localError = null },
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("access-password-confirm"),
                enabled = enabled,
            )
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (confirmPassword && password != confirmation) {
                        localError = "Passwords do not match."
                    } else {
                        val secret = password.toCharArray()
                        password = ""
                        confirmation = ""
                        localError = null
                        onSubmit(username, secret)
                    }
                },
                enabled = enabled && username.isNotBlank() && password.isNotEmpty() && (!confirmPassword || confirmation.isNotEmpty()),
                modifier = Modifier.heightIn(min = 48.dp).testTag("access-submit"),
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun AccountManagement(
    access: AccessState,
    onCreateAccount: (String, CharArray, UserRole) -> Unit,
    onAccountEnabled: (String, Boolean) -> Unit,
    onClearMessage: () -> Unit,
) {
    var role by rememberSaveable { mutableStateOf(UserRole.OPERATOR) }
    Notice("ROLE POLICY", "Operator accounts are intended for operations; viewer accounts are monitor-only. Current enforced actions are local account management and administrator-only UDP peer configuration. No vehicle commands are implemented.")
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local accounts · ${access.accounts.size}/50", style = MaterialTheme.typography.titleMedium)
            access.accounts.forEach { account ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.username, style = MaterialTheme.typography.titleSmall)
                        Text("${account.role.name} · ${if (account.enabled) "ENABLED" else "DISABLED"}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (account.role != UserRole.ADMIN) TextButton(
                        onClick = { onAccountEnabled(account.id, !account.enabled) },
                        enabled = !access.busy,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(if (account.enabled) "Disable" else "Enable") }
                }
            }
        }
    }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add user", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(username, { username = it.take(64); localError = null; onClearMessage() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new-access-username"))
            OutlinedTextField(password, { password = it.take(128); localError = null; onClearMessage() }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("new-access-password"))
            OutlinedTextField(confirmation, { confirmation = it.take(128); localError = null; onClearMessage() }, label = { Text("Confirm password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("new-access-password-confirm"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserRole.entries.filter { it != UserRole.ADMIN }.forEach { choice ->
                    FilterChip(selected = role == choice, onClick = { role = choice }, label = { Text(choice.name) })
                }
            }
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (password != confirmation) {
                        localError = "Passwords do not match."
                    } else {
                        val accountName = username
                        val secret = password.toCharArray()
                        password = ""; confirmation = ""; username = ""
                        onCreateAccount(accountName, secret, role)
                    }
                },
                enabled = !access.busy && username.isNotBlank() && password.isNotEmpty() && confirmation.isNotEmpty() && access.accounts.size < 50,
                modifier = Modifier.heightIn(min = 48.dp).testTag("add-access-account"),
            ) { Text("Add account") }
        }
    }
    access.message?.let { Notice("ACCOUNT", it) }
    access.error?.let { Notice("ACCOUNT ERROR", it) }
}

@Composable
private fun PermissionSummary(role: UserRole) {
    val permissions = when (role) {
        UserRole.ADMIN -> "Local account management and UDP peer configuration. Vehicle command permissions are not active."
        UserRole.OPERATOR -> "No vehicle commands are active. Operations are monitoring-only in this build."
        UserRole.VIEWER -> "Monitor-only role. No vehicle commands are active."
    }
    Notice("YOUR PERMISSIONS", permissions)
}

@Composable
private fun AuditHistory(records: List<AuditRecord>) {
    if (records.isEmpty()) {
        Notice("LOCAL AUDIT", "Account activity will appear here. History is encrypted on this device and bounded to the most recent 500 events.")
        return
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local audit · newest first · ${records.size} shown", style = MaterialTheme.typography.titleMedium)
            records.forEach { record ->
                HorizontalDivider()
                Text("${record.event.name.replace('_', ' ')} · ${record.outcome}", style = MaterialTheme.typography.labelLarge)
                Text("${record.subject ?: "Unknown user"} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.timestampEpochMillis))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("This device-local history is not tamper-evident and does not record vehicle commands.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
