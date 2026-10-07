package com.mamadrones.gcs.data.mavlink

import com.mamadrones.gcs.data.repository.VehicleRepositoryImpl
import com.mamadrones.gcs.data.transport.udp.UdpTransport
import com.mamadrones.gcs.data.transport.udp.UdpTransportConfig
import com.mamadrones.gcs.domain.model.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Synthetic telemetry over real loopback sockets, never a vehicle or LAN destination. */
class UdpMavlinkIntegrationTest {
    @Test fun `real UDP socket decodes telemetry rejects other peer and retains closed diagnostics`() = runBlocking {
        DatagramSocket(0).use { peer ->
            DatagramSocket(0).use { otherPeer ->
                val localPort = DatagramSocket(0).use { it.localPort }
                val transport = UdpTransport(UdpTransportConfig("127.0.0.1", peer.localPort, localPort))
                val repository = VehicleRepositoryImpl()
                val session = MavlinkSession(transport, MavlinkParser(), MavlinkMessageRouter(repository), repository,
                    heartbeatTimeoutMillis = 60_000)
                fun send(socket: DatagramSocket, bytes: ByteArray) = socket.send(
                    DatagramPacket(bytes, bytes.size, InetSocketAddress("127.0.0.1", localPort)))
                try {
                    session.start()
                    assertFalse(repository.vehicleState.value.connected)
                    send(otherPeer, heartbeatFrame(systemId = 99))
                    val heartbeat = heartbeatFrame(systemId = 42)
                    send(peer, heartbeat.copyOfRange(0, 5))
                    send(peer, heartbeat.copyOfRange(5, heartbeat.size))
                    val payload = ByteArray(28).apply {
                        putInt32(4, -353_632_621)
                        putInt32(8, 1_491_652_374)
                        putInt32(12, 584_000)
                        putInt16(20, 300)
                        putInt16(22, 400)
                        putUInt16(26, 35_200)
                    }
                    send(peer, mavlinkTestFrame(33, payload, 104, systemId = 42))
                    withTimeout(5_000) {
                        while (repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages != 2L ||
                            transport.connectionState.value.packetStatistics.receivedPackets != 3L) delay(10)
                    }
                    val state = repository.vehicleState.value
                    assertTrue(state.connected)
                    assertEquals(42, state.systemId)
                    assertEquals(-35.3632621, state.position.latitude!!, 0.0000001)
                    assertEquals(149.1652374, state.position.longitude!!, 0.0000001)
                    assertEquals(5.0, state.speedMetersPerSecond!!, 0.001)
                    assertEquals(352.0, state.headingDegrees!!, 0.001)
                    assertEquals(TelemetryLinkKind.UDP, state.mavlinkDiagnostics.linkKind)
                    assertEquals(3L, state.mavlinkDiagnostics.receivedChunks)
                    assertEquals(0L, state.mavlinkDiagnostics.ignoredOtherSource)
                    assertEquals(0L, transport.connectionState.value.packetStatistics.transmittedPackets)
                    session.stop()
                    assertEquals(VehicleConnectionState.DISCONNECTED, repository.vehicleState.value.connectionStatus)
                    assertFalse(repository.vehicleState.value.mavlinkDiagnostics.active)
                    assertEquals(2L, repository.vehicleState.value.mavlinkDiagnostics.acceptedMessages)
                } finally {
                    session.close()
                    transport.close()
                }
            }
        }
    }
}
