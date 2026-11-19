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
import kotlinx.coroutines.Dispatchers

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
            // Keep the owner's Job, and serialize receive and timeout work off the UI.
            // Serial execution preserves the ordering of heartbeat and timeout updates.
            scope = CoroutineScope(scope.coroutineContext + Dispatchers.Default.limitedParallelism(1))
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MavlinkSessionModule {
    @Binds
    @Singleton
    abstract fun bindMavlinkSessionFactory(factory: DefaultMavlinkSessionFactory): MavlinkSessionFactory
}
