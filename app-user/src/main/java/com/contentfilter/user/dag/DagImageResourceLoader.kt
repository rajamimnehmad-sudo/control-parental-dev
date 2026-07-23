package com.contentfilter.user.dag

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class DagImageResourceLoader(
    classifiers: List<DagImageClassifier>,
    private val onCalibrationCandidate: (ByteArray, DagImageClassification) -> Unit = { _, _ -> },
    private val onManualCandidateReady: (String, DagImageDecision) -> Unit = { _, _ -> },
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(RequestTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
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
    init {
        require(classifiers.isNotEmpty()) { "DAG requires at least one image classifier" }
    }

    private val imageCount = AtomicInteger(0)
    private val allowedCount = AtomicInteger(0)
    private val blockedCount = AtomicInteger(0)
    private val uncertainCount = AtomicInteger(0)
    private val pageGeneration = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private val devCalibrationRevealEnabled = AtomicBoolean(false)
    private val pageStateLock = Any()
    private val prioritizedImageUrls = LinkedHashSet<String>()
    private val activeCalls = ConcurrentHashMap<Call, Int>()
    private val imageSlots = Semaphore(DagImageDeliveryPolicy.MaximumConcurrentImages, true)
    private val classifierPool = ArrayBlockingQueue(classifiers.size, true, classifiers)
    private val calibrationClassifier = classifiers.first()
    private val responseCache = LinkedHashMap<String, CachedImageResource>(16, 0.75f, true)
    private val manualCalibrationCandidates = LinkedHashMap<String, DagManualCalibrationCandidate>(16, 0.75f, true)
    private var responseCacheBytes = 0
    private val client =
        client
            .newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    fun resetPage() {
        val currentGeneration =
            synchronized(pageStateLock) {
                val generation = pageGeneration.incrementAndGet()
                imageCount.set(0)
                allowedCount.set(0)
                blockedCount.set(0)
                uncertainCount.set(0)
                responseCache.clear()
                responseCacheBytes = 0
                manualCalibrationCandidates.clear()
                prioritizedImageUrls.clear()
                generation
            }
        activeCalls.forEach { (call, generation) ->
            if (generation != currentGeneration) call.cancel()
        }
    }

    fun pageSummary(): DagImagePageSummary =
        synchronized(pageStateLock) {
            DagImagePageSummary(
                allowed = allowedCount.get(),
                blocked = blockedCount.get(),
                uncertain = uncertainCount.get(),
            )
        }

    fun cancel() {
        synchronized(pageStateLock) {
            closed.set(true)
            pageGeneration.incrementAndGet()
            responseCache.clear()
            responseCacheBytes = 0
            manualCalibrationCandidates.clear()
            prioritizedImageUrls.clear()
        }
        activeCalls.keys.forEach(Call::cancel)
    }

    fun setDevCalibrationRevealEnabled(enabled: Boolean): Boolean {
        if (devCalibrationRevealEnabled.getAndSet(enabled) == enabled) return false
        resetPage()
        return true
    }

    fun manualCalibrationDecision(imageUrl: String): DagImageDecision? =
        synchronized(pageStateLock) {
            manualCalibrationCandidates[normalizeImageUrl(imageUrl)]?.classification?.decision
        }

    fun prioritizeImageUrls(imageUrls: Collection<String>) {
        synchronized(pageStateLock) {
            if (closed.get()) return
            dagAddBoundedPrioritizedImageUrls(
                target = prioritizedImageUrls,
                imageUrls = imageUrls,
                maximumSize = DagImageDeliveryPolicy.MaximumPrioritizedImageUrls,
            )
        }
    }

    fun manualCalibrationDecisions(): Map<String, DagImageDecision> =
        synchronized(pageStateLock) {
            manualCalibrationCandidates.mapValues { it.value.classification.decision }
        }

    fun takeManualCalibrationCandidate(imageUrl: String): DagManualCalibrationCandidate? =
        synchronized(pageStateLock) {
            manualCalibrationCandidates.remove(normalizeImageUrl(imageUrl))
        }

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String? = null,
    ): WebResourceResponse? {
        if (closed.get()) return unavailableImageResource()
        if (request.isForMainFrame) return null
        val requestUrl = request.url.toString()
        val originalImageUrl = dagOriginalImageUrl(requestUrl)
        val imageUrl = originalImageUrl ?: requestUrl
        if (isBlockedMediaRequest(imageUrl, request.requestHeaders)) return blockedResource()
        if (!isProbableImageRequest(requestUrl, request.requestHeaders)) return null
        val generation = pageGeneration.get()
        cachedResource(imageUrl, generation)?.let { return it }
        if (!admitImageUrl(imageUrl, originalImageUrl != null, generation)) {
            return unavailableImageResource()
        }
        if (
            !imageUrl.startsWith("https://", ignoreCase = true) ||
            !reserveImage(generation)
        ) {
            return unavailableImageResource()
        }

        var acquired = false
        try {
            while (!acquired && isCurrentGeneration(generation)) {
                acquired = imageSlots.tryAcquire(ResourceWaitMillis, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (!acquired) {
            return unavailableImageResource()
        }
        return try {
            if (!isCurrentGeneration(generation)) return unavailableImageResource()
            runCatching { loadClassifiedImage(request, pageUrl, imageUrl, generation) }.getOrElse {
                incrementIfCurrent(generation, uncertainCount)
                unavailableImageResource()
            }
        } finally {
            imageSlots.release()
        }
    }

    private fun loadClassifiedImage(
        request: WebResourceRequest,
        pageUrl: String?,
        imageUrl: String,
        generation: Int,
    ): WebResourceResponse {
        val requestBuilder =
            Request
                .Builder()
                .url(imageUrl)
                .get()
                .header("Accept", DisplayImageAcceptHeader)
        val storedCookie =
            if (dagShouldForwardImageCookie(imageUrl, pageUrl)) {
                runCatching { cookieManager.getCookie(imageUrl) }.getOrNull()
            } else {
                null
            }
        dagForwardedImageRequestHeaders(
            requestHeaders = request.requestHeaders,
            imageUrl = imageUrl,
            pageUrl = pageUrl,
            storedCookie = storedCookie,
        ).forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val trackedResponse =
            executeImageRequest(
                initialRequest = requestBuilder.build(),
                pageUrl = pageUrl,
                generation = generation,
            ) ?: return unavailableImageResource()
        try {
            trackedResponse.response.use { response ->
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                if (!response.isSuccessful || response.request.url.scheme != "https") {
                    return unavailableImageResource()
                }
                val body = response.body ?: return unavailableImageResource()
                if (body.contentLength() > DagImageClassifier.MaximumImageBytes) return unavailableImageResource()
                val bytes = body.byteStream().readLimited(DagImageClassifier.MaximumImageBytes)
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                val mimeType = body.contentType()?.toString()
                val corsAllowOrigin = dagSafeCorsAllowOrigin(response.header("Access-Control-Allow-Origin"))
                val corsAllowCredentials =
                    dagSafeCorsAllowCredentials(
                        value = response.header("Access-Control-Allow-Credentials"),
                        allowOrigin = corsAllowOrigin,
                        requestOrigin = response.request.header("Origin"),
                    )
                if (isSafeStaticSvg(bytes, mimeType)) {
                    incrementIfCurrent(generation, allowedCount)
                    return cacheAndCreateResource(
                        imageUrl,
                        bytes,
                        "image/svg+xml",
                        "safe-icon",
                        corsAllowOrigin,
                        corsAllowCredentials,
                        generation,
                    )
                }
                val measuredClassification =
                    withClassifier(generation) { classifier ->
                        classifier.classify(
                            bytes,
                            mimeType,
                            dagImageAudienceContext(imageUrl, pageUrl),
                        )
                    }
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                val calibrationThumbnail =
                    if (shouldCreateCalibrationThumbnail(measuredClassification, devCalibrationRevealEnabled.get())) {
                        dagCalibrationThumbnail(bytes)
                    } else {
                        null
                    }
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                val classification =
                    calibrationThumbnail
                        ?.let { calibrationClassifier.exactDecision(it.dagCalibrationHash()) }
                        ?.let { measuredClassification.copy(decision = it) }
                        ?: measuredClassification
                if (calibrationThumbnail != null) {
                    calibrationThumbnail.let { thumbnail ->
                        rememberManualCalibrationCandidate(
                            imageUrl,
                            thumbnail,
                            classification,
                            generation,
                        )
                    }
                    if (isCurrentGeneration(generation)) {
                        onManualCandidateReady(imageUrl, classification.decision)
                    }
                }
                when (classification.decision) {
                    DagImageDecision.Allowed -> incrementIfCurrent(generation, allowedCount)
                    DagImageDecision.Blocked -> incrementIfCurrent(generation, blockedCount)
                    DagImageDecision.Uncertain -> incrementIfCurrent(generation, uncertainCount)
                }
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                if (classification.decision != DagImageDecision.Allowed) {
                    if (classification.decision == DagImageDecision.Uncertain) {
                        calibrationThumbnail?.let {
                            if (isCurrentGeneration(generation)) onCalibrationCandidate(it, classification)
                        }
                    }
                    if (devCalibrationRevealEnabled.get()) {
                        return cacheAndCreateResource(
                            imageUrl,
                            bytes,
                            classification.mimeType ?: return unavailableImageResource(),
                            classification.decision.name.lowercase(),
                            corsAllowOrigin,
                            corsAllowCredentials,
                            generation,
                        )
                    }
                    return blurredImageResource(
                        imageUrl,
                        bytes,
                        corsAllowOrigin,
                        corsAllowCredentials,
                        generation,
                    )
                        ?: unavailableImageResource()
                }

                val displayResource =
                    displayCompatibleImage(bytes, classification.mimeType)
                        ?: return unavailableImageResource()
                if (!isCurrentGeneration(generation)) return unavailableImageResource()
                return cacheAndCreateResource(
                    imageUrl,
                    displayResource.bytes,
                    displayResource.mimeType,
                    corsAllowOrigin = corsAllowOrigin,
                    corsAllowCredentials = corsAllowCredentials,
                    generation = generation,
                )
            }
        } finally {
            activeCalls.remove(trackedResponse.call)
        }
    }

    private fun executeImageRequest(
        initialRequest: Request,
        pageUrl: String?,
        generation: Int,
    ): TrackedImageResponse? {
        var currentRequest = initialRequest
        var redirectCount = 0
        val deadlineNanos =
            System.nanoTime() +
                TimeUnit.SECONDS.toNanos(RequestTimeoutSeconds)
        while (isCurrentGeneration(generation)) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) return null
            val call = client.newCall(currentRequest)
            call.timeout().timeout(remainingNanos, TimeUnit.NANOSECONDS)
            activeCalls[call] = generation
            if (!isCurrentGeneration(generation)) {
                activeCalls.remove(call)
                call.cancel()
                return null
            }
            val response =
                try {
                    call.execute()
                } catch (error: Throwable) {
                    activeCalls.remove(call)
                    throw error
                }
            if (!isCurrentGeneration(generation)) {
                response.close()
                activeCalls.remove(call)
                return null
            }
            val location =
                response
                    .takeIf { it.code in ImageRedirectStatusCodes }
                    ?.header("Location")
            if (location == null) {
                return TrackedImageResponse(call, response)
            }
            if (redirectCount >= MaximumImageRedirects) {
                response.close()
                activeCalls.remove(call)
                return null
            }
            val redirectUrl = response.request.url.resolve(location)
            response.close()
            activeCalls.remove(call)
            if (redirectUrl == null || redirectUrl.scheme != "https") return null

            val redirectCookie =
                if (dagShouldForwardImageCookie(redirectUrl.toString(), pageUrl)) {
                    runCatching { cookieManager.getCookie(redirectUrl.toString()) }.getOrNull()
                } else {
                    null
                }
            val redirectRequestBuilder =
                currentRequest
                    .newBuilder()
                    .url(redirectUrl)
                    .removeHeader("Cookie")
            dagImageCookieHeader(redirectUrl.toString(), pageUrl, redirectCookie)?.let {
                redirectRequestBuilder.header("Cookie", it)
            }
            currentRequest = redirectRequestBuilder.build()
            redirectCount += 1
        }
        return null
    }

    private fun displayCompatibleImage(
        bytes: ByteArray,
        mimeType: String?,
    ): DisplayImageResource? {
        val detectedMime = mimeType ?: return null
        if (detectedMime != "image/avif") {
            return DisplayImageResource(bytes, detectedMime)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MaximumDisplayDimension) {
            sampleSize *= 2
        }
        val source =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null
        return try {
            ByteArrayOutputStream().use { output ->
                val hasAlpha = source.hasAlpha()
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!source.compress(format, DisplayJpegQuality, output)) return null
                DisplayImageResource(output.toByteArray(), if (hasAlpha) "image/png" else "image/jpeg")
            }
        } finally {
            source.recycle()
        }
    }

    private fun blurredImageResource(
        cacheKey: String,
        bytes: ByteArray,
        corsAllowOrigin: String?,
        corsAllowCredentials: Boolean,
        generation: Int,
    ): WebResourceResponse? {
        if (!isCurrentGeneration(generation)) return null
        val source = decodeSampledImage(bytes, MaximumBlurredDimension) ?: return null
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
                cacheAndCreateResource(
                    cacheKey,
                    blurredBytes,
                    "image/jpeg",
                    "blurred",
                    corsAllowOrigin,
                    corsAllowCredentials,
                    generation,
                )
            } finally {
                if (blurred !== tiny && blurred !== source) blurred.recycle()
                if (tiny !== source) tiny.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun dagCalibrationThumbnail(bytes: ByteArray): ByteArray? {
        val source = decodeSampledImage(bytes, CalibrationThumbnailDimension) ?: return null
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

    private fun decodeSampledImage(
        bytes: ByteArray,
        maximumDimension: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MaximumSourceImageDimension) return null
        if (bounds.outHeight !in 1..MaximumSourceImageDimension) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maximumDimension) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun rememberManualCalibrationCandidate(
        imageUrl: String,
        thumbnail: ByteArray,
        classification: DagImageClassification,
        generation: Int,
    ) {
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return
            manualCalibrationCandidates[normalizeImageUrl(imageUrl)] =
                DagManualCalibrationCandidate(thumbnail, classification)
            while (manualCalibrationCandidates.size > MaximumManualCalibrationCandidates) {
                manualCalibrationCandidates.remove(manualCalibrationCandidates.entries.first().key)
            }
        }
    }

    private fun cachedResource(
        key: String,
        generation: Int,
    ): WebResourceResponse? =
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return@synchronized null
            responseCache[normalizeImageUrl(key)]?.let {
                imageResource(
                    it.bytes,
                    it.mimeType,
                    it.decision,
                    it.corsAllowOrigin,
                    it.corsAllowCredentials,
                )
            }
        }

    private fun cacheAndCreateResource(
        key: String,
        bytes: ByteArray,
        mimeType: String,
        decision: String? = null,
        corsAllowOrigin: String? = null,
        corsAllowCredentials: Boolean = false,
        generation: Int,
    ): WebResourceResponse {
        if (!isCurrentGeneration(generation)) return unavailableImageResource()
        val normalizedKey = normalizeImageUrl(key)
        val cached =
            CachedImageResource(
                bytes,
                mimeType,
                decision,
                corsAllowOrigin,
                corsAllowCredentials,
            )
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return unavailableImageResource()
            if (bytes.size <= MaximumCachedImageBytes) {
                responseCache.put(normalizedKey, cached)?.let { responseCacheBytes -= it.bytes.size }
                responseCacheBytes += bytes.size
                while (responseCacheBytes > MaximumResponseCacheBytes || responseCache.size > MaximumCachedImages) {
                    val eldest = responseCache.entries.iterator().next()
                    responseCacheBytes -= eldest.value.bytes.size
                    responseCache.remove(eldest.key)
                }
            }
        }
        return imageResource(
            cached.bytes,
            cached.mimeType,
            cached.decision,
            cached.corsAllowOrigin,
            cached.corsAllowCredentials,
        )
    }

    private fun imageResource(
        bytes: ByteArray,
        mimeType: String,
        decision: String? = null,
        corsAllowOrigin: String? = null,
        corsAllowCredentials: Boolean = false,
    ): WebResourceResponse =
        WebResourceResponse(
            mimeType,
            null,
            200,
            "OK",
            buildMap {
                put("Cache-Control", "no-store")
                put("Content-Length", bytes.size.toString())
                put("X-Content-Type-Options", "nosniff")
                corsAllowOrigin?.let { put("Access-Control-Allow-Origin", it) }
                if (corsAllowCredentials) put("Access-Control-Allow-Credentials", "true")
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

    private inline fun <T> withClassifier(
        generation: Int,
        block: (DagImageClassifier) -> T,
    ): T {
        val classifier =
            try {
                var available: DagImageClassifier? = null
                while (available == null && isCurrentGeneration(generation)) {
                    available = classifierPool.poll(ResourceWaitMillis, TimeUnit.MILLISECONDS)
                }
                available ?: throw IOException("Obsolete DAG image classification")
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("DAG image classification interrupted", error)
            }
        if (!isCurrentGeneration(generation)) {
            check(classifierPool.offer(classifier)) { "DAG classifier pool overflow" }
            throw IOException("Obsolete DAG image classification")
        }
        return try {
            block(classifier)
        } finally {
            check(classifierPool.offer(classifier)) { "DAG classifier pool overflow" }
        }
    }

    private fun isCurrentGeneration(generation: Int): Boolean = !closed.get() && pageGeneration.get() == generation

    private fun admitImageUrl(
        imageUrl: String,
        isSynthetic: Boolean,
        generation: Int,
    ): Boolean =
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return@synchronized false
            isSynthetic || normalizeImageUrl(imageUrl) in prioritizedImageUrls
        }

    private fun reserveImage(generation: Int): Boolean =
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return@synchronized false
            imageCount.incrementAndGet() <= MaximumImagesPerPage
        }

    private fun incrementIfCurrent(
        generation: Int,
        counter: AtomicInteger,
    ): Boolean =
        synchronized(pageStateLock) {
            if (!isCurrentGeneration(generation)) return@synchronized false
            counter.incrementAndGet()
            true
        }

    private companion object {
        const val MaximumImagesPerPage = DagImageDeliveryPolicy.MaximumPrioritizedImageUrls
        const val MaximumImageRedirects = 5
        const val RequestTimeoutSeconds = 15L
        const val ResourceWaitMillis = 100L
        const val InitialBufferBytes = 64 * 1024
        const val ReadBufferBytes = 16 * 1024
        const val MaximumBlurredDimension = 480
        const val BlurSampleSide = 4
        const val BlurJpegQuality = 72
        const val DisplayJpegQuality = 88
        const val MaximumDisplayDimension = 2_048
        const val MaximumSourceImageDimension = 16_384
        const val CalibrationThumbnailDimension = 512
        const val CalibrationThumbnailQuality = 65
        const val CalibrationThumbnailMaximumBytes = 128 * 1024
        const val MaximumCachedImages = 64
        const val MaximumCachedImageBytes = 1024 * 1024
        const val MaximumResponseCacheBytes = 16 * 1024 * 1024
        const val MaximumManualCalibrationCandidates = 160
        const val DisplayImageAcceptHeader = "image/webp,image/png,image/jpeg,image/svg+xml;q=0.9,*/*;q=0.5"
        val ImageRedirectStatusCodes = setOf(301, 302, 303, 307, 308)
    }
}

private data class TrackedImageResponse(
    val call: Call,
    val response: Response,
)

private data class DisplayImageResource(
    val bytes: ByteArray,
    val mimeType: String,
)

internal fun shouldCreateCalibrationThumbnail(
    classification: DagImageClassification,
    calibrationRevealEnabled: Boolean,
): Boolean =
    classification.scores.isNotEmpty() &&
        (classification.decision == DagImageDecision.Uncertain || calibrationRevealEnabled)

internal data class DagManualCalibrationCandidate(
    val thumbnail: ByteArray,
    val classification: DagImageClassification,
)

internal fun normalizeImageUrl(value: String): String = value.substringBefore('#')

internal fun dagAddBoundedPrioritizedImageUrls(
    target: LinkedHashSet<String>,
    imageUrls: Collection<String>,
    maximumSize: Int,
) {
    require(maximumSize > 0) { "DAG image priority limit must be positive" }
    imageUrls.forEach { candidate ->
        if (candidate.length > DagMaximumOriginalImageUrlLength) return@forEach
        val normalized = normalizeImageUrl(candidate)
        if (!normalized.startsWith("https://", ignoreCase = true)) return@forEach
        target.remove(normalized)
        target.add(normalized)
        while (target.size > maximumSize) {
            target.remove(target.first())
        }
    }
}

internal fun dagShouldForwardImageCookie(
    imageUrl: String,
    pageUrl: String?,
): Boolean = pageUrl != null && dagSameOrigin(imageUrl, pageUrl)

internal fun dagImageCookieHeader(
    imageUrl: String,
    pageUrl: String?,
    cookie: String?,
): String? =
    cookie
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { dagShouldForwardImageCookie(imageUrl, pageUrl) }

internal fun dagForwardedImageRequestHeaders(
    requestHeaders: Map<String, String>,
    imageUrl: String,
    pageUrl: String?,
    storedCookie: String?,
): Map<String, String> =
    buildMap {
        DagForwardedImageRequestHeaderNames.forEach { name ->
            requestHeaders.headerValue(name).takeIf(String::isNotBlank)?.let { put(name, it) }
        }
        val cookie =
            requestHeaders
                .headerValue("Cookie")
                .takeIf(String::isNotBlank)
                ?: storedCookie
        dagImageCookieHeader(imageUrl, pageUrl, cookie)?.let { put("Cookie", it) }
    }

internal fun dagOriginalImageUrl(requestUrl: String): String? =
    runCatching {
        val requestUri = java.net.URI(requestUrl)
        if (requestUri.scheme != "https" || requestUri.host.isNullOrBlank()) return@runCatching null
        val path = requestUri.path.orEmpty()
        if (!path.startsWith(DagSyntheticImagePathPrefix)) return@runCatching null
        val encoded =
            path
                .removePrefix(DagSyntheticImagePathPrefix)
                .substringBefore('/')
                .removeSuffix(DagSyntheticImagePathSuffix)
                .takeIf { it.length in 8..DagMaximumEncodedImageUrlLength }
                ?: return@runCatching null
        val original =
            String(
                java.util.Base64.getUrlDecoder().decode(encoded),
                Charsets.UTF_8,
            )
        original.takeIf {
            it.length <= DagMaximumOriginalImageUrlLength &&
                dagSameOrigin(requestUrl, original)
        }
    }.getOrNull()

internal fun dagSameOrigin(
    firstUrl: String,
    secondUrl: String,
): Boolean =
    runCatching {
        val first = java.net.URI(firstUrl)
        val second = java.net.URI(secondUrl)
        first.scheme.equals("https", ignoreCase = true) &&
            second.scheme.equals("https", ignoreCase = true) &&
            !first.host.isNullOrBlank() &&
            first.host.equals(second.host, ignoreCase = true) &&
            (if (first.port == -1) 443 else first.port) == (if (second.port == -1) 443 else second.port) &&
            first.userInfo == null &&
            second.userInfo == null
    }.getOrDefault(false)

internal fun dagSafeCorsAllowOrigin(value: String?): String? {
    val candidate = value?.trim().orEmpty()
    if (candidate == "*") return candidate
    return runCatching {
        val origin = java.net.URI(candidate)
        candidate.takeIf {
            origin.scheme == "https" &&
                !origin.host.isNullOrBlank() &&
                origin.userInfo == null &&
                origin.query == null &&
                origin.fragment == null &&
                origin.path.isNullOrBlank() &&
                (origin.port == -1 || origin.port in 1..65_535)
        }
    }.getOrNull()
}

internal fun dagSafeCorsAllowCredentials(
    value: String?,
    allowOrigin: String?,
    requestOrigin: String?,
): Boolean =
    value?.trim() == "true" &&
        allowOrigin != null &&
        allowOrigin != "*" &&
        requestOrigin != null &&
        allowOrigin == requestOrigin.trim()

private data class CachedImageResource(
    val bytes: ByteArray,
    val mimeType: String,
    val decision: String?,
    val corsAllowOrigin: String?,
    val corsAllowCredentials: Boolean,
)

private const val DagSyntheticImagePathPrefix = "/.dag-safe-image/"
private const val DagSyntheticImagePathSuffix = ".png"
private const val DagMaximumEncodedImageUrlLength = 8_192
private const val DagMaximumOriginalImageUrlLength = 4_096
private val DagForwardedImageRequestHeaderNames =
    setOf(
        "Accept-Language",
        "Origin",
        "Referer",
        "User-Agent",
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
    // Keep downloads and decoded buffers bounded on lower-memory phones while
    // still feeding both classifier workers.
    const val MaximumConcurrentImages = 4

    // Two independent model stacks keep visible photos moving without letting
    // inference contend across all WebView resource threads or exhaust memory.
    const val MaximumConcurrentClassifications = 2

    // The DOM bridge is untrusted input. One page cannot retain more image
    // priorities than the native loader can ever classify for that generation.
    const val MaximumPrioritizedImageUrls = 400
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
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=",
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
