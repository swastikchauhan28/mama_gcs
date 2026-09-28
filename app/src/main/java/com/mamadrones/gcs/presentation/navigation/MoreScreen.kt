package com.mamadrones.gcs.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("More", fontWeight = FontWeight.Bold)
        Text("Operational tools and local configuration")
        listOf("health" to "Vehicle Health", "control" to "Control", "admin" to "Admin", "settings" to "Settings")
            .forEach { (route, label) ->
                Card { TextButton(onClick = { onNavigate(route) }) { Text(label) } }
            }
    }
}
