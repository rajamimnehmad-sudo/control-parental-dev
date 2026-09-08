package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Local bytes enter the same image authority as upstream photos; only sanitized results are retained. */
internal class ChromeLocalPhotoEndpoint(
    private val sanitize: (ChromePhotosUpstreamResponse) -> ChromePhotosSanitizedResponse,
    private val authorize: (ChromePhotosSanitizedResponse) -> Boolean,
    private val validates: (String, ChromeMediaShieldSelfReadyIdentity) -> Boolean =
        ChromeMediaShieldDocumentAuthorityRegistry::validatesClaimedSelfReady,
    private val maximumStoredBytes: Int = 8 * 1024 * 1024,
) : AutoCloseable {
    private data class Owner(val token: String, val identity: ChromeMediaShieldSelfReadyIdentity)

    private data class Asset(val owner: Owner, val origin: String, val response: ChromePhotosSanitizedResponse)

    private val lock = Any()
    private val assets = LinkedHashMap<String, Asset>(128, 0.75f, true)
    private val permits = Semaphore(2, true)
    private val random = SecureRandom()
    private var storedBytes = 0
    private var closed = false
    private val submitted = AtomicLong()
    private val served = AtomicLong()
    private val rejected = AtomicLong()

    fun metrics(): String =
        synchronized(lock) {
            "submitted=${submitted.get()},served=${served.get()},rejected=${rejected.get()},entries=${assets.size},bytes=$storedBytes"
        }

    fun handle(
        origin: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosSanitizedResponse? {
        if (request.target.startsWith(AssetPath)) return serve(origin, request)
        if (request.target != SubmitPath) return null
        submitted.incrementAndGet()
        if (request.method != "POST" || request.body.size > MaximumRequestBytes ||
            request.headerValues("Origin").singleOrNull() != origin ||
            request.headerValues("Content-Type").singleOrNull()?.substringBefore(';')?.trim() != "text/plain"
        ) {
            return reject(403)
        }
        val split = request.body.indexOf('\n'.code.toByte())
        if (split !in 1..512) return reject(400)
        val owner = parseOwner(request.body.copyOfRange(0, split).toString(Charsets.UTF_8)) ?: return reject(403)
        if (!valid(owner)) return reject(403)
        val admitted =
            try {
                permits.tryAcquire(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return reject(503)
            }
        if (!admitted) return reject(429)
        try {
            val decoded = ChromeLocalPhotoDataUrl.decode(request.body.copyOfRange(split + 1, request.body.size).toString(Charsets.UTF_8)) ?: return reject(415)
            val response =
                try {
                    sanitize(
                        ChromePhotosUpstreamResponse(
                            host = URI(origin).host,
                            statusCode = 200,
                            statusText = "OK",
                            headers = listOf(ChromeHttpHeader("Content-Type", decoded.mimeType)),
                            body = decoded.bytes.inputStream(),
                            bodyLength = decoded.bytes.size.toLong(),
                            protocol = "local-static-photo",
                        ),
                    )
                } finally {
                    decoded.bytes.fill(0)
                }
            // A passthrough response can never become an authorized raster asset.
            if (!authorize(response) || response.decision == ChromePhotosResourceDecision.Passthrough) {
                return reject(
                    415,
                )
            }
            val id =
                synchronized(lock) {
                    if (closed || !valid(owner) || response.bytes.size > maximumStoredBytes) return reject(403)
                    prune()
                    while (assets.isNotEmpty() && (assets.size >= 128 || storedBytes + response.bytes.size > maximumStoredBytes)) {
                        remove(assets.keys.first())
                    }
                    val key = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
                    assets[key] = Asset(owner, origin, response)
                    storedBytes += response.bytes.size
                    key
                }
            return plain(200, AssetOrigin + AssetPath + id)
        } finally {
            request.body.fill(0)
            permits.release()
        }
    }

    private fun serve(
        origin: String,
        request: ChromePhotosProxyRequest,
    ): ChromePhotosSanitizedResponse {
        if (origin != AssetOrigin || request.method != "GET") return reject(403)
        val key = request.target.removePrefix(AssetPath)
        if (key.length != 48 || key.any { it !in "0123456789abcdef" }) return reject(404)
        return synchronized(lock) {
            if (closed) return reject(410)
            val asset = assets[key] ?: return reject(410)
            if (!valid(asset.owner) || !authorize(asset.response)) {
                remove(key)
                return reject(410)
            }
            val corsOrigin = request.headerValues("Origin").singleOrNull()
            if (request.headerValues("Origin").size > 1 || (corsOrigin != null && corsOrigin != asset.origin)) {
                return reject(
                    403,
                )
            }
            served.incrementAndGet()
            asset.response.copy(
                headers =
                    listOf(
                        ChromeHttpHeader("Content-Type", asset.response.contentType ?: "image/png"),
                        ChromeHttpHeader("Cache-Control", "no-store"),
                        ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
                        ChromeHttpHeader("Content-Security-Policy", "default-src 'none'; sandbox"),
                    ) + if (corsOrigin != null) listOf(ChromeHttpHeader("Access-Control-Allow-Origin", corsOrigin), ChromeHttpHeader("Access-Control-Allow-Credentials", "true")) else emptyList(),
            )
        }
    }

    private fun valid(owner: Owner) = validates(owner.token, owner.identity)

    private fun prune() {
        assets.filterValues { !valid(it.owner) }.keys.toList().forEach(::remove)
    }

    private fun remove(key: String) {
        assets.remove(key)?.let { storedBytes -= it.response.bytes.size }
    }

    private fun parseOwner(line: String): Owner? {
        val f = line.split('|')
        if (f.size != 8 || f[0] != "v1" || f[1] != "LOCAL_PHOTO") return null
        return Owner(
            f[2],
            ChromeMediaShieldSelfReadyIdentity(
                protectionSessionId = f[3],
                policyEpoch = f[4].toLongOrNull() ?: return null,
                navigationSequence = f[5].toLongOrNull() ?: return null,
                documentSequence = f[6].toLongOrNull() ?: return null,
                lifecycleSequence = 1,
                topLevel =
                    when (f[7]) {
                        "T" -> true
                        "S" -> false
                        else -> return null
                    },
            ),
        )
    }

    private fun reject(code: Int): ChromePhotosSanitizedResponse {
        rejected.incrementAndGet()
        return plain(code, "")
    }

    private fun plain(
        code: Int,
        value: String,
    ) = ChromePhotosSanitizedResponse(
        code,
        if (code == 200) "OK" else "Rejected",
        listOf(
            ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8"),
            ChromeHttpHeader("Cache-Control", "no-store"),
            ChromeHttpHeader("X-Content-Type-Options", "nosniff"),
        ),
        value.toByteArray(Charsets.UTF_8),
        ChromePhotosResourceDecision.Passthrough,
        false,
        null,
        0,
    )

    override fun close() =
        synchronized(lock) {
            closed = true
            assets.clear()
            storedBytes = 0
        }

    companion object {
        const val SubmitPath = "/__glosh/local-photo/submit"
        const val AssetPath = "/__glosh/local-photo/asset/"
        const val AssetOrigin = "https://" + ChromePhotosDataPlaneLabContract.FixtureHost
        const val MaximumRequestBytes = 1400 * 1024
    }
}

internal data class ChromeLocalPhotoDataUrl(val mimeType: String, val bytes: ByteArray) {
    companion object {
        fun decode(value: String): ChromeLocalPhotoDataUrl? {
            if (value.length > ChromeLocalPhotoEndpoint.MaximumRequestBytes || !value.startsWith("data:", true)) return null
            val comma = value.indexOf(',')
            if (comma !in 6..128) return null
            val parts = value.substring(5, comma).lowercase().split(';')
            if (parts.size != 2 || parts[1] != "base64" || parts[0] !in setOf("image/jpeg", "image/png", "image/webp", "image/avif")) return null
            val payload = value.substring(comma + 1)
            if (payload.any { !(it.isLetterOrDigit() && it.code < 128) && it !in "+/=\r\n\t " }) return null
            val bytes = runCatching { Base64.getDecoder().decode(payload.filterNot { it in "\r\n\t " }) }.getOrNull() ?: return null
            if (bytes.isEmpty() || bytes.size > 1024 * 1024) {
                bytes.fill(0)
                return null
            }
            return ChromeLocalPhotoDataUrl(parts[0], bytes)
        }
    }
}
