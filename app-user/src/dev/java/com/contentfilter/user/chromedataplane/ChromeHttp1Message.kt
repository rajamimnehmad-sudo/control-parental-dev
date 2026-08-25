package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class ChromeHttpHeader(
    val name: String,
    val value: String,
)

internal enum class ChromeHttpBodyFraming {
    None,
    ContentLength,
    Chunked,
}

internal data class ChromePhotosProxyRequest(
    val method: String,
    val target: String,
    val version: String = Http11,
    val headers: List<ChromeHttpHeader> = emptyList(),
    val body: ByteArray = ByteArray(0),
    val bodyFraming: ChromeHttpBodyFraming = ChromeHttpBodyFraming.None,
    val closeAfterResponse: Boolean = false,
) {
    fun headerValues(name: String): List<String> =
        headers.filter { it.name.equals(name, ignoreCase = true) }.map(ChromeHttpHeader::value)

    fun firstHeader(name: String): String? = headerValues(name).firstOrNull()

    fun hasUpgrade(): Boolean =
        firstHeader("Upgrade") != null ||
            headerValues("Connection")
                .flatMap { it.split(',') }
                .any { it.trim().equals("upgrade", ignoreCase = true) }

    fun authorityMatches(expectedHost: String): Boolean {
        val values = headerValues("Host")
        if (version == Http11 && values.size != 1) return false
        if (values.isEmpty()) return true
        val authority = values.single().trim()
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "443").toIntOrNull() ?: return false
        return port == 443 && runCatching { normalizeDnsHost(host) }.getOrNull() == expectedHost
    }

    companion object {
        const val Head = "HEAD"
        const val Http11 = "HTTP/1.1"
        val AllowedMethods = setOf("GET", Head, "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

        fun parse(line: String): ChromePhotosProxyRequest? {
            val parts = line.split(' ')
            if (parts.size != 3 || parts[2] !in setOf("HTTP/1.0", Http11)) return null
            if (!parts[1].startsWith('/') || parts[1].startsWith("//") || '#' in parts[1]) return null
            val method = parts[0].uppercase(Locale.US)
            if (method !in AllowedMethods) return null
            return ChromePhotosProxyRequest(method = method, target = parts[1], version = parts[2])
        }
    }
}

internal class ChromeHttpProtocolException(
    val statusCode: Int,
    message: String,
) : Exception(message)

internal class ChromeHttp1RequestReader(
    private val maximumLineBytes: Int = DefaultMaximumLineBytes,
    private val maximumHeaderBytes: Int = DefaultMaximumHeaderBytes,
    private val maximumHeaderCount: Int = DefaultMaximumHeaderCount,
    private val maximumBodyBytes: Int = DefaultMaximumBodyBytes,
) {
    init {
        require(maximumLineBytes > 0)
        require(maximumHeaderBytes >= maximumLineBytes)
        require(maximumHeaderCount > 0)
        require(maximumBodyBytes > 0)
    }

    fun read(
        input: InputStream,
        onContinue: () -> Unit = {},
    ): ChromePhotosProxyRequest? {
        var requestLine = input.readCrlfLine(maximumLineBytes) ?: return null
        while (requestLine.isEmpty()) requestLine = input.readCrlfLine(maximumLineBytes) ?: return null
        val parsed =
            ChromePhotosProxyRequest.parse(requestLine)
                ?: throw ChromeHttpProtocolException(400, "Malformed request line")
        val headers = readHeaders(input)
        val framing = bodyFraming(headers)
        if (
            headers.values("Expect").any { it.equals("100-continue", ignoreCase = true) } &&
            framing != ChromeHttpBodyFraming.None
        ) {
            onContinue()
        }
        val body =
            when (framing) {
                ChromeHttpBodyFraming.None -> ByteArray(0)
                ChromeHttpBodyFraming.ContentLength -> {
                    val length = contentLength(headers)
                    if (length > maximumBodyBytes) throw ChromeHttpProtocolException(413, "Request body too large")
                    input.readExactly(length)
                }
                ChromeHttpBodyFraming.Chunked -> readChunked(input)
            }
        val close =
            headers.connectionTokens().any { it == "close" } ||
                (parsed.version == "HTTP/1.0" && headers.connectionTokens().none { it == "keep-alive" })
        return parsed.copy(
            headers = headers,
            body = body,
            bodyFraming = framing,
            closeAfterResponse = close,
        )
    }

    private fun readHeaders(input: InputStream): List<ChromeHttpHeader> {
        val headers = mutableListOf<ChromeHttpHeader>()
        var totalBytes = 0
        repeat(maximumHeaderCount) {
            val line =
                input.readCrlfLine(maximumLineBytes)
                    ?: throw ChromeHttpProtocolException(400, "Truncated headers")
            totalBytes += line.length + CrlfBytes
            if (totalBytes > maximumHeaderBytes) throw ChromeHttpProtocolException(431, "Headers too large")
            if (line.isEmpty()) return headers
            if (line.firstOrNull()?.isWhitespace() == true) {
                throw ChromeHttpProtocolException(400, "Obsolete folded header")
            }
            val separator = line.indexOf(':')
            if (separator <= 0) throw ChromeHttpProtocolException(400, "Malformed header")
            val name = line.substring(0, separator)
            val value = line.substring(separator + 1).trim(' ', '\t')
            if (!name.all(::isHeaderTokenCharacter) || value.any { it == '\r' || it == '\n' || it.code == 0 }) {
                throw ChromeHttpProtocolException(400, "Invalid header")
            }
            headers += ChromeHttpHeader(name, value)
        }
        throw ChromeHttpProtocolException(431, "Too many headers")
    }

    private fun bodyFraming(headers: List<ChromeHttpHeader>): ChromeHttpBodyFraming {
        if (headers.values("Trailer").isNotEmpty()) {
            throw ChromeHttpProtocolException(501, "Request trailers unsupported")
        }
        val transferEncodings =
            headers
                .values("Transfer-Encoding")
                .flatMap { it.split(',') }
                .map { it.trim().lowercase() }
        val contentLengths = headers.values("Content-Length")
        if (transferEncodings.isNotEmpty() && contentLengths.isNotEmpty()) {
            throw ChromeHttpProtocolException(400, "Conflicting body framing")
        }
        if (transferEncodings.isNotEmpty()) {
            if (transferEncodings != listOf("chunked")) {
                throw ChromeHttpProtocolException(501, "Unsupported transfer coding")
            }
            return ChromeHttpBodyFraming.Chunked
        }
        if (contentLengths.isEmpty()) return ChromeHttpBodyFraming.None
        contentLength(headers)
        return ChromeHttpBodyFraming.ContentLength
    }

    private fun contentLength(headers: List<ChromeHttpHeader>): Int {
        val values = headers.values("Content-Length")
        val parsed = values.map { value -> value.toLongOrNull()?.takeIf { it >= 0 } }
        if (parsed.any { it == null } || parsed.distinct().size != 1 || parsed.single()!! > Int.MAX_VALUE) {
            throw ChromeHttpProtocolException(400, "Invalid Content-Length")
        }
        return parsed.single()!!.toInt()
    }

    private fun readChunked(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val line =
                input.readCrlfLine(maximumLineBytes)
                    ?: throw ChromeHttpProtocolException(400, "Truncated chunk header")
            val token = line.substringBefore(';').trim()
            val length =
                token
                    .toLongOrNull(16)
                    ?.takeIf { it >= 0 && it <= Int.MAX_VALUE }
                    ?: throw ChromeHttpProtocolException(400, "Malformed chunk size")
            if (length == 0L) {
                readTrailers(input)
                return output.toByteArray()
            }
            if (output.size().toLong() + length > maximumBodyBytes) {
                throw ChromeHttpProtocolException(413, "Request body too large")
            }
            output.write(input.readExactly(length.toInt()))
            if (input.read() != '\r'.code || input.read() != '\n'.code) {
                throw ChromeHttpProtocolException(400, "Malformed chunk terminator")
            }
        }
    }

    private fun readTrailers(input: InputStream) {
        repeat(maximumHeaderCount) {
            val line =
                input.readCrlfLine(maximumLineBytes)
                    ?: throw ChromeHttpProtocolException(400, "Truncated trailers")
            if (line.isEmpty()) return
            val separator = line.indexOf(':')
            if (separator <= 0 || !line.substring(0, separator).all(::isHeaderTokenCharacter)) {
                throw ChromeHttpProtocolException(400, "Malformed trailer")
            }
        }
        throw ChromeHttpProtocolException(431, "Too many trailers")
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(bytes, offset, length - offset)
            if (read < 0) throw ChromeHttpProtocolException(400, "Truncated request body")
            offset += read
        }
        return bytes
    }

    private fun InputStream.readCrlfLine(maximumBytes: Int): String? {
        val output = ByteArrayOutputStream(minOf(maximumBytes, 256))
        var sawCarriageReturn = false
        while (output.size() <= maximumBytes) {
            val next = read()
            if (next < 0) {
                if (output.size() == 0 && !sawCarriageReturn) return null
                throw EOFException("Truncated HTTP line")
            }
            if (sawCarriageReturn) {
                if (next != '\n'.code) throw ChromeHttpProtocolException(400, "HTTP line requires CRLF")
                return output.toString(StandardCharsets.US_ASCII.name())
            }
            if (next == '\r'.code) {
                sawCarriageReturn = true
            } else if (next == '\n'.code) {
                throw ChromeHttpProtocolException(400, "Bare LF is not accepted")
            } else {
                output.write(next)
            }
        }
        throw ChromeHttpProtocolException(431, "HTTP line too long")
    }

    private companion object {
        const val DefaultMaximumLineBytes = 8 * 1024
        const val DefaultMaximumHeaderBytes = 64 * 1024
        const val DefaultMaximumHeaderCount = 100
        const val DefaultMaximumBodyBytes = 16 * 1024 * 1024
        const val CrlfBytes = 2
    }
}

internal object ChromeHttp1Wire {
    fun writeAscii(
        output: OutputStream,
        value: String,
    ) {
        output.write(value.toByteArray(StandardCharsets.US_ASCII))
    }
}

private fun List<ChromeHttpHeader>.values(name: String): List<String> =
    filter { it.name.equals(name, ignoreCase = true) }.map(ChromeHttpHeader::value)

private fun List<ChromeHttpHeader>.connectionTokens(): Set<String> =
    values("Connection")
        .flatMap { it.split(',') }
        .mapTo(linkedSetOf()) { it.trim().lowercase(Locale.US) }

private fun isHeaderTokenCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character in "!#$%&'*+-.^_`|~"
