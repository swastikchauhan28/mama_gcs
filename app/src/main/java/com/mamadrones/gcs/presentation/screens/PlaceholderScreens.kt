package com.mamadrones.gcs.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun PlaceholderScreen(title: String, message: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(title, "Mama GCS")
        Card { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Phase 1 foundation", fontWeight = FontWeight.Bold); Text(message) } }
    }
}

@Composable fun MapScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Map", "Map rendering and offline tiles are intentionally not configured yet. Vehicle position is UNKNOWN.", modifier)
@Composable fun MissionScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Mission", "Mission planning and MAVLink mission protocol are scheduled for a later phase.", modifier)
@Composable fun HealthScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Vehicle Health", "No telemetry is connected. Health is UNKNOWN by design.", modifier)
@Composable fun ControlScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Control", "Vehicle commands are unavailable until MAVLink command acknowledgement and transport safety controls are implemented.", modifier)
@Composable fun AdminScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Admin", "Local user roles, vehicle pairing, and configuration arrive in a later phase.", modifier)
