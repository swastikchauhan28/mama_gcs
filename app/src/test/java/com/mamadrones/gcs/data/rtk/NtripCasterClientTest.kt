package com.mamadrones.gcs.data.rtk

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class NtripCasterClientTest {
    @Test fun stoppedClientCannotOpenANewSocket() {
        val settings = NtripSettings(NtripScheme.HTTP, "127.0.0.1", 2101, "MOUNT", "demo", "sample")
        val client = NtripCasterClient(settings, true)
        client.close()
        assertEquals("NTRIP stopped.", assertThrows(IOException::class.java) { client.open() }.message)
    }

    private fun withCaster(response: String, check: (NtripCasterClient, () -> String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<String> {
                    listener.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val input = socket.getInputStream()
                        val request = StringBuilder()
                        while (!request.endsWith("\r\n\r\n")) {
                            val next = input.read()
                            if (next < 0 || request.length >= 8_192) throw IOException("Incomplete test request")
                            request.append(next.toChar())
                        }
                        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                        request.toString()
                    }
                }
                val settings = NtripSettings(NtripScheme.HTTP, "127.0.0.1", listener.localPort,
                    "MOUNT", "demo", "sample")
                NtripCasterClient(settings, true).use { client ->
                    check(client) { received.get(5, TimeUnit.SECONDS) }
                }
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun explicitHttpOptInSendsOnlyRequestedMountWithBasicAuthAndReadsRtcm() {
        withCaster("HTTP/1.1 200 OK\r\nContent-Type: gnss/data\r\n\r\nRTCM") { client, request ->
            val bytes = ByteArray(4)
            assertEquals(4, client.open().read(bytes))
            assertEquals("RTCM", bytes.toString(Charsets.US_ASCII))
            val header = request()
            assertTrue(header.startsWith("GET /MOUNT HTTP/1.1\r\n"))
            assertTrue(header.contains("Authorization: Basic " +
                Base64.getEncoder().encodeToString("demo:sample".toByteArray()) + "\r\n"))
            assertFalse(header.contains("demo:sample"))
        }
    }

    @Test fun casterStatusIsReportedWithoutCredentials() {
        withCaster("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n") { client, request ->
            val error = assertThrows(IOException::class.java) { client.open() }
            assertEquals("NTRIP caster returned HTTP 404.", error.message)
            request()
        }
        withCaster("HTTP/1.1 302 Found\r\nLocation: http://other.test/MOUNT\r\n\r\n") { client, request ->
            val error = assertThrows(IOException::class.java) { client.open() }
            assertEquals("NTRIP caster returned HTTP 302.", error.message)
            request() // Redirect is not followed with Basic credentials.
        }
    }

    @Test fun icyAndChunkedStreamsAreHandledWithoutReadingHeadersAsCorrections() {
        withCaster("ICY 200 OK\r\nRTCM") { client, request ->
            val bytes = ByteArray(4)
            assertEquals(4, client.open().read(bytes))
            assertEquals("RTCM", bytes.toString(Charsets.US_ASCII))
            request()
        }
        withCaster("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nRTCM\r\n0\r\n\r\n") { client, request ->
            val input = client.open()
            val bytes = ByteArray(4)
            assertEquals(4, input.read(bytes))
            assertEquals("RTCM", bytes.toString(Charsets.US_ASCII))
            assertEquals(-1, input.read())
            request()
        }
    }

    @Test fun optionalGgaIsWrittenOnlyAfterSuccessfulCasterStatus() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<String> {
                    listener.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val input = socket.getInputStream()
                        val request = StringBuilder()
                        while (!request.toString().endsWith("\r\n\r\n")) {
                            val next = input.read()
                            if (next < 0 || request.length >= 8_192) throw IOException("Incomplete test request")
                            request.append(next.toChar())
                        }
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
                        socket.getOutputStream().flush()
                        val sentence = StringBuilder()
                        while (!sentence.toString().endsWith("\r\n")) {
                            val next = input.read()
                            if (next < 0 || sentence.length > 160) throw IOException("Missing GGA")
                            sentence.append(next.toChar())
                        }
                        sentence.toString()
                    }
                }
                val settings = NtripSettings(NtripScheme.HTTP, "127.0.0.1", listener.localPort,
                    "MOUNT", "demo", "sample")
                NtripCasterClient(settings, true).use { client ->
                    client.open("\$GPGGA,123519.00,4807.0380,N,01131.0000,E,1,08,0.9,545.40,M,,M,,*00\r\n")
                    assertTrue(received.get(5, TimeUnit.SECONDS).startsWith("\$GPGGA,123519.00"))
                }
            } finally { executor.shutdownNow() }
        }
    }
}
