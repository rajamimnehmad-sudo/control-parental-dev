package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.vpn.service.VpnController
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory

internal data class ChromePhotosUpstreamResponse(
    val host: String,
    val statusCode: Int,
    val statusText: String,
    val headers: List<ChromeHttpHeader>,
    val body: InputStream,
    val bodyLength: Long,
    val protocol: String,
)

internal class ChromePhotosUpstreamExchange(
    val response: ChromePhotosUpstreamResponse,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() = closeAction()
}

internal data class ChromePhotosUpstreamMetrics(
    val protectedSocketsCreated: Long,
    val protectSuccess: Long,
    val protectFailure: Long,
)

internal enum class ChromePhotosUpstreamScheme(
    val wireName: String,
) {
    Http("http"),
    Https("https"),
}

internal data class ChromePhotosUpstreamEndpoint(
    val scheme: ChromePhotosUpstreamScheme,
    val host: String,
    val port: Int,
) {
    init {
        require(port in 1..65_535)
    }

    companion object {
        const val HttpPort = 80
        const val HttpsPort = 443
    }
}

internal interface ChromePhotosUpstream : AutoCloseable {
    @Throws(IOException::class)
    fun execute(
        host: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamExchange

    @Throws(IOException::class)
    fun execute(
        endpoint: ChromePhotosUpstreamEndpoint,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamExchange {
        require(endpoint.scheme == ChromePhotosUpstreamScheme.Https)
        require(endpoint.port == ChromePhotosUpstreamEndpoint.HttpsPort)
        return execute(endpoint.host, request)
    }

    fun metrics(): ChromePhotosUpstreamMetrics = ChromePhotosUpstreamMetrics(0, 0, 0)

    override fun close() = Unit
}

internal class ChromePhotosRealUpstream(
    private val upstreamPort: Int = HttpsPort,
    private val destinationAuthority: ChromePublicDestinationAuthority = ChromePublicDestinationAuthority(),
    private val protectedSocketFactory: ChromeProtectedSocketFactory =
        ChromeProtectedSocketFactory(VpnController::protectDevUpstreamSocket),
    private val client: OkHttpClient = defaultClient(destinationAuthority, protectedSocketFactory),
) : ChromePhotosUpstream {
    init {
        require(upstreamPort in 1..65_535)
    }

    override fun execute(
        host: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamExchange =
        execute(
            ChromePhotosUpstreamEndpoint(
                scheme = ChromePhotosUpstreamScheme.Https,
                host = host,
                port = upstreamPort,
            ),
            request,
        )

    override fun execute(
        endpoint: ChromePhotosUpstreamEndpoint,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosUpstreamExchange {
        val normalizedHost = normalizeDnsHost(endpoint.host)
        require(request.method in ChromePhotosProxyRequest.AllowedMethods)
        val upstreamRequest =
            Request.Builder()
                .url(buildUrl(endpoint.scheme, normalizedHost, request.target, endpoint.port))
                .method(request.method, request.upstreamBody())
                .apply {
                    ChromeHttpHeaderPolicy.upstreamRequestHeaders(request).forEach { header ->
                        addHeader(header.name, header.value)
                    }
                }
                .build()
        val response = client.newCall(upstreamRequest).execute()
        val body = response.body
        return ChromePhotosUpstreamExchange(
            response =
                ChromePhotosUpstreamResponse(
                    host = normalizedHost,
                    statusCode = response.code,
                    statusText = response.message.sanitizeStatusText(),
                    headers = response.headers.map { pair -> ChromeHttpHeader(pair.first, pair.second) },
                    body = body?.byteStream() ?: EmptyInputStream,
                    bodyLength = body?.contentLength() ?: 0L,
                    protocol = response.protocol.logName(),
                ),
            closeAction = response::close,
        )
    }

    override fun metrics(): ChromePhotosUpstreamMetrics = protectedSocketFactory.metrics()

    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    internal companion object {
        const val HttpsPort = 443
        const val DefaultMaximumBodyBytes = 12 * 1024 * 1024
        private const val TimeoutSeconds = 20L

        fun defaultClient(
            destinationAuthority: ChromePublicDestinationAuthority = ChromePublicDestinationAuthority(),
            socketFactory: SocketFactory = SocketFactory.getDefault(),
        ): OkHttpClient =
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .dns(ChromeAuthorityDns(destinationAuthority))
                .socketFactory(socketFactory)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(TimeoutSeconds, TimeUnit.SECONDS)
                .build()

        fun buildUrl(
            scheme: ChromePhotosUpstreamScheme,
            host: String,
            target: String,
            port: Int,
        ) = "${scheme.wireName}://$host${if (port == scheme.defaultPort()) "" else ":$port"}$target"
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

        fun buildUrl(
            host: String,
            target: String,
            port: Int = HttpsPort,
        ) = buildUrl(ChromePhotosUpstreamScheme.Https, host, target, port)
    }
}

private fun ChromePhotosUpstreamScheme.defaultPort(): Int =
    when (this) {
        ChromePhotosUpstreamScheme.Http -> ChromePhotosUpstreamEndpoint.HttpPort
        ChromePhotosUpstreamScheme.Https -> ChromePhotosUpstreamEndpoint.HttpsPort
    }

internal class ChromeProtectedSocketFactory(
    private val protect: (Socket) -> Boolean,
    private val delegate: SocketFactory = SocketFactory.getDefault(),
) : SocketFactory() {
    private val created = AtomicLong()
    private val succeeded = AtomicLong()
    private val failed = AtomicLong()

    override fun createSocket(): Socket = createProtectedSocket(local = null)

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = connect(InetSocketAddress(host, port), null)

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = connect(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = connect(InetSocketAddress(host, port), null)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = connect(InetSocketAddress(address, port), InetSocketAddress(localAddress, localPort))

    fun metrics(): ChromePhotosUpstreamMetrics =
        ChromePhotosUpstreamMetrics(
            protectedSocketsCreated = created.get(),
            protectSuccess = succeeded.get(),
            protectFailure = failed.get(),
        )

    private fun createProtectedSocket(local: SocketAddress?): Socket {
        val socket = delegate.createSocket()
        try {
            socket.bind(local ?: InetSocketAddress(0))
            return protect(socket)
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun protect(socket: Socket): Socket {
        created.incrementAndGet()
        if (runCatching { protect.invoke(socket) }.getOrDefault(false)) {
            succeeded.incrementAndGet()
            return socket
        }
        failed.incrementAndGet()
        runCatching { socket.close() }
        throw IOException("VPN socket protection unavailable")
    }

    private fun connect(
        remote: SocketAddress,
        local: SocketAddress?,
    ): Socket =
        createProtectedSocket(local).apply {
            try {
                connect(remote)
            } catch (error: Throwable) {
                close()
                throw error
            }
        }
}

internal data class BoundedBytes(
    val bytes: ByteArray,
    val exceeded: Boolean,
)

internal fun InputStream.readBounded(maximumBytes: Int): BoundedBytes {
    val output = ByteArrayOutputStream(minOf(maximumBytes, InitialBufferBytes))
    val buffer = ByteArray(ReadBufferBytes)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maximumBytes) return BoundedBytes(ByteArray(0), exceeded = true)
        output.write(buffer, 0, read)
    }
    return BoundedBytes(output.toByteArray(), exceeded = false)
}

private fun ChromePhotosProxyRequest.upstreamBody(): RequestBody? {
    val permitsBody = method in setOf("POST", "PUT", "PATCH") || body.isNotEmpty()
    if (!permitsBody) return null
    val mediaType = firstHeader("Content-Type")?.toMediaTypeOrNull()
    if (bodyFraming != ChromeHttpBodyFraming.Chunked) return body.toRequestBody(mediaType)
    val bytes = body
    return object : RequestBody() {
        override fun contentType() = mediaType

        override fun contentLength(): Long = -1L

        override fun writeTo(sink: BufferedSink) {
            sink.write(bytes)
        }
    }
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

private object EmptyInputStream : InputStream() {
    override fun read(): Int = -1
}

private const val InitialBufferBytes = 32 * 1024
private const val ReadBufferBytes = 32 * 1024
private const val MaxStatusTextLength = 64
