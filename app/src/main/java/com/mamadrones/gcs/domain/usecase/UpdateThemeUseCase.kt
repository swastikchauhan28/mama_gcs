package com.mamadrones.gcs.domain.usecase

import com.mamadrones.gcs.domain.model.ThemeMode
import com.mamadrones.gcs.domain.model.UdpEndpoint
import com.mamadrones.gcs.domain.repository.SettingsRepository
import javax.inject.Inject

class UpdateThemeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(theme: ThemeMode) = repository.setTheme(theme)
}

class UpdateUdpEndpointUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(endpoint: UdpEndpoint?) = repository.setUdpEndpoint(endpoint)
}
