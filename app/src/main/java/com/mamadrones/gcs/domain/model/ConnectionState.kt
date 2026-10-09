package com.mamadrones.gcs.domain.model

/**
 * Transport lifecycle only. It does not establish MAVLink liveness, vehicle identity,
 * or permission to control a vehicle.
 */
data class ConnectionState(
    val status: TransportStatus = TransportStatus.DISCONNECTED,
    val detail: String? = null,
    val connectedAtEpochMillis: Long? = null,
    val packetStatistics: PacketStatistics = PacketStatistics()
)

enum class TransportStatus { DISCONNECTED, CONNECTING, OPEN, ERROR }

data class PacketStatistics(
    val receivedPackets: Long = 0,
    val transmittedPackets: Long = 0,
    val lastReceivedAtEpochMillis: Long? = null,
    val lastTransmittedAtEpochMillis: Long? = null
)
