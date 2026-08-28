package com.contentfilter.user.chromedataplane

import java.security.MessageDigest
import java.util.LinkedHashMap

internal enum class ChromePhotosResourceDecision {
    Safe,
    Block,
    Unknown,
    Passthrough,
    AuditReplaced,
}

internal data class ChromePhotosTransformResult(
    val bytes: ByteArray,
    val decision: ChromePhotosResourceDecision,
    val contentHash: String?,
    val cacheHit: Boolean,
    val decisionResult: ChromePhotoDecisionResult? = null,
)

/** Applies a content-identity decision session before any image body reaches Chrome. */
internal class ChromePhotosResourceTransformer private constructor(
    private val placeholderBytes: ByteArray,
    private val decisionSession: ChromePhotoDecisionSession,
) : AutoCloseable {
    constructor(
        safeBytes: Collection<ByteArray>,
        blockedBytes: Collection<ByteArray>,
        placeholderBytes: ByteArray,
        safeContentHashes: Collection<String> = emptySet(),
        blockedContentHashes: Collection<String> = emptySet(),
        maximumEntries: Int = DefaultMaximumEntries,
    ) : this(
        placeholderBytes = placeholderBytes,
        decisionSession =
            HashRegistryDecisionSession(
                safeBytes = safeBytes,
                blockedBytes = blockedBytes,
                safeContentHashes = safeContentHashes,
                blockedContentHashes = blockedContentHashes,
                maximumEntries = maximumEntries,
            ),
    )

    init {
        require(placeholderBytes.isNotEmpty())
    }

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
        val result = decisionSession.decide(hash, candidateBytes, contentType.normalizedImageMimeType())
        val decision =
            when (result.decision) {
                ChromePhotoDecision.Safe -> ChromePhotosResourceDecision.Safe
                ChromePhotoDecision.Block -> ChromePhotosResourceDecision.Block
                ChromePhotoDecision.Unknown -> ChromePhotosResourceDecision.Unknown
            }
        val output = if (decision == ChromePhotosResourceDecision.Safe) candidateBytes else placeholderBytes
        return ChromePhotosTransformResult(
            bytes = output,
            decision = decision,
            contentHash = hash,
            cacheHit = result.source == ChromePhotoDecisionSource.Cache,
            decisionResult = result,
        )
    }

    fun clear() = decisionSession.clear()

    fun cacheSize(): Int = decisionSession.cacheSize()

    fun decisionMetrics(): ChromePhotoDecisionSessionMetrics = decisionSession.metrics()

    override fun close() = decisionSession.close()

    private fun String.isImageContentType(): Boolean = normalizedImageMimeType().startsWith("image/")

    internal companion object {
        const val DefaultMaximumEntries = 32

        fun forDecisionSession(
            decisionSession: ChromePhotoDecisionSession,
            placeholderBytes: ByteArray,
        ) = ChromePhotosResourceTransformer(placeholderBytes, decisionSession)
    }
}

private class HashRegistryDecisionSession(
    safeBytes: Collection<ByteArray>,
    blockedBytes: Collection<ByteArray>,
    safeContentHashes: Collection<String> = emptySet(),
    blockedContentHashes: Collection<String> = emptySet(),
    private val maximumEntries: Int,
) : ChromePhotoDecisionSession {
    private val safeHashes = safeBytes.mapTo(mutableSetOf(), ::sha256).apply { addAll(safeContentHashes) }
    private val blockedHashes = blockedBytes.mapTo(mutableSetOf(), ::sha256).apply { addAll(blockedContentHashes) }
    private val cache =
        object : LinkedHashMap<HashRegistryKey, ChromePhotoDecision>(maximumEntries, LoadFactor, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<HashRegistryKey, ChromePhotoDecision>?,
            ): Boolean =
                size > maximumEntries
        }

    init {
        require(maximumEntries > 0)
        require((safeHashes + blockedHashes).all { hash -> hash.matches(Sha256Pattern) })
        require(safeHashes.intersect(blockedHashes).isEmpty())
    }

    @Synchronized
    override fun decide(
        contentHash: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult {
        val key = HashRegistryKey(contentHash, mimeType.normalizedImageMimeType())
        val cached = cache[key]
        val decision =
            cached ?: when (contentHash) {
                in safeHashes -> ChromePhotoDecision.Safe
                in blockedHashes -> ChromePhotoDecision.Block
                else -> ChromePhotoDecision.Unknown
            }.also { cache[key] = it }
        return ChromePhotoDecisionResult(
            decision = decision,
            reason = "deterministic_hash_${decision.name.lowercase()}",
            source = if (cached == null) ChromePhotoDecisionSource.Engine else ChromePhotoDecisionSource.Cache,
        )
    }

    @Synchronized
    override fun clear() {
        cache.clear()
    }

    @Synchronized
    override fun cacheSize(): Int = cache.size

    private companion object {
        const val LoadFactor = 0.75f
        val Sha256Pattern = Regex("[0-9a-f]{64}")
    }

    private data class HashRegistryKey(
        val contentHash: String,
        val canonicalMimeType: String,
    )
}

internal fun chromePhotosDeterministicTransformer(origin: ChromePhotosFixtureSource): ChromePhotosResourceTransformer =
    ChromePhotosResourceTransformer(
        safeBytes = listOf(origin.safeImageBytes),
        blockedBytes = listOf(origin.sentinelImageBytes),
        placeholderBytes = origin.placeholderImageBytes,
        safeContentHashes = ChromePhotosRealWebLabConfig.safeHashes,
        blockedContentHashes = ChromePhotosRealWebLabConfig.blockedHashes,
    )

internal fun chromePhotosGloshiaTransformer(
    decisionSession: ChromePhotoDecisionSession,
    origin: ChromePhotosFixtureSource,
): ChromePhotosResourceTransformer =
    ChromePhotosResourceTransformer.forDecisionSession(
        decisionSession = decisionSession,
        placeholderBytes = origin.placeholderImageBytes,
    )

internal fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
