package com.mamadrones.gcs.data.transport

import com.mamadrones.gcs.data.transport.udp.UdpTransport
import com.mamadrones.gcs.data.transport.udp.UdpTransportConfig
import com.mamadrones.gcs.domain.model.UdpEndpoint
import javax.inject.Inject
import javax.inject.Singleton

/** Builds a new socket per explicit connection attempt; transports are never shared across profiles. */
fun interface UdpTransportFactory {
    fun create(endpoint: UdpEndpoint): VehicleTransport
}

@Singleton
class DefaultUdpTransportFactory @Inject constructor() : UdpTransportFactory {
    override fun create(endpoint: UdpEndpoint): VehicleTransport = UdpTransport(
        UdpTransportConfig(
            remoteHost = endpoint.remoteHost,
            remotePort = endpoint.remotePort,
            localPort = endpoint.localPort
        )
    )
}
