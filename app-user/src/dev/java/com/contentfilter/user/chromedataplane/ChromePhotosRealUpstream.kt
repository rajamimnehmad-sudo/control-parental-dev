package com.contentfilter.user.chromedataplane

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal data class ChromePhotosUpstreamResponse(
    val host: String,
    val statusCode: Int,
    val statusText: String,
    val contentType: String?,
    val contentEncoding: String?,
    val location: String?,
    val body: ByteArray,
    val bodyTooLarge: Boolean,
    val protocol: String,
)

internal interface ChromePhotosUpstream : AutoCloseable {
    @Throws(IOException::class)
    fun execute(
        host: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamResponse

    override fun close() = Unit
}

internal class ChromePhotosRealUpstream(
    private val maximumBodyBytes: Int = DefaultMaximumBodyBytes,
    private val upstreamPort: Int = HttpsPort,
    private val client: OkHttpClient = defaultClient(),
) : ChromePhotosUpstream {
    init {
        require(maximumBodyBytes > 0)
        require(upstreamPort in 1..65_535)
    }

    override fun execute(
        host: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamResponse {
        val normalizedHost = normalizeDnsHost(host)
        require(request.method in ChromePhotosProxyRequest.AllowedMethods)
        val url = buildUrl(normalizedHost, request.target, upstreamPort)
        val upstreamRequest =
            Request.Builder().url(url).method(request.method, null).apply {
                ChromePhotosUpstreamRequestPolicy.headers.forEach(::header)
            }.build()

        return client.newCall(upstreamRequest).execute().use { response ->
            val body = response.body
            val contentLength = body?.contentLength() ?: 0L
            val bodyExpected = request.method != ChromePhotosProxyRequest.Head && response.code !in RedirectCodes
            val tooLarge = bodyExpected && contentLength > maximumBodyBytes
            val boundedBody =
                if (!bodyExpected || body == null || tooLarge) {
                    BoundedBytes(ByteArray(0), exceeded = false)
                } else {
                    body.byteStream().readBounded(maximumBodyBytes)
                }
            ChromePhotosUpstreamResponse(
                host = normalizedHost,
                statusCode = response.code,
                statusText = response.message.sanitizeStatusText(),
                contentType = response.header("Content-Type"),
                contentEncoding = response.header("Content-Encoding"),
                location = response.header("Location"),
                body = boundedBody.bytes,
                bodyTooLarge = tooLarge || boundedBody.exceeded,
                protocol = response.protocol.logName(),
            )
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    internal companion object {
        const val DefaultMaximumBodyBytes = 12 * 1024 * 1024
        const val HttpsPort = 443
        val RedirectCodes = 300..399

        private const val TimeoutSeconds = 20L

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .build()

        fun buildUrl(
            host: String,
            target: String,
            port: Int = HttpsPort,
        ) = "https://$host${if (port == HttpsPort) "" else ":$port"}$target"
            .toHttpUrlOrNull()
            ?.takeIf { url ->
                target.startsWith('/') &&
                    !target.startsWith("//") &&
                    '#' !in target &&
                    url.host == host &&
                    url.port == port &&
                    url.username.isEmpty() &&
                    url.password.isEmpty()
            }
            ?: error("Invalid upstream target")
    }
}

internal object ChromePhotosUpstreamRequestPolicy {
    val headers: Map<String, String> =
        linkedMapOf(
            "Accept" to "image/avif,image/webp,image/png,image/jpeg,image/*,*/*;q=0.8",
            "Accept-Encoding" to "identity",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
        )

    val strippedRequestHeaders: Set<String> =
        setOf(
            "authorization",
            "cookie",
            "proxy-authorization",
            "if-none-match",
            "if-modified-since",
            "if-range",
            "range",
        )
}

internal data class BoundedBytes(
    val bytes: ByteArray,
    val exceeded: Boolean,
)

internal fun java.io.InputStream.readBounded(maximumBytes: Int): BoundedBytes =
    use { input ->
        val output = ByteArrayOutputStream(minOf(maximumBytes, InitialBufferBytes))
        val buffer = ByteArray(ReadBufferBytes)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maximumBytes) return@use BoundedBytes(ByteArray(0), exceeded = true)
            output.write(buffer, 0, read)
        }
        BoundedBytes(output.toByteArray(), exceeded = false)
    }

private fun String.sanitizeStatusText(): String =
    filter { character -> character.code in 32..126 }
        .take(MaxStatusTextLength)
        .ifBlank { "OK" }

private fun Protocol.logName(): String =
    when (this) {
        Protocol.HTTP_2 -> "h2"
        Protocol.HTTP_1_1 -> "http/1.1"
        else -> toString()
    }

private const val InitialBufferBytes = 32 * 1024
private const val ReadBufferBytes = 32 * 1024
private const val MaxStatusTextLength = 64
