package com.contentfilter.user.dag

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class DagImageResourceLoader(
    private val classifier: DagImageClassifier,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        Dns.SYSTEM.lookup(hostname).takeIf { addresses ->
                            addresses.isNotEmpty() && addresses.all(::isPublicAddress)
                        } ?: throw java.net.UnknownHostException("DAG blocked a private address")
                },
            )
            .build(),
) {
    private val imageCount = AtomicInteger(0)
    private val allowedCount = AtomicInteger(0)
    private val blockedCount = AtomicInteger(0)
    private val uncertainCount = AtomicInteger(0)
    private val imageSlots = Semaphore(MaximumConcurrentImages, true)

    fun resetPage() {
        imageCount.set(0)
        allowedCount.set(0)
        blockedCount.set(0)
        uncertainCount.set(0)
    }

    fun pageSummary(): DagImagePageSummary =
        DagImagePageSummary(
            allowed = allowedCount.get(),
            blocked = blockedCount.get(),
            uncertain = uncertainCount.get(),
        )

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String? = null,
    ): WebResourceResponse? {
        if (request.isForMainFrame) return null
        if (isBlockedMediaRequest(request.url.toString(), request.requestHeaders)) return blockedResource()
        if (!isProbableImageRequest(request.url.toString(), request.requestHeaders)) return null
        if (request.url.scheme != "https" || imageCount.incrementAndGet() > MaximumImagesPerPage) return blockedResource()

        val acquired =
            try {
                imageSlots.tryAcquire(ImageSlotWaitSeconds, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        if (!acquired) {
            uncertainCount.incrementAndGet()
            return blockedResource()
        }
        return try {
            runCatching { loadClassifiedImage(request, pageUrl) }.getOrElse {
                uncertainCount.incrementAndGet()
                blockedResource()
            }
        } finally {
            imageSlots.release()
        }
    }

    private fun loadClassifiedImage(
        request: WebResourceRequest,
        pageUrl: String?,
    ): WebResourceResponse {
        val requestBuilder = Request.Builder().url(request.url.toString()).get()
        ForwardedHeaders.forEach { name ->
            request.requestHeaders.headerValue(name).takeIf { it.isNotBlank() }?.let { requestBuilder.header(name, it) }
        }
        cookieManager.getCookie(
            request.url.toString(),
        )?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Cookie", it) }
        if (request.requestHeaders.headerValue("Referer").isBlank()) {
            pageUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let {
                requestBuilder.header("Referer", it)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful || response.request.url.scheme != "https") return blockedResource()
            val body = response.body ?: return blockedResource()
            if (body.contentLength() > DagImageClassifier.MaximumImageBytes) return blockedResource()
            val bytes = body.byteStream().readLimited(DagImageClassifier.MaximumImageBytes)
            val mimeType = body.contentType()?.toString()
            val classification = classifier.classify(bytes, mimeType)
            when (classification.decision) {
                DagImageDecision.Allowed -> allowedCount.incrementAndGet()
                DagImageDecision.Blocked -> blockedCount.incrementAndGet()
                DagImageDecision.Uncertain -> uncertainCount.incrementAndGet()
            }
            if (classification.decision != DagImageDecision.Allowed) return blockedResource()

            return WebResourceResponse(
                classification.mimeType ?: return blockedResource(),
                null,
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-store",
                    "Content-Length" to bytes.size.toString(),
                    "X-Content-Type-Options" to "nosniff",
                ),
                ByteArrayInputStream(bytes),
            )
        }
    }

    private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, InitialBufferBytes))
        val buffer = ByteArray(ReadBufferBytes)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Image exceeds DAG limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MaximumImagesPerPage = 160
        const val MaximumConcurrentImages = 3
        const val ImageSlotWaitSeconds = 8L
        const val RequestTimeoutSeconds = 8L
        const val InitialBufferBytes = 64 * 1024
        const val ReadBufferBytes = 16 * 1024
        val ForwardedHeaders = setOf("Accept", "Accept-Language", "Referer", "User-Agent")
    }
}

internal data class DagImagePageSummary(
    val allowed: Int,
    val blocked: Int,
    val uncertain: Int,
) {
    val classified: Int
        get() = allowed + blocked + uncertain
}

internal fun isPublicAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return false
    }
    val bytes = address.address
    if (bytes.size == 4) {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        if (first == 100 && second in 64..127) return false
    }
    if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return false
    return true
}

internal fun isProbableImageRequest(
    url: String,
    headers: Map<String, String>,
): Boolean {
    val accept = headers.headerValue("Accept").lowercase()
    if (accept.contains("image/")) return true
    if (headers.headerValue("Sec-Fetch-Dest").equals("image", ignoreCase = true)) return true
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return ImageExtensions.any(path::endsWith)
}

internal fun isBlockedMediaRequest(
    url: String,
    headers: Map<String, String>,
): Boolean {
    val accept = headers.headerValue("Accept").lowercase()
    if (accept.contains("video/") || accept.contains("audio/")) return true
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return BlockedMediaExtensions.any(path::endsWith)
}

private fun Map<String, String>.headerValue(name: String): String =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()

private fun blockedResource(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private val ImageExtensions =
    setOf(
        ".jpg",
        ".jpeg",
        ".png",
        ".webp",
        ".gif",
        ".svg",
        ".bmp",
        ".avif",
        ".heic",
        ".heif",
    )

private val BlockedMediaExtensions =
    setOf(
        ".mp4", ".webm", ".m3u8", ".mp3", ".wav", ".ogg", ".mov", ".m4a", ".aac",
    )
