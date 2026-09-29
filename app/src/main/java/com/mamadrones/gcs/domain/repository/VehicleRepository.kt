package com.mamadrones.gcs.domain.repository

import com.mamadrones.gcs.domain.model.VehicleState
import kotlinx.coroutines.flow.StateFlow

/** Domain-facing vehicle state; presentation never sees transport bytes or MAVLink frames. */
interface VehicleRepository {
    val vehicleState: StateFlow<VehicleState>
}
