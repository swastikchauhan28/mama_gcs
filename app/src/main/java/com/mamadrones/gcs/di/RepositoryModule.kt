package com.mamadrones.gcs.di

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.DefaultUdpTransportFactory
import com.mamadrones.gcs.data.transport.UdpTransportFactory
import com.mamadrones.gcs.domain.repository.VehicleRepository
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.MissionDraftGeoJsonCodec
import com.mamadrones.gcs.domain.repository.MissionDraftRepository
import com.mamadrones.gcs.domain.repository.MissionDraftFileCodec
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
    abstract fun bindMissionDraftRepository(implementation: LocalMissionDraftRepository): MissionDraftRepository

    @Binds
    abstract fun bindMissionDraftFileCodec(implementation: MissionDraftGeoJsonCodec): MissionDraftFileCodec

    @Binds
    abstract fun bindVehicleRepository(implementation: VehicleRepositoryImpl): VehicleRepository

    @Binds
    abstract fun bindUdpTransportFactory(implementation: DefaultUdpTransportFactory): UdpTransportFactory
}
