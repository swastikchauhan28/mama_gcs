package com.mamadrones.gcs.data.rtk

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class NtripScheme { HTTPS, HTTP }

/** Session-only settings. Never log this value: it contains a caster password. */
data class NtripSettings(
    val scheme: NtripScheme, val host: String, val port: Int, val mountpoint: String,
    val username: String, val password: String,
) {
    init {
        require(host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")) && !host.endsWith('.') &&
            host.split('.').all { it.isNotEmpty() && it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') })
        require(port in 1..65535)
        require(mountpoint.matches(Regex("[A-Za-z0-9][A-Za-z0-9._~-]{0,127}")))
        require(username.length <= 128 && ':' !in username && username.none(Char::isISOControl))
        require(password.length <= 128 && password.none(Char::isISOControl))
        require((username.isEmpty() && password.isEmpty()) || (username.isNotEmpty() && password.isNotEmpty()))
    }

    val displayTarget: String get() = "${scheme.name.lowercase()}://$host:$port/$mountpoint"
    override fun toString(): String = "NtripSettings($displayTarget, credentials=[redacted])"

    companion object {
        fun parse(raw: String): NtripSettings {
            require(raw.length in 1..512 && raw.none { it.isWhitespace() || it.isISOControl() })
            val uri = URI(raw)
            val scheme = when (uri.scheme?.lowercase()) {
                "https" -> NtripScheme.HTTPS
                "http" -> NtripScheme.HTTP
                else -> throw IllegalArgumentException("Unsupported NTRIP scheme")
            }
            require(uri.rawQuery == null && uri.rawFragment == null && uri.host != null && uri.port > 0)
            val path = uri.rawPath ?: ""
            require(path.startsWith('/') && path.length > 1 && '/' !in path.drop(1))
            val rawUserInfo = uri.rawUserInfo
            require(rawUserInfo == null || ':' in rawUserInfo)
            val userInfo = uri.userInfo ?: ""
            val separator = userInfo.indexOf(':')
            val username = if (separator >= 0) userInfo.substring(0, separator) else ""
            val password = if (separator >= 0) userInfo.substring(separator + 1) else ""
            return NtripSettings(scheme, uri.host, uri.port, path.drop(1), username, password)
        }
    }
}

/** One operator-started caster stream. Plain HTTP requires an explicit session opt-in. */
class NtripCasterClient(private val settings: NtripSettings, allowPlainHttp: Boolean) : AutoCloseable {
    private val lifecycleLock = Any()
    @Volatile private var closed = false
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null

    init { require(settings.scheme == NtripScheme.HTTPS || allowPlainHttp) }

    fun open(initialGga: String? = null): InputStream {
        val raw = Socket()
        synchronized(lifecycleLock) {
            if (closed) { raw.close(); throw IOException("NTRIP stopped.") }
            socket = raw // Stop can close a connection that is still establishing.
        }
        try {
            raw.connect(InetSocketAddress(settings.host, settings.port), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            val active = if (settings.scheme == NtripScheme.HTTPS) {
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, settings.host, settings.port, true) as SSLSocket
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                synchronized(lifecycleLock) {
                    if (closed) { tls.close(); throw IOException("NTRIP stopped.") }
                    socket = tls
                }
                tls.startHandshake()
                tls
            } else raw
            active.soTimeout = READ_TIMEOUT_MS
            val outgoing = active.getOutputStream()
            synchronized(lifecycleLock) {
                if (closed) throw IOException("NTRIP stopped.")
                output = outgoing
            }
            val auth = if (settings.username.isNotEmpty()) {
                val encoded = Base64.getEncoder().encodeToString(
                    "${settings.username}:${settings.password}".toByteArray(Charsets.UTF_8))
                "Authorization: Basic $encoded\r\n"
            } else ""
            val request = "GET /${settings.mountpoint} HTTP/1.1\r\n" +
                "Host: ${settings.host}:${settings.port}\r\n" +
                "User-Agent: NTRIP MamaGCS/0.1\r\n" +
                "Ntrip-Version: Ntrip/2.0\r\n" +
                "Accept: application/octet-stream\r\n" + auth +
                "Connection: keep-alive\r\n\r\n"
            outgoing.write(request.toByteArray(Charsets.US_ASCII))
            outgoing.flush()

            val incoming = BufferedInputStream(active.getInputStream(), 8192)
            val status = readAsciiLine(incoming, 1024)
            val match = Regex("^(HTTP/1\\.[01]|ICY) ([0-9]{3})(?: .*)?$").matchEntire(status)
                ?: throw IOException("Unsupported NTRIP caster response.")
            val code = match.groupValues[2].toInt()
            if (code != 200) throw IOException("NTRIP caster returned HTTP $code.")
            if (match.groupValues[1] == "ICY") {
                if (initialGga != null) sendGga(initialGga)
                return incoming // NTRIP v1 streams directly after its status line.
            }

            var headerBytes = 0
            var chunked = false
            while (true) {
                val line = readAsciiLine(incoming, 1024)
                headerBytes += line.length + 2
                if (headerBytes > MAX_HEADER_BYTES) throw IOException("NTRIP response headers are too large.")
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) throw IOException("Malformed NTRIP response header.")
                when (line.substring(0, separator).lowercase()) {
                    "transfer-encoding" -> {
                        if (!line.substring(separator + 1).trim().equals("chunked", ignoreCase = true))
                            throw IOException("Unsupported NTRIP transfer encoding.")
                        chunked = true
                    }
                    "content-encoding" -> if (!line.substring(separator + 1).trim().equals("identity", ignoreCase = true))
                        throw IOException("Compressed NTRIP streams are unsupported.")
                    "content-type" -> if (line.substring(separator + 1).contains("sourcetable", ignoreCase = true))
                        throw IOException("Caster returned a sourcetable instead of corrections.")
                }
            }
            if (initialGga != null) sendGga(initialGga)
            return if (chunked) ChunkedNtripInputStream(incoming) else incoming
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun sendGga(sentence: String) {
        val active = output ?: throw IOException("NTRIP stream is closed.")
        writeGga(active, sentence)
        active.flush()
    }

    override fun close() {
        val current = synchronized(lifecycleLock) {
            closed = true
            val active = socket
            socket = null
            output = null
            active
        }
        runCatching { current?.close() }
    }

    private fun writeGga(destination: OutputStream, sentence: String) {
        require(sentence.length <= 160 && sentence.startsWith("\$GPGGA,") && sentence.endsWith("\r\n"))
        destination.write(sentence.toByteArray(Charsets.US_ASCII))
    }

    private fun readAsciiLine(input: InputStream, limit: Int): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size <= limit) {
            val value = input.read()
            if (value < 0) throw IOException("NTRIP caster closed the response.")
            if (value == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().toString(Charsets.US_ASCII)
            }
            if (value !in 0x20..0x7e && value != '\r'.code)
                throw IOException("Malformed NTRIP response header.")
            bytes.add(value.toByte())
        }
        throw IOException("NTRIP response line is too long.")
    }

    private inner class ChunkedNtripInputStream(private val input: InputStream) : InputStream() {
        private var remaining = 0
        private var finished = false
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            if (finished) return -1
            if (remaining == 0) {
                val sizeLine = readAsciiLine(input, 64).substringBefore(';')
                remaining = sizeLine.trim().toIntOrNull(16)
                    ?.takeIf { it in 0..MAX_CHUNK_BYTES }
                    ?: throw IOException("Invalid NTRIP chunk size.")
                if (remaining == 0) {
                    var trailerBytes = 0
                    while (true) {
                        val line = readAsciiLine(input, 1024)
                        trailerBytes += line.length + 2
                        if (trailerBytes > MAX_HEADER_BYTES) throw IOException("NTRIP trailers are too large.")
                        if (line.isEmpty()) break
                    }
                    finished = true
                    return -1
                }
            }
            val count = input.read(buffer, offset, minOf(length, remaining))
            if (count < 0) throw IOException("NTRIP chunk ended early.")
            remaining -= count
            if (remaining == 0 && (input.read() != '\r'.code || input.read() != '\n'.code))
                throw IOException("Malformed NTRIP chunk ending.")
            return count
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 3_000
        private const val MAX_HEADER_BYTES = 8_192
        private const val MAX_CHUNK_BYTES = 256 * 1024
    }
}
