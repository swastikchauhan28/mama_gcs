package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.VehicleTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

interface VehicleMavlinkSession : AutoCloseable {
    suspend fun start()
    suspend fun stop()
}

fun interface MavlinkSessionFactory {
    fun create(transport: VehicleTransport, scope: CoroutineScope): VehicleMavlinkSession
}

@Singleton
class DefaultMavlinkSessionFactory @Inject constructor(
    private val router: MavlinkMessageRouter,
    private val vehicleRepository: VehicleRepositoryImpl
) : MavlinkSessionFactory {
    override fun create(transport: VehicleTransport, scope: CoroutineScope): VehicleMavlinkSession =
        MavlinkSession(
            transport = transport,
            parser = MavlinkParser(),
            router = router,
            vehicleRepository = vehicleRepository,
            scope = scope
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MavlinkSessionModule {
    @Binds
    @Singleton
    abstract fun bindMavlinkSessionFactory(factory: DefaultMavlinkSessionFactory): MavlinkSessionFactory
}
