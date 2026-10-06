package com.mamadrones.gcs.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mamadrones.gcs.presentation.components.Notice
import com.mamadrones.gcs.presentation.components.ScreenBody
import com.mamadrones.gcs.presentation.components.ScreenHeader
import kotlinx.coroutines.delay

@Composable
fun VescBluetoothDiscoveryScreen(
    state: VescDiscoveryUiState = VescDiscoveryUiState(),
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onPermissionDenied: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { requiredScanPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) onStartScan() else onPermissionDenied()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onStopScan()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopScan()
        }
    }
    LaunchedEffect(state.scanning) {
        if (state.scanning) {
            delay(SCAN_DURATION_MILLIS)
            onStopScan()
        }
    }

    ScreenBody(modifier) {
        ScreenHeader("VESC Bluetooth discovery", "Identify the nearby radio before protocol integration")
        Notice(
            "DISCOVERY ONLY · NO CONTROLLER CONNECTION",
            "This performs a short BLE advertisement scan. It does not pair, connect, read VESC telemetry, or send motor commands. Results stay on screen only; MAC addresses are not displayed or saved.",
        )
        Notice(
            "BLE VS CLASSIC",
            "This scan finds BLE advertisements only. A Bluetooth Classic / SPP module will not appear here. Close VESC Tool before scanning if it is connected; some modules stop advertising during an active connection. Android may also omit some beacon-style advertisements because location is not used.",
        )
        Notice(
            "SCAN PERMISSION",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Android will ask for Nearby devices access. On Android 11 and earlier, the system requires Location permission for BLE scanning; Mama GCS does not read or save your location."
            else "Android will ask for Location permission because this version requires it for BLE scanning; Mama GCS does not read or save your location.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) onStartScan() else permissionLauncher.launch(missing.toTypedArray())
                },
                enabled = !state.scanning && state.bleSupported,
                modifier = Modifier.heightIn(min = 48.dp).testTag("vesc-ble-scan"),
            ) { Text(if (state.finished) "Scan again" else "Scan for BLE devices") }
            if (state.scanning) OutlinedButton(
                onClick = onStopScan,
                modifier = Modifier.heightIn(min = 48.dp).testTag("vesc-ble-stop"),
            ) { Text("Stop scan") }
        }
        if (!state.bleSupported) {
            Notice("BLE UNAVAILABLE", "This phone does not report BLE support. Bluetooth Classic devices cannot be scanned here.")
        } else if (state.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Scanning for up to 12 seconds…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Notice("SCAN STATUS", it) }
        when {
            state.scanning && state.advertisements.isEmpty() -> Text("No BLE advertisements found yet.")
            state.finished && state.advertisements.isEmpty() && state.error == null -> Text(
                "No BLE advertisements found. This does not rule out a Bluetooth Classic module or a BLE module that is connected, asleep, or not advertising."
            )
        }
        state.advertisements.forEach { result ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(result.name, style = MaterialTheme.typography.titleMedium)
                    Text("Signal ${result.rssiDbm} dBm", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (result.serviceUuids.isEmpty()) "No service UUID advertised"
                        else "Advertised services: ${result.serviceUuids.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Notice(
            "NEXT HARDWARE EVIDENCE",
            "Send the matching device name and advertised service UUIDs to the hardware team. Also capture the VESC Tool hardware/firmware identification for both motor channels and the Bluetooth module label. Discovery does not prove protocol compatibility.",
        )
    }
}

private fun requiredScanPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private const val SCAN_DURATION_MILLIS = 12_000L
