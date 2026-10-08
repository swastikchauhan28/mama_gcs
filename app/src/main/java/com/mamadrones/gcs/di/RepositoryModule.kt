package com.mamadrones.gcs.di

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.DefaultUdpTransportFactory
import com.mamadrones.gcs.data.transport.UdpTransportFactory
import com.mamadrones.gcs.domain.repository.VehicleRepository
import com.mamadrones.gcs.domain.repository.SettingsRepository
import com.mamadrones.gcs.data.local.datastore.LocalSettingsRepository
import com.mamadrones.gcs.data.local.datastore.LocalMissionDraftRepository
import com.mamadrones.gcs.data.local.datastore.MissionDraftRouteCodec
import com.mamadrones.gcs.domain.repository.MissionDraftRepository
import com.mamadrones.gcs.domain.repository.MissionDraftFileCodec
import com.mamadrones.gcs.domain.repository.AccessRepository
import com.mamadrones.gcs.data.local.datastore.LocalAccessRepository
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
    abstract fun bindAccessRepository(implementation: LocalAccessRepository): AccessRepository

    @Binds
    abstract fun bindMissionDraftRepository(implementation: LocalMissionDraftRepository): MissionDraftRepository

    @Binds
    abstract fun bindMissionDraftFileCodec(implementation: MissionDraftRouteCodec): MissionDraftFileCodec

    @Binds
    abstract fun bindVehicleRepository(implementation: VehicleRepositoryImpl): VehicleRepository

    @Binds
    abstract fun bindUdpTransportFactory(implementation: DefaultUdpTransportFactory): UdpTransportFactory
}
