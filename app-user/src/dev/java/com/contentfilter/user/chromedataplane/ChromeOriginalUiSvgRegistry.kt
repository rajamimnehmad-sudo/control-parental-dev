package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

internal data class ChromeOriginalUiSvgAsset(
    val digest: String,
    val bytes: ByteArray,
)

internal class ChromeOriginalUiSvgRegistry(
    private val validator: ChromeOriginalUiSvgValidator = ChromeOriginalUiSvgValidator(),
    private val maximumEntries: Int = 512,
    private val maximumTotalBytes: Int = 4 * 1024 * 1024,
    randomBytes: (Int) -> ByteArray = { size -> ByteArray(size).also(SecureRandom()::nextBytes) },
) : AutoCloseable {
    private val generation = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(TokenBytes))
    private val capability = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(TokenBytes))
    private val active = AtomicBoolean(true)
    private val entries = linkedMapOf<String, ByteArray>()
    private var totalBytes = 0

    @Synchronized
    fun register(bytes: ByteArray): String? {
        if (!active.get()) return null
        val valid = validator.validate(bytes, SvgMimeType) as? ChromeOriginalUiSvgValidation.Valid ?: return null
        val digest = sha256(valid.bytes)
        if (digest !in entries) {
            if (entries.size >= maximumEntries || totalBytes + valid.bytes.size > maximumTotalBytes) return null
            entries[digest] = valid.bytes.copyOf()
            totalBytes += valid.bytes.size
        }
        return "https://${ChromePhotosDataPlaneLabContract.OriginalUiSvgHost}$PathPrefix/$generation/$capability/$digest.svg"
    }

    @Synchronized
    fun resolve(path: String): ChromeOriginalUiSvgAsset? {
        if (!active.get()) return null
        if ('?' in path || '#' in path) return null
        val parts = path.split('/')
        if (parts.size != 7 || parts[1] != ".well-known" || parts[2] != "glosh-ui-svg" || parts[3] != "v1") return null
        if (parts[4] != generation || parts[5] != capability) return null
        val filename = parts[6]
        if (!filename.endsWith(".svg")) return null
        val digest = filename.removeSuffix(".svg")
        if (!Digest.matches(digest)) return null
        val bytes = entries[digest] ?: return null
        if (sha256(bytes) != digest) return null
        return ChromeOriginalUiSvgAsset(digest, bytes.copyOf())
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun bytes(): Int = totalBytes

    @Synchronized
    override fun close() {
        active.set(false)
        entries.values.forEach { it.fill(0) }
        entries.clear()
        totalBytes = 0
    }

    private companion object {
        const val TokenBytes = 18
        const val SvgMimeType = "image/svg+xml"
        const val PathPrefix = "/.well-known/glosh-ui-svg/v1"
        val Digest = Regex("[0-9a-f]{64}")
    }
}
