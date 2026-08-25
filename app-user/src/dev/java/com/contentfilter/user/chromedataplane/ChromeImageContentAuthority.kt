package com.contentfilter.user.chromedataplane

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal enum class ChromeImageFormat(
    val canonicalMimeType: String,
    val supportedStaticRaster: Boolean,
) {
    Jpeg("image/jpeg", true),
    Png("image/png", true),
    Webp("image/webp", true),
    Avif("image/avif", true),
    Gif("image/gif", false),
    Bmp("image/bmp", false),
    Ico("image/x-icon", false),
    Heif("image/heif", false),
    Svg("image/svg+xml", false),
}

internal data class ChromeImageAuthorityMetrics(
    val candidates: Long = 0,
    val prefixPeeks: Long = 0,
    val magicCandidates: Long = 0,
    val bodyAdmissionPeak: Int = 0,
    val bodyAdmissionRejects: Long = 0,
)

internal sealed interface ChromeImageContentInspection {
    val response: ChromePhotosUpstreamResponse

    data class Candidate(
        override val response: ChromePhotosUpstreamResponse,
        val requestIntent: Boolean,
        val declaredMimeTypes: List<String>,
        val prefixFormat: ChromeImageFormat?,
    ) : ChromeImageContentInspection

    data class Passthrough(
        override val response: ChromePhotosUpstreamResponse,
    ) : ChromeImageContentInspection
}

internal sealed interface ChromeImageContentResolution {
    data class Inspect(
        val format: ChromeImageFormat,
    ) : ChromeImageContentResolution

    data class Reject(
        val reason: String,
    ) : ChromeImageContentResolution
}

/** Establishes image authority before any candidate body can be delivered to Chrome. */
internal class ChromeImageContentAuthority(
    maximumConcurrentBodies: Int = DefaultMaximumConcurrentBodies,
    private val maximumSniffBytes: Int = DefaultMaximumSniffBytes,
) {
    private val bodyPermits = Semaphore(maximumConcurrentBodies, true)
    private val activeBodies = AtomicInteger()
    private val bodyAdmissionPeak = AtomicInteger()
    private val candidates = AtomicLong()
    private val prefixPeeks = AtomicLong()
    private val magicCandidates = AtomicLong()
    private val bodyAdmissionRejects = AtomicLong()

    init {
        require(maximumConcurrentBodies > 0)
        require(maximumSniffBytes >= MinimumSniffBytes)
    }

    fun normalizeUpstreamRequest(request: ChromePhotosProxyRequest): ChromePhotosProxyRequest {
        if (!request.isImageIntent()) return request
        val headers =
            request.headers.filterNot { header ->
                header.name.lowercase(Locale.US) in ImageRequestHeadersRemoved
            } + ChromeHttpHeader("Accept-Encoding", "identity")
        return request.copy(headers = headers)
    }

    fun inspect(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromeImageContentInspection {
        val requestIntent = request.isImageIntent()
        val declaredMimeTypes = response.headers.declaredContentTypes()
        if (requestIntent || declaredMimeTypes.any(String::isDeclaredImageMimeType)) {
            candidates.incrementAndGet()
            return ChromeImageContentInspection.Candidate(
                response = response,
                requestIntent = requestIntent,
                declaredMimeTypes = declaredMimeTypes,
                prefixFormat = null,
            )
        }
        if (!response.headers.hasIdentityContentEncoding() || !responseMayHaveBody(request.method, response.statusCode)) {
            return ChromeImageContentInspection.Passthrough(response)
        }

        val peek = response.body.peekPrefix(maximumSniffBytes)
        prefixPeeks.incrementAndGet()
        val replayResponse =
            response.copy(
                body = SequenceInputStream(ByteArrayInputStream(peek.bytes), response.body),
            )
        val format = sniffFormat(peek.bytes)
        return if (format == null) {
            ChromeImageContentInspection.Passthrough(replayResponse)
        } else {
            candidates.incrementAndGet()
            magicCandidates.incrementAndGet()
            ChromeImageContentInspection.Candidate(
                response = replayResponse,
                requestIntent = false,
                declaredMimeTypes = declaredMimeTypes,
                prefixFormat = format,
            )
        }
    }

    fun inspectBuffered(
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
        bytes: ByteArray,
    ): ChromeImageContentInspection {
        val requestIntent = request.isImageIntent()
        val declaredMimeTypes = response.headers.declaredContentTypes()
        val format = sniffFormat(bytes)
        return if (requestIntent || declaredMimeTypes.any(String::isDeclaredImageMimeType) || format != null) {
            candidates.incrementAndGet()
            if (!requestIntent && declaredMimeTypes.none(String::isDeclaredImageMimeType) && format != null) {
                magicCandidates.incrementAndGet()
            }
            ChromeImageContentInspection.Candidate(
                response = response.copy(body = bytes.inputStream()),
                requestIntent = requestIntent,
                declaredMimeTypes = declaredMimeTypes,
                prefixFormat = format,
            )
        } else {
            ChromeImageContentInspection.Passthrough(response.copy(body = bytes.inputStream()))
        }
    }

    fun resolve(
        candidate: ChromeImageContentInspection.Candidate,
        bytes: ByteArray,
    ): ChromeImageContentResolution {
        val format = sniffFormat(bytes) ?: return ChromeImageContentResolution.Reject(UnknownFormatReason)
        if (candidate.prefixFormat != null && candidate.prefixFormat != format) {
            return ChromeImageContentResolution.Reject(FormatChangedAfterPeekReason)
        }
        if (!format.supportedStaticRaster) {
            return ChromeImageContentResolution.Reject("unsupported_${format.name.lowercase(Locale.US)}")
        }
        if (isAnimated(format, bytes)) {
            return ChromeImageContentResolution.Reject(AnimatedImageReason)
        }
        return ChromeImageContentResolution.Inspect(format)
    }

    fun <T> withBodyAdmission(
        onRejected: () -> T,
        block: () -> T,
    ): T {
        if (!bodyPermits.tryAcquire()) {
            bodyAdmissionRejects.incrementAndGet()
            return onRejected()
        }
        val active = activeBodies.incrementAndGet()
        bodyAdmissionPeak.accumulateAndGet(active, ::maxOf)
        return try {
            block()
        } finally {
            activeBodies.decrementAndGet()
            bodyPermits.release()
        }
    }

    fun metrics(): ChromeImageAuthorityMetrics =
        ChromeImageAuthorityMetrics(
            candidates = candidates.get(),
            prefixPeeks = prefixPeeks.get(),
            magicCandidates = magicCandidates.get(),
            bodyAdmissionPeak = bodyAdmissionPeak.get(),
            bodyAdmissionRejects = bodyAdmissionRejects.get(),
        )

    internal fun sniffFormat(bytes: ByteArray): ChromeImageFormat? {
        if (bytes.startsWith(JpegSignature)) return ChromeImageFormat.Jpeg
        if (bytes.startsWith(PngSignature)) return ChromeImageFormat.Png
        if (bytes.size >= WebpHeaderBytes && bytes.matchesAscii(0, "RIFF") && bytes.matchesAscii(8, "WEBP")) {
            return ChromeImageFormat.Webp
        }
        if (bytes.matchesAscii(0, "GIF87a") || bytes.matchesAscii(0, "GIF89a")) return ChromeImageFormat.Gif
        if (bytes.startsWith(BmpSignature)) return ChromeImageFormat.Bmp
        if (bytes.startsWith(IcoSignature)) return ChromeImageFormat.Ico
        sniffIsoBmff(bytes)?.let { return it }
        if (looksLikeSvg(bytes)) return ChromeImageFormat.Svg
        return null
    }

    private fun sniffIsoBmff(bytes: ByteArray): ChromeImageFormat? {
        if (bytes.size < IsoBmffMinimumBytes || !bytes.matchesAscii(4, "ftyp")) return null
        val declaredBoxSize = isoBmffBrandScanEnd(bytes)
        if (declaredBoxSize < IsoBmffMinimumBytes) return null
        var offset = IsoBmffBrandOffset
        while (offset + IsoBmffBrandBytes <= declaredBoxSize) {
            val brand = String(bytes, offset, IsoBmffBrandBytes, StandardCharsets.US_ASCII)
            if (brand in AvifBrands) return ChromeImageFormat.Avif
            if (brand in HeifBrands) return ChromeImageFormat.Heif
            offset += IsoBmffBrandBytes
        }
        return null
    }

    private fun isAnimated(
        format: ChromeImageFormat,
        bytes: ByteArray,
    ): Boolean =
        when (format) {
            ChromeImageFormat.Png -> bytes.indexOfAscii("acTL") >= 0
            ChromeImageFormat.Webp ->
                bytes.indexOfAscii("ANIM") >= 0 ||
                    bytes.indexOfAscii("ANMF") >= 0 ||
                    (bytes.matchesAscii(12, "VP8X") && bytes.size > WebpFlagsOffset && bytes[WebpFlagsOffset].toInt() and 0x02 != 0)
            ChromeImageFormat.Avif -> hasIsoBmffBrand(bytes, "avis")
            else -> false
        }

    private fun hasIsoBmffBrand(
        bytes: ByteArray,
        expected: String,
    ): Boolean {
        if (bytes.size < IsoBmffMinimumBytes || !bytes.matchesAscii(4, "ftyp")) return false
        val end = isoBmffBrandScanEnd(bytes)
        var offset = IsoBmffBrandOffset
        while (offset + IsoBmffBrandBytes <= end) {
            if (bytes.matchesAscii(offset, expected)) return true
            offset += IsoBmffBrandBytes
        }
        return false
    }

    private fun isoBmffBrandScanEnd(bytes: ByteArray): Int =
        bytes.readUnsignedInt(0)
            .coerceAtMost(bytes.size.toLong())
            .coerceAtMost(MaximumIsoBmffBrandBytes.toLong())
            .toInt()

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val prefix =
            bytes.copyOfRange(0, minOf(bytes.size, SvgSniffBytes))
                .toString(StandardCharsets.UTF_8)
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                .lowercase(Locale.US)
        return prefix.startsWith("<svg") || (prefix.startsWith("<?xml") && "<svg" in prefix)
    }

    private companion object {
        const val DefaultMaximumConcurrentBodies = 2
        const val DefaultMaximumSniffBytes = 512
        const val MinimumSniffBytes = 32
        const val WebpHeaderBytes = 12
        const val WebpFlagsOffset = 20
        const val IsoBmffMinimumBytes = 16
        const val IsoBmffBrandOffset = 8
        const val IsoBmffBrandBytes = 4
        const val MaximumIsoBmffBrandBytes = 512
        const val SvgSniffBytes = 512
        const val UnknownFormatReason = "image_format_unknown"
        const val FormatChangedAfterPeekReason = "image_format_changed_after_peek"
        const val AnimatedImageReason = "animated_image"
        val ImageRequestHeadersRemoved =
            setOf("accept-encoding", "range", "if-range", "if-none-match", "if-modified-since")
        val AvifBrands = setOf("avif", "avis")
        val HeifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
        val JpegSignature = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val BmpSignature = byteArrayOf('B'.code.toByte(), 'M'.code.toByte())
        val IcoSignature = byteArrayOf(0, 0, 1, 0)
    }
}

private data class PrefixPeek(
    val bytes: ByteArray,
)

private fun ChromePhotosProxyRequest.isImageIntent(): Boolean =
    headerValues("Sec-Fetch-Dest").any { value -> value.trim().equals("image", ignoreCase = true) }

private fun List<ChromeHttpHeader>.declaredContentTypes(): List<String> =
    filter { it.name.equals("Content-Type", ignoreCase = true) }
        .map { header -> header.value.normalizedImageMimeType() }
        .filter(String::isNotEmpty)

internal fun List<ChromeHttpHeader>.hasIdentityContentEncoding(): Boolean {
    val values = filter { it.name.equals("Content-Encoding", ignoreCase = true) }.map(ChromeHttpHeader::value)
    if (values.isEmpty()) return true
    val codings = values.flatMap { value -> value.split(',') }.map { it.trim() }.filter(String::isNotEmpty)
    return codings.isNotEmpty() && codings.all { it.equals("identity", ignoreCase = true) }
}

private fun String.isDeclaredImageMimeType(): Boolean = startsWith("image/")

private fun InputStream.peekPrefix(maximumBytes: Int): PrefixPeek {
    val output = ByteArrayOutputStream(maximumBytes)
    val buffer = ByteArray(minOf(maximumBytes, 256))
    var zeroReads = 0
    while (output.size() < maximumBytes) {
        val count = read(buffer, 0, minOf(buffer.size, maximumBytes - output.size()))
        if (count < 0) break
        if (count == 0) {
            zeroReads++
            if (zeroReads >= MaximumZeroReads) break
            continue
        }
        zeroReads = 0
        output.write(buffer, 0, count)
    }
    return PrefixPeek(output.toByteArray())
}

private fun ByteArray.startsWith(signature: ByteArray): Boolean =
    size >= signature.size && signature.indices.all { index -> this[index] == signature[index] }

private fun ByteArray.matchesAscii(
    offset: Int,
    value: String,
): Boolean =
    offset >= 0 && offset + value.length <= size && value.indices.all { index -> this[offset + index] == value[index].code.toByte() }

private fun ByteArray.indexOfAscii(value: String): Int {
    if (value.isEmpty() || value.length > size) return -1
    for (offset in 0..size - value.length) {
        if (matchesAscii(offset, value)) return offset
    }
    return -1
}

private fun ByteArray.readUnsignedInt(offset: Int): Long =
    ((this[offset].toLong() and 0xff) shl 24) or
        ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or
        (this[offset + 3].toLong() and 0xff)

private const val MaximumZeroReads = 3
