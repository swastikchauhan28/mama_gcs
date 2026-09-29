package com.mamadrones.gcs.di

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.domain.repository.VehicleRepository
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindSettingsRepository(implementation: LocalSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindVehicleRepository(implementation: VehicleRepositoryImpl): VehicleRepository
}
