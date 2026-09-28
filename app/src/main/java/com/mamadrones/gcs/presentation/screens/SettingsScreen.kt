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
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Settings", "Local preferences")
        Card { Column { Text("Transport", fontWeight = FontWeight.Bold); Text("Not configured — UDP and Bluetooth setup is planned for a later phase.") } }
        Card { Column { Text("Units", fontWeight = FontWeight.Bold); Text("Metric (default)") } }
        Card { Column { Text("Theme", fontWeight = FontWeight.Bold); Text("Follows system setting") } }
    }
}
