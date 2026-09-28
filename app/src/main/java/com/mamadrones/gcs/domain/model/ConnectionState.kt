package com.mamadrones.gcs.domain.model

/**
 * Transport-level state. MAVLink heartbeat health is deliberately not inferred here;
 * it is introduced with the MAVLink layer in Phase 3.
 */
data class ConnectionState(
    val status: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
    val detail: String? = null,
    val connectedAtEpochMillis: Long? = null,
    val packetStatistics: PacketStatistics = PacketStatistics()
)

data class PacketStatistics(
    val receivedPackets: Long = 0,
    val transmittedPackets: Long = 0,
    val lastReceivedAtEpochMillis: Long? = null,
    val lastTransmittedAtEpochMillis: Long? = null
)
