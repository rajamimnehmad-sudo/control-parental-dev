package com.contentfilter.user.dag

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class DagImageResourceLoader(
    private val classifier: DagImageClassifier,
    private val onCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit = { _, _ -> },
    private val onManualCandidateReady: (String, DagImageDecision) -> Unit = { _, _ -> },
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
    private val pageGeneration = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private val devCalibrationRevealEnabled = AtomicBoolean(false)
    private val imageSlots = Semaphore(DagImageDeliveryPolicy.MaximumConcurrentImages, true)
    private val responseCache = LinkedHashMap<String, CachedImageResource>(16, 0.75f, true)
    private val manualCalibrationCandidates = LinkedHashMap<String, DagManualCalibrationCandidate>(16, 0.75f, true)
    private var responseCacheBytes = 0

    fun resetPage() {
        pageGeneration.incrementAndGet()
        imageCount.set(0)
        allowedCount.set(0)
        blockedCount.set(0)
        uncertainCount.set(0)
        synchronized(responseCache) {
            responseCache.clear()
            responseCacheBytes = 0
        }
        synchronized(manualCalibrationCandidates) { manualCalibrationCandidates.clear() }
    }

    fun pageSummary(): DagImagePageSummary =
        DagImagePageSummary(
            allowed = allowedCount.get(),
            blocked = blockedCount.get(),
            uncertain = uncertainCount.get(),
        )

    fun cancel() {
        closed.set(true)
        pageGeneration.incrementAndGet()
    }

    fun setDevCalibrationRevealEnabled(enabled: Boolean): Boolean {
        if (devCalibrationRevealEnabled.getAndSet(enabled) == enabled) return false
        resetPage()
        return true
    }

    fun manualCalibrationDecision(imageUrl: String): DagImageDecision? =
        synchronized(manualCalibrationCandidates) {
            manualCalibrationCandidates[normalizeImageUrl(imageUrl)]?.classification?.decision
        }

    fun manualCalibrationDecisions(): Map<String, DagImageDecision> =
        synchronized(manualCalibrationCandidates) {
            manualCalibrationCandidates.mapValues { it.value.classification.decision }
        }

    fun takeManualCalibrationCandidate(imageUrl: String): DagManualCalibrationCandidate? =
        synchronized(manualCalibrationCandidates) {
            manualCalibrationCandidates.remove(normalizeImageUrl(imageUrl))
        }

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String? = null,
    ): WebResourceResponse? {
        if (closed.get()) return unavailableImageResource()
        if (request.isForMainFrame) return null
        if (isBlockedMediaRequest(request.url.toString(), request.requestHeaders)) return blockedResource()
        if (!isProbableImageRequest(request.url.toString(), request.requestHeaders)) return null
        cachedResource(request.url.toString())?.let { return it }
        if (request.url.scheme != "https" || imageCount.incrementAndGet() > MaximumImagesPerPage) {
            return unavailableImageResource()
        }
        val generation = pageGeneration.get()

        val acquired =
            try {
                imageSlots.acquire()
                true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        if (!acquired) {
            uncertainCount.incrementAndGet()
            return unavailableImageResource()
        }
        return try {
            if (closed.get() || generation != pageGeneration.get()) return unavailableImageResource()
            runCatching { loadClassifiedImage(request, pageUrl) }.getOrElse {
                uncertainCount.incrementAndGet()
                unavailableImageResource()
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
            if (!response.isSuccessful || response.request.url.scheme != "https") return unavailableImageResource()
            val body = response.body ?: return unavailableImageResource()
            if (body.contentLength() > DagImageClassifier.MaximumImageBytes) return unavailableImageResource()
            val bytes = body.byteStream().readLimited(DagImageClassifier.MaximumImageBytes)
            val mimeType = body.contentType()?.toString()
            if (isSafeStaticSvg(bytes, mimeType)) {
                allowedCount.incrementAndGet()
                return cacheAndCreateResource(request.url.toString(), bytes, "image/svg+xml", "safe-icon")
            }
            val measuredClassification =
                classifier.classify(
                    bytes,
                    mimeType,
                    dagImageAudienceContext(request.url.toString(), pageUrl),
                )
            val calibrationThumbnail =
                if (measuredClassification.scores.isNotEmpty()) dagCalibrationThumbnail(bytes) else null
            val classification =
                calibrationThumbnail
                    ?.let { classifier.exactDecision(it.dagCalibrationHash()) }
                    ?.let { measuredClassification.copy(decision = it) }
                    ?: measuredClassification
            if (calibrationThumbnail != null) {
                calibrationThumbnail.let { thumbnail ->
                    rememberManualCalibrationCandidate(request.url.toString(), thumbnail, classification)
                }
                onManualCandidateReady(request.url.toString(), classification.decision)
            }
            when (classification.decision) {
                DagImageDecision.Allowed -> allowedCount.incrementAndGet()
                DagImageDecision.Blocked -> blockedCount.incrementAndGet()
                DagImageDecision.Uncertain -> uncertainCount.incrementAndGet()
            }
            if (classification.decision != DagImageDecision.Allowed) {
                if (classification.decision == DagImageDecision.Uncertain) {
                    calibrationThumbnail?.let { onCalibrationCandidate(it, classification) }
                }
                if (devCalibrationRevealEnabled.get()) {
                    return cacheAndCreateResource(
                        request.url.toString(),
                        bytes,
                        classification.mimeType ?: return unavailableImageResource(),
                        classification.decision.name.lowercase(),
                    )
                }
                return blurredImageResource(request.url.toString(), bytes) ?: unavailableImageResource()
            }

            return cacheAndCreateResource(
                request.url.toString(),
                bytes,
                classification.mimeType ?: return unavailableImageResource(),
            )
        }
    }

    private fun blurredImageResource(
        cacheKey: String,
        bytes: ByteArray,
    ): WebResourceResponse? {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val outputWidth = minOf(source.width, MaximumBlurredDimension)
            val outputHeight = maxOf(1, source.height * outputWidth / source.width)
            val tinyWidth = minOf(BlurSampleSide, outputWidth)
            val tinyHeight = maxOf(1, outputHeight * tinyWidth / outputWidth)
            val tiny = Bitmap.createScaledBitmap(source, tinyWidth, tinyHeight, true)
            val blurred = Bitmap.createScaledBitmap(tiny, outputWidth, outputHeight, true)
            val output = ByteArrayOutputStream()
            try {
                if (!blurred.compress(Bitmap.CompressFormat.JPEG, BlurJpegQuality, output)) return null
                val blurredBytes = output.toByteArray()
                cacheAndCreateResource(cacheKey, blurredBytes, "image/jpeg", "blurred")
            } finally {
                if (blurred !== tiny && blurred !== source) blurred.recycle()
                if (tiny !== source) tiny.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun dagCalibrationThumbnail(bytes: ByteArray): ByteArray? {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val scale = minOf(1f, CalibrationThumbnailDimension.toFloat() / maxOf(source.width, source.height))
            val width = maxOf(1, (source.width * scale).toInt())
            val height = maxOf(1, (source.height * scale).toInt())
            val thumbnail = Bitmap.createScaledBitmap(source, width, height, true)
            try {
                ByteArrayOutputStream().use { output ->
                    if (!thumbnail.compress(
                            Bitmap.CompressFormat.JPEG,
                            CalibrationThumbnailQuality,
                            output,
                        )
                    ) {
                        return null
                    }
                    output.toByteArray().takeIf { it.size <= CalibrationThumbnailMaximumBytes }
                }
            } finally {
                if (thumbnail !== source) thumbnail.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun rememberManualCalibrationCandidate(
        imageUrl: String,
        thumbnail: ByteArray,
        classification: DagImageClassification,
    ) {
        synchronized(manualCalibrationCandidates) {
            manualCalibrationCandidates[normalizeImageUrl(imageUrl)] =
                DagManualCalibrationCandidate(thumbnail, classification)
            while (manualCalibrationCandidates.size > MaximumManualCalibrationCandidates) {
                manualCalibrationCandidates.remove(manualCalibrationCandidates.entries.first().key)
            }
        }
    }

    private fun cachedResource(key: String): WebResourceResponse? =
        synchronized(responseCache) { responseCache[key] }?.let {
            imageResource(it.bytes, it.mimeType, it.decision)
        }

    private fun cacheAndCreateResource(
        key: String,
        bytes: ByteArray,
        mimeType: String,
        decision: String? = null,
    ): WebResourceResponse {
        val cached = CachedImageResource(bytes, mimeType, decision)
        if (bytes.size <= MaximumCachedImageBytes) {
            synchronized(responseCache) {
                responseCache.put(key, cached)?.let { responseCacheBytes -= it.bytes.size }
                responseCacheBytes += bytes.size
                while (responseCacheBytes > MaximumResponseCacheBytes || responseCache.size > MaximumCachedImages) {
                    val eldest = responseCache.entries.iterator().next()
                    responseCacheBytes -= eldest.value.bytes.size
                    responseCache.remove(eldest.key)
                }
            }
        }
        return imageResource(cached.bytes, cached.mimeType, cached.decision)
    }

    private fun imageResource(
        bytes: ByteArray,
        mimeType: String,
        decision: String? = null,
    ): WebResourceResponse =
        WebResourceResponse(
            mimeType,
            null,
            200,
            "OK",
            buildMap {
                put("Access-Control-Allow-Origin", "*")
                put("Cache-Control", "no-store")
                put("Content-Length", bytes.size.toString())
                put("X-Content-Type-Options", "nosniff")
                decision?.let { put("X-DAG-Image-Decision", it) }
            },
            ByteArrayInputStream(bytes),
        )

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
        const val MaximumImagesPerPage = 400
        const val RequestTimeoutSeconds = 15L
        const val InitialBufferBytes = 64 * 1024
        const val ReadBufferBytes = 16 * 1024
        const val MaximumBlurredDimension = 480
        const val BlurSampleSide = 4
        const val BlurJpegQuality = 72
        const val CalibrationThumbnailDimension = 512
        const val CalibrationThumbnailQuality = 65
        const val CalibrationThumbnailMaximumBytes = 128 * 1024
        const val MaximumCachedImages = 64
        const val MaximumCachedImageBytes = 1024 * 1024
        const val MaximumResponseCacheBytes = 16 * 1024 * 1024
        const val MaximumManualCalibrationCandidates = 160
        val ForwardedHeaders = setOf("Accept", "Accept-Language", "Referer", "User-Agent")
    }
}

internal data class DagManualCalibrationCandidate(
    val thumbnail: ByteArray,
    val classification: DagImageClassification,
)

internal fun normalizeImageUrl(value: String): String = value.substringBefore('#')

private data class CachedImageResource(
    val bytes: ByteArray,
    val mimeType: String,
    val decision: String?,
)

internal fun isSafeStaticSvg(
    bytes: ByteArray,
    mimeType: String?,
): Boolean {
    if (bytes.size !in 32..MaximumSafeSvgBytes) return false
    if (!mimeType.orEmpty().substringBefore(';').trim().equals("image/svg+xml", ignoreCase = true)) return false
    val svg = bytes.toString(Charsets.UTF_8)
    if (!SvgRootPattern.containsMatchIn(svg)) return false
    if (!svg.contains("</svg>", ignoreCase = true)) return false
    if (ForbiddenSvgPattern.any { it.containsMatchIn(svg) }) return false
    if (
        SvgHrefPattern.findAll(svg).any { match ->
            match.groupValues.drop(1).firstOrNull(String::isNotBlank)?.trim()?.startsWith('#') != true
        }
    ) {
        return false
    }
    val withoutInternalPaintReferences = svg.replace(InternalSvgPaintReferencePattern, "")
    return !ExternalSvgUrlPattern.containsMatchIn(withoutInternalPaintReferences)
}

internal object DagImageDeliveryPolicy {
    // WebView has its own bounded resource pool. Keeping only three synchronous
    // callbacks occupied starves lazy-loaded pages after their first viewport.
    const val MaximumConcurrentImages = 8
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

private fun unavailableImageResource(): WebResourceResponse =
    WebResourceResponse(
        "image/png",
        null,
        200,
        "OK",
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store",
            "Content-Length" to NeutralPlaceholderPng.size.toString(),
            "X-Content-Type-Options" to "nosniff",
            "X-DAG-Image-Decision" to "blurred",
        ),
        ByteArrayInputStream(NeutralPlaceholderPng),
    )

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
        ".ico",
    )

private val BlockedMediaExtensions =
    setOf(
        ".mp4", ".webm", ".m3u8", ".mp3", ".wav", ".ogg", ".mov", ".m4a", ".aac",
    )

private const val MaximumSafeSvgBytes = 64 * 1024

private val NeutralPlaceholderPng by lazy(LazyThreadSafetyMode.PUBLICATION) {
    android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        android.util.Base64.DEFAULT,
    )
}
private val ForbiddenSvgPattern =
    listOf(
        Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE),
        Regex(
            "<(script|style|foreignObject|image|iframe|object|embed|video|audio|animate|set)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex("\\bon[a-z]+\\s*=", RegexOption.IGNORE_CASE),
        Regex("@import|javascript:|data:", RegexOption.IGNORE_CASE),
    )
private val SvgRootPattern = Regex("^\\s*(?:<\\?xml[^>]*>\\s*)?<svg\\b", RegexOption.IGNORE_CASE)
private val SvgHrefPattern =
    Regex(
        """\b(?:xlink:href|href)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        RegexOption.IGNORE_CASE,
    )
private val InternalSvgPaintReferencePattern =
    Regex("url\\s*\\(\\s*(['\"]?)#[A-Za-z_][A-Za-z0-9_.:-]*\\1\\s*\\)", RegexOption.IGNORE_CASE)
private val ExternalSvgUrlPattern = Regex("url\\s*\\(", RegexOption.IGNORE_CASE)
