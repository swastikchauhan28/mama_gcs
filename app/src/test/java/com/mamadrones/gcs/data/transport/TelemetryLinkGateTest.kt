package com.mamadrones.gcs.data.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TelemetryLinkGateTest {
    @Test fun `blocks competing links until cleanup releases the lease`() {
        val gate = TelemetryLinkGate()
        val ble = gate.acquire("BLE")
        assertThrows(IllegalStateException::class.java) { gate.acquire("UDP") }
        assertThrows(IllegalStateException::class.java) { gate.acquire("BLE") }
        gate.release(ble)
        val udp = gate.acquire("UDP")
        gate.release(ble) // An old callback cannot release a new session's lease.
        assertThrows(IllegalStateException::class.java) { gate.acquire("BLE") }
        gate.release(udp)
        gate.release(gate.acquire("BLE"))
    }

    @Test fun `simultaneous requests admit exactly one producer`() {
        val gate = TelemetryLinkGate()
        val start = CountDownLatch(1)
        val successes = AtomicInteger()
        val threads = List(8) { index -> Thread {
            start.await()
            try { gate.acquire("link $index"); successes.incrementAndGet() }
            catch (_: IllegalStateException) { }
        }.apply { start() } }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, successes.get())
    }
}
