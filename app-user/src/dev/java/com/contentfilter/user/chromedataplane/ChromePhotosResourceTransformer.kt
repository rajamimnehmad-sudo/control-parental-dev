package com.contentfilter.user.chromedataplane

import java.security.MessageDigest
import java.util.LinkedHashMap

internal enum class ChromePhotosResourceDecision {
    Safe,
    Block,
    Unknown,
    Passthrough,
}

internal data class ChromePhotosTransformResult(
    val bytes: ByteArray,
    val decision: ChromePhotosResourceDecision,
    val contentHash: String?,
    val cacheHit: Boolean,
)

/**
 * Deterministic byte-identity gate for the spike. Image decisions never depend
 * on URL, DOM coordinates, or request order.
 */
internal class ChromePhotosResourceTransformer(
    safeBytes: Collection<ByteArray>,
    blockedBytes: Collection<ByteArray>,
    private val placeholderBytes: ByteArray,
    private val maximumEntries: Int = DefaultMaximumEntries,
) {
    private val safeHashes = safeBytes.mapTo(mutableSetOf(), ::sha256)
    private val blockedHashes = blockedBytes.mapTo(mutableSetOf(), ::sha256)
    private val cache =
        object : LinkedHashMap<String, ChromePhotosResourceDecision>(maximumEntries, LoadFactor, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ChromePhotosResourceDecision>?,
            ): Boolean = size > maximumEntries
        }

    init {
        require(maximumEntries > 0)
        require(placeholderBytes.isNotEmpty())
        require(safeHashes.intersect(blockedHashes).isEmpty())
    }

    @Synchronized
    fun transform(
        contentType: String,
        candidateBytes: ByteArray,
    ): ChromePhotosTransformResult {
        if (!contentType.isImageContentType()) {
            return ChromePhotosTransformResult(
                bytes = candidateBytes,
                decision = ChromePhotosResourceDecision.Passthrough,
                contentHash = null,
                cacheHit = false,
            )
        }

        val hash = sha256(candidateBytes)
        val cached = cache[hash]
        val decision =
            cached ?: when (hash) {
                in safeHashes -> ChromePhotosResourceDecision.Safe
                in blockedHashes -> ChromePhotosResourceDecision.Block
                else -> ChromePhotosResourceDecision.Unknown
            }.also { cache[hash] = it }
        val output =
            when (decision) {
                ChromePhotosResourceDecision.Safe -> candidateBytes
                ChromePhotosResourceDecision.Block,
                ChromePhotosResourceDecision.Unknown,
                -> placeholderBytes
                ChromePhotosResourceDecision.Passthrough -> error("Image cannot be passthrough without a decision")
            }
        return ChromePhotosTransformResult(
            bytes = output,
            decision = decision,
            contentHash = hash,
            cacheHit = cached != null,
        )
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun cacheSize(): Int = cache.size

    private fun String.isImageContentType(): Boolean = lowercase().substringBefore(';').trim().startsWith("image/")

    private companion object {
        const val DefaultMaximumEntries = 32
        const val LoadFactor = 0.75f
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
