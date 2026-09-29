package com.mamadrones.gcs.domain.usecase

import com.mamadrones.gcs.domain.repository.VehicleRepository
import javax.inject.Inject

class ObserveVehicleStateUseCase @Inject constructor(private val repository: VehicleRepository) {
    operator fun invoke() = repository.vehicleState
}
