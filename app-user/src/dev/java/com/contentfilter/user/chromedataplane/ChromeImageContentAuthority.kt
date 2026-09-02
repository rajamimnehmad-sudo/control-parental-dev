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

private enum class DeclaredMimeDisposition {
    DeclaredImage,
    InvalidOrMultipart,
    AmbiguousSniffable,
    DefiniteNonImage,
}

private sealed interface ProgressiveSniffDecision {
    data class Match(
        val format: ChromeImageFormat,
    ) : ProgressiveSniffDecision

    data object NeedMore : ProgressiveSniffDecision

    data object NoMatch : ProgressiveSniffDecision
}

/** Establishes image authority before any candidate body can be delivered to Chrome. */
internal class ChromeImageContentAuthority(
    maximumConcurrentBodies: Int = DefaultMaximumConcurrentBodies,
    private val maximumSniffBytes: Int = DefaultMaximumSniffBytes,
    private val stockMediaAuthority: Boolean = false,
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
        if (!request.isImageIntent(stockMediaAuthority)) return request
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
        val requestIntent = request.isImageIntent(stockMediaAuthority)
        val declaredMimeTypes = response.headers.declaredContentTypes()
        val mimeDisposition = declaredMimeTypes.disposition(stockMediaAuthority)
        if (
            requestIntent ||
            mimeDisposition == DeclaredMimeDisposition.DeclaredImage ||
            mimeDisposition == DeclaredMimeDisposition.InvalidOrMultipart
        ) {
            candidates.incrementAndGet()
            return ChromeImageContentInspection.Candidate(
                response = response,
                requestIntent = requestIntent,
                declaredMimeTypes = declaredMimeTypes,
                prefixFormat = null,
            )
        }
        if (!responseMayHaveBody(request.method, response.statusCode)) {
            return ChromeImageContentInspection.Passthrough(response)
        }
        if (mimeDisposition == DeclaredMimeDisposition.DefiniteNonImage) {
            return ChromeImageContentInspection.Passthrough(response)
        }
        if (!response.headers.hasIdentityContentEncoding()) {
            return if (mimeDisposition == DeclaredMimeDisposition.DefiniteNonImage) {
                ChromeImageContentInspection.Passthrough(response)
            } else {
                candidates.incrementAndGet()
                ChromeImageContentInspection.Candidate(
                    response = response,
                    requestIntent = false,
                    declaredMimeTypes = declaredMimeTypes,
                    prefixFormat = null,
                )
            }
        }

        val peek = response.body.peekImagePrefix(maximumSniffBytes)
        prefixPeeks.incrementAndGet()
        val replayResponse =
            response.copy(
                body = SequenceInputStream(ByteArrayInputStream(peek.bytes), response.body),
            )
        val format = peek.format
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
        val requestIntent = request.isImageIntent(stockMediaAuthority)
        val declaredMimeTypes = response.headers.declaredContentTypes()
        val mimeDisposition = declaredMimeTypes.disposition(stockMediaAuthority)
        val encodedAmbiguous =
            mimeDisposition == DeclaredMimeDisposition.AmbiguousSniffable &&
                !response.headers.hasIdentityContentEncoding()
        val format =
            if (encodedAmbiguous || mimeDisposition == DeclaredMimeDisposition.DefiniteNonImage) {
                null
            } else {
                sniffFormat(bytes)
            }
        return if (
            requestIntent ||
            mimeDisposition == DeclaredMimeDisposition.DeclaredImage ||
            mimeDisposition == DeclaredMimeDisposition.InvalidOrMultipart ||
            encodedAmbiguous ||
            format != null
        ) {
            candidates.incrementAndGet()
            if (!requestIntent && mimeDisposition != DeclaredMimeDisposition.DeclaredImage && format != null) {
                magicCandidates.incrementAndGet()
            }
            ChromeImageContentInspection.Candidate(
                response = response.copy(body = bytes.inputStream()),
                requestIntent = requestIntent,
                declaredMimeTypes = declaredMimeTypes,
                prefixFormat = format,
            )
        } else {
            ChromeImageContentInspection.Passthrough(
                response.copy(
                    headers = response.headers,
                    body = bytes.inputStream(),
                ),
            )
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
        if (format == ChromeImageFormat.Avif && !hasCompleteAvifBrandEvidence(bytes)) {
            return ChromeImageContentResolution.Reject(AvifBrandEvidenceReason)
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
        try {
            bodyPermits.acquire()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
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
        sniffBinaryFormat(bytes)?.let { return it }
        if (looksLikeSvg(bytes)) return ChromeImageFormat.Svg
        return null
    }

    private fun sniffBinaryFormat(bytes: ByteArray): ChromeImageFormat? {
        if (bytes.startsWith(JpegSignature)) return ChromeImageFormat.Jpeg
        if (bytes.startsWith(PngSignature)) return ChromeImageFormat.Png
        if (bytes.size >= WebpHeaderBytes && bytes.matchesAscii(0, "RIFF") && bytes.matchesAscii(8, "WEBP")) {
            return ChromeImageFormat.Webp
        }
        if (bytes.matchesAscii(0, "GIF87a") || bytes.matchesAscii(0, "GIF89a")) return ChromeImageFormat.Gif
        if (bytes.startsWith(BmpSignature)) return ChromeImageFormat.Bmp
        if (bytes.startsWith(IcoSignature)) return ChromeImageFormat.Ico
        sniffIsoBmff(bytes)?.let { return it }
        return null
    }

    private fun InputStream.peekImagePrefix(maximumBytes: Int): PrefixPeek {
        val output = ByteArrayOutputStream(minOf(maximumBytes, ProgressiveInitialCapacity))
        while (output.size() < maximumBytes) {
            when (val decision = progressiveSniff(output.toByteArray(), endOfInput = false, maximumBytes)) {
                is ProgressiveSniffDecision.Match -> return PrefixPeek(output.toByteArray(), decision.format)
                ProgressiveSniffDecision.NoMatch -> return PrefixPeek(output.toByteArray(), null)
                ProgressiveSniffDecision.NeedMore -> Unit
            }
            val next = read()
            if (next < 0) {
                val bytes = output.toByteArray()
                val final = progressiveSniff(bytes, endOfInput = true, maximumBytes)
                return PrefixPeek(bytes, (final as? ProgressiveSniffDecision.Match)?.format)
            }
            output.write(next)
        }
        val bytes = output.toByteArray()
        val final = progressiveSniff(bytes, endOfInput = true, maximumBytes)
        return PrefixPeek(bytes, (final as? ProgressiveSniffDecision.Match)?.format)
    }

    private fun progressiveSniff(
        bytes: ByteArray,
        endOfInput: Boolean,
        maximumBytes: Int,
    ): ProgressiveSniffDecision {
        sniffBinaryFormat(bytes)?.let { return ProgressiveSniffDecision.Match(it) }
        val svgDecision = svgRootDecision(bytes, endOfInput)
        if (svgDecision is ProgressiveSniffDecision.Match) return svgDecision
        val binaryCouldMatch = binaryImageCouldMatch(bytes, maximumBytes)
        return if (!endOfInput && (binaryCouldMatch || svgDecision == ProgressiveSniffDecision.NeedMore)) {
            ProgressiveSniffDecision.NeedMore
        } else {
            ProgressiveSniffDecision.NoMatch
        }
    }

    private fun binaryImageCouldMatch(
        bytes: ByteArray,
        maximumBytes: Int,
    ): Boolean =
        JpegSignature.couldStartWith(bytes) ||
            PngSignature.couldStartWith(bytes) ||
            Gif87aSignature.couldStartWith(bytes) ||
            Gif89aSignature.couldStartWith(bytes) ||
            BmpSignature.couldStartWith(bytes) ||
            IcoSignature.couldStartWith(bytes) ||
            webpCouldMatch(bytes) ||
            isoBmffCouldMatch(bytes, maximumBytes)

    private fun webpCouldMatch(bytes: ByteArray): Boolean {
        if (bytes.size > WebpHeaderBytes) return false
        for (index in bytes.indices) {
            val expected =
                when (index) {
                    in 0..3 -> "RIFF"[index].code.toByte()
                    in 8..11 -> "WEBP"[index - 8].code.toByte()
                    else -> null
                }
            if (expected != null && bytes[index] != expected) return false
        }
        return true
    }

    private fun isoBmffCouldMatch(
        bytes: ByteArray,
        maximumBytes: Int,
    ): Boolean {
        for (index in 4 until minOf(bytes.size, 8)) {
            if (bytes[index] != "ftyp"[index - 4].code.toByte()) return false
        }
        if (bytes.size < 8) return true
        val declaredBoxSize = bytes.readUnsignedInt(0)
        if (declaredBoxSize < IsoBmffMinimumBytes) return false
        val scanEnd =
            declaredBoxSize
                .coerceAtMost(MaximumIsoBmffBrandBytes.toLong())
                .coerceAtMost(maximumBytes.toLong())
                .toInt()
        return bytes.size < scanEnd
    }

    private fun sniffIsoBmff(bytes: ByteArray): ChromeImageFormat? {
        if (bytes.size < IsoBmffMinimumBytes || !bytes.matchesAscii(4, "ftyp")) return null
        val declaredBoxSize = isoBmffBrandScanEnd(bytes)
        if (declaredBoxSize < IsoBmffMinimumBytes) return null
        var offset = IsoBmffBrandOffset
        var avifFound = false
        var heifFound = false
        while (offset + IsoBmffBrandBytes <= declaredBoxSize) {
            val brand = String(bytes, offset, IsoBmffBrandBytes, StandardCharsets.US_ASCII)
            when (brand) {
                in AvifBrands -> avifFound = true
                in HeifBrands -> heifFound = true
            }
            offset += IsoBmffBrandBytes
        }
        return when {
            avifFound -> ChromeImageFormat.Avif
            heifFound -> ChromeImageFormat.Heif
            else -> null
        }
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

    private fun hasCompleteAvifBrandEvidence(bytes: ByteArray): Boolean {
        if (bytes.size < IsoBmffMinimumBytes || !bytes.matchesAscii(4, "ftyp")) return false
        val declared = bytes.readUnsignedInt(0)
        return declared in IsoBmffMinimumBytes.toLong()..MaximumIsoBmffBrandBytes.toLong() &&
            declared <= bytes.size.toLong()
    }

    private fun isoBmffBrandScanEnd(bytes: ByteArray): Int =
        bytes.readUnsignedInt(0)
            .coerceAtMost(bytes.size.toLong())
            .coerceAtMost(MaximumIsoBmffBrandBytes.toLong())
            .toInt()

    private fun looksLikeSvg(bytes: ByteArray): Boolean =
        svgRootDecision(bytes.copyOfRange(0, minOf(bytes.size, SvgSniffBytes)), endOfInput = true) is
            ProgressiveSniffDecision.Match

    private fun svgRootDecision(
        bytes: ByteArray,
        endOfInput: Boolean,
    ): ProgressiveSniffDecision {
        val bomOffset = utf8BomOffset(bytes) ?: return if (endOfInput) ProgressiveSniffDecision.NoMatch else ProgressiveSniffDecision.NeedMore
        val text = bytes.copyOfRange(bomOffset, bytes.size).toString(StandardCharsets.UTF_8)
        var offset = text.skipXmlWhitespace(0)
        while (true) {
            if (offset >= text.length) return text.moreOrNoMatch(endOfInput)
            if (
                !endOfInput &&
                listOf(XmlDeclarationStart, XmlCommentStart, DoctypeStart).any { token ->
                    token.regionMatches(0, text, offset, text.length - offset, ignoreCase = true)
                }
            ) {
                return ProgressiveSniffDecision.NeedMore
            }
            when {
                text.regionMatches(offset, XmlDeclarationStart, 0, XmlDeclarationStart.length, ignoreCase = true) -> {
                    val end = text.indexOf("?>", offset + XmlDeclarationStart.length)
                    if (end < 0) return text.moreOrNoMatch(endOfInput)
                    offset = text.skipXmlWhitespace(end + 2)
                }
                text.startsWith(XmlCommentStart, offset) -> {
                    val end = text.indexOf(XmlCommentEnd, offset + XmlCommentStart.length)
                    if (end < 0) return text.moreOrNoMatch(endOfInput)
                    offset = text.skipXmlWhitespace(end + XmlCommentEnd.length)
                }
                text.regionMatches(offset, DoctypeStart, 0, DoctypeStart.length, ignoreCase = true) -> {
                    val end = text.findDoctypeEnd(offset + DoctypeStart.length)
                    if (end < 0) return text.moreOrNoMatch(endOfInput)
                    offset = text.skipXmlWhitespace(end + 1)
                }
                else -> break
            }
        }
        if (text[offset] != '<') return ProgressiveSniffDecision.NoMatch
        if (offset + 1 >= text.length) return text.moreOrNoMatch(endOfInput)
        if (text[offset + 1] in setOf('/', '!', '?')) return ProgressiveSniffDecision.NoMatch
        var nameEnd = offset + 1
        while (nameEnd < text.length && text[nameEnd].isXmlNameCharacter()) nameEnd++
        if (nameEnd == offset + 1) return ProgressiveSniffDecision.NoMatch
        if (nameEnd == text.length && !endOfInput) return ProgressiveSniffDecision.NeedMore
        val rootName = text.substring(offset + 1, nameEnd).substringAfterLast(':')
        return if (rootName.equals("svg", ignoreCase = true)) {
            ProgressiveSniffDecision.Match(ChromeImageFormat.Svg)
        } else {
            ProgressiveSniffDecision.NoMatch
        }
    }

    private companion object {
        const val DefaultMaximumConcurrentBodies = 2
        const val DefaultMaximumSniffBytes = 512
        const val MinimumSniffBytes = 32
        const val ProgressiveInitialCapacity = 16
        const val WebpHeaderBytes = 12
        const val WebpFlagsOffset = 20
        const val IsoBmffMinimumBytes = 16
        const val IsoBmffBrandOffset = 8
        const val IsoBmffBrandBytes = 4
        const val MaximumIsoBmffBrandBytes = 512
        const val SvgSniffBytes = 512
        const val XmlDeclarationStart = "<?xml"
        const val XmlCommentStart = "<!--"
        const val XmlCommentEnd = "-->"
        const val DoctypeStart = "<!doctype"
        const val UnknownFormatReason = "image_format_unknown"
        const val FormatChangedAfterPeekReason = "image_format_changed_after_peek"
        const val AnimatedImageReason = "animated_image"
        const val AvifBrandEvidenceReason = "unsupported_avif_brand_evidence"
        val ImageRequestHeadersRemoved =
            setOf("accept-encoding", "range", "if-range", "if-none-match", "if-modified-since")
        val AvifBrands = setOf("avif", "avis")
        val HeifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
        val JpegSignature = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val Gif87aSignature = "GIF87a".toByteArray(StandardCharsets.US_ASCII)
        val Gif89aSignature = "GIF89a".toByteArray(StandardCharsets.US_ASCII)
        val BmpSignature = byteArrayOf('B'.code.toByte(), 'M'.code.toByte())
        val IcoSignature = byteArrayOf(0, 0, 1, 0)
    }
}

private data class PrefixPeek(
    val bytes: ByteArray,
    val format: ChromeImageFormat?,
)

private fun ChromePhotosProxyRequest.isImageIntent(stockMediaAuthority: Boolean): Boolean =
    headerValues("Sec-Fetch-Dest").any { value ->
        val destination = value.trim().lowercase(Locale.US)
        destination == "image" || stockMediaAuthority && destination in H19ProtectedVisualDestinations
    }

private fun List<ChromeHttpHeader>.declaredContentTypes(): List<String> =
    filter { it.name.equals("Content-Type", ignoreCase = true) }
        .map { header -> header.value.normalizedImageMimeType() }

private fun List<String>.disposition(stockMediaAuthority: Boolean): DeclaredMimeDisposition {
    if (
        stockMediaAuthority &&
        (size > 1 || any { !it.isSyntacticallyValidMimeType() || it.startsWith("multipart/") })
    ) {
        return DeclaredMimeDisposition.InvalidOrMultipart
    }
    if (any { it.isDeclaredImageMimeType(stockMediaAuthority) }) return DeclaredMimeDisposition.DeclaredImage
    if (isEmpty() || any(String::isAmbiguousSniffableMimeType)) return DeclaredMimeDisposition.AmbiguousSniffable
    return DeclaredMimeDisposition.DefiniteNonImage
}

private fun String.isSyntacticallyValidMimeType(): Boolean {
    if (isEmpty() || ',' in this || count { it == '/' } != 1) return false
    val type = substringBefore('/')
    val subtype = substringAfter('/')
    return type.isNotEmpty() && subtype.isNotEmpty() &&
        type.all(::isMimeTokenCharacter) && subtype.all(::isMimeTokenCharacter)
}

private fun isMimeTokenCharacter(character: Char): Boolean = character.isLetterOrDigit() || character in "!#$&^_.+-"

internal fun List<ChromeHttpHeader>.hasIdentityContentEncoding(): Boolean {
    val values = filter { it.name.equals("Content-Encoding", ignoreCase = true) }.map(ChromeHttpHeader::value)
    if (values.isEmpty()) return true
    val codings = values.flatMap { value -> value.split(',') }.map { it.trim() }.filter(String::isNotEmpty)
    return codings.isNotEmpty() && codings.all { it.equals("identity", ignoreCase = true) }
}

private fun String.isDeclaredImageMimeType(stockMediaAuthority: Boolean): Boolean =
    startsWith("image/") ||
        stockMediaAuthority && (startsWith("video/") || this in H19ProtectedVisualContainerMimeTypes)

private val H19ProtectedVisualDestinations = setOf("video", "object", "embed")
private val H19ProtectedVisualContainerMimeTypes =
    setOf(
        "application/pdf",
        "application/signed-exchange",
        "application/webbundle",
        "application/webbundle;v=b1",
        "multipart/x-mixed-replace",
    )

private fun String.isAmbiguousSniffableMimeType(): Boolean =
    this == "text/plain" ||
        this == "application/octet-stream" ||
        this == "binary/octet-stream" ||
        this == "application/unknown" ||
        this == "application/x-unknown" ||
        this == "unknown/unknown"

private fun ByteArray.couldStartWith(prefix: ByteArray): Boolean =
    prefix.size <= size && prefix.indices.all { index -> this[index] == prefix[index] }

private fun ByteArray.startsWith(signature: ByteArray): Boolean =
    size >= signature.size && signature.indices.all { index -> this[index] == signature[index] }

private fun ByteArray.matchesAscii(
    offset: Int,
    value: String,
): Boolean =
    offset >= 0 &&
        offset + value.length <= size &&
        value.indices.all { index ->
            this[offset + index] == value[index].code.toByte()
        }

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

private fun utf8BomOffset(bytes: ByteArray): Int? {
    if (bytes.isEmpty() || bytes[0] != 0xef.toByte()) return 0
    if (bytes.size < 3) return null
    return if (bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) 3 else 0
}

private fun String.skipXmlWhitespace(start: Int): Int {
    var offset = start
    while (offset < length && this[offset] in setOf(' ', '\t', '\r', '\n')) offset++
    return offset
}

private fun String.moreOrNoMatch(endOfInput: Boolean): ProgressiveSniffDecision =
    if (endOfInput) ProgressiveSniffDecision.NoMatch else ProgressiveSniffDecision.NeedMore

private fun Char.isXmlNameCharacter(): Boolean = isLetterOrDigit() || this in setOf('_', '-', '.', ':')

private fun String.findDoctypeEnd(start: Int): Int {
    var quote: Char? = null
    var subsetDepth = 0
    for (index in start until length) {
        val character = this[index]
        if (quote != null) {
            if (character == quote) quote = null
        } else {
            when (character) {
                '\'', '"' -> quote = character
                '[' -> subsetDepth++
                ']' -> if (subsetDepth > 0) subsetDepth--
                '>' -> if (subsetDepth == 0) return index
            }
        }
    }
    return -1
}
