package com.mamadrones.gcs.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.mamadrones.gcs.domain.model.HomePositionEvidence
import com.mamadrones.gcs.domain.model.VehicleState
import com.mamadrones.gcs.presentation.components.PanelSpec
import com.mamadrones.gcs.presentation.components.SubsystemCard
import kotlinx.coroutines.delay

object HomePositionPanel {
    fun panel(state: VehicleState, now: Long): PanelSpec {
        val sample = state.homePosition
        val eligible = HomePositionEvidence.belongsToSession(state, now)
        val visible = HomePositionEvidence.visibleOnMap(state, now) != null
        val valid = eligible && sample?.hasCoordinate == true
        val status = when {
            sample == null -> "NOT RECEIVED"
            !eligible -> "SESSION / CLOCK MISMATCH · VALUES HIDDEN"
            !sample.hasCoordinate -> "INVALID COORDINATES · MARKER HIDDEN"
            !visible -> "LAST OBSERVATION · LINK NOT LIVE"
            else -> "REPORTED HOME · NOT VERIFIED"
        }
        return PanelSpec("Reported home", status, listOf(
            "Latitude" to if (valid) sample?.latitude.coordinateReading() else "—",
            "Longitude" to if (valid) sample?.longitude.coordinateReading() else "—",
            "Altitude MSL" to if (valid) sample?.altitudeMetersMsl.reading("m") else "—",
        ), note = (if (eligible) "Observed ${(now - sample!!.receivedAtEpochMillis) / 1_000} s ago. " else "") +
            "H on the map is the last HOME_POSITION received in this session while the link is live. " +
            "It is not the phone location or a verified safe return point. No Set Home, Return or message request is sent.")
    }
}

@Composable
fun HomePositionCard(state: VehicleState) {
    val clockTick by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    val now = remember(clockTick, state) { System.currentTimeMillis() }
    SubsystemCard(HomePositionPanel.panel(state, now))
}
