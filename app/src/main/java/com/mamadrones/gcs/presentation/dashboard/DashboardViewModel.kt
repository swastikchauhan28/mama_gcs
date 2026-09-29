package com.mamadrones.gcs.presentation.dashboard

import androidx.lifecycle.ViewModel
import com.mamadrones.gcs.domain.usecase.ObserveVehicleStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.mamadrones.gcs.domain.model.VehicleState

@HiltViewModel
class DashboardViewModel @Inject constructor(observeVehicle: ObserveVehicleStateUseCase) : ViewModel() {
    val vehicleState: StateFlow<VehicleState> = observeVehicle()
}
