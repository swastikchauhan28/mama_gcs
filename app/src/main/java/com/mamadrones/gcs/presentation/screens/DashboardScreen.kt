package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Mama GCS", "Ground Control Station · offline-first")
        StatusCard("VEHICLE", "NOT CONNECTED", "A vehicle connection will be added in a later phase")
        Text("VEHICLE HEALTH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StatusCard("GPS", "UNKNOWN")
        StatusCard("BATTERY", "UNKNOWN")
        StatusCard("MOTOR TELEMETRY", "UNKNOWN")
        StatusCard("OVERALL HEALTH", "UNKNOWN", "No validated telemetry received")
        Spacer(Modifier.height(4.dp))
        Text("Speed, heading, mode, and armed state will remain unavailable until a real MAVLink transport is connected.", style = MaterialTheme.typography.bodySmall)
    }
}
