package com.contentfilter.user.dag2

import android.webkit.CookieManager
import com.contentfilter.core.network.security.PublicDestinationDecision
import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface DagV2CalibrationFetchResult {
    data class Success(
        val bytes: ByteArray,
        val mimeType: String,
    ) : DagV2CalibrationFetchResult

    data class Rejected(
        val reason: String,
    ) : DagV2CalibrationFetchResult
}

@Singleton
class DagV2CalibrationImageFetcher
    @Inject
    constructor(
        baseClient: OkHttpClient,
        private val destinationGuard: PublicNetworkDestinationGuard,
    ) {
        private val client =
            baseClient
                .newBuilder()
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 4
                        maxRequestsPerHost = 2
                    },
                ).followRedirects(false)
                .followSslRedirects(false)
                .dns(DagV2PublicOnlyDns)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(18, TimeUnit.SECONDS)
                .build()

        suspend fun fetch(candidate: DagV2CalibrationCandidate): DagV2CalibrationFetchResult =
            withContext(Dispatchers.IO) {
                if (!candidate.reviewable) return@withContext rejected("candidate_not_reviewable")
                var currentUrl = candidate.resourceUrl
                repeat(MaxRedirects + 1) { redirectCount ->
                    coroutineContext.ensureActive()
                    val guard = destinationGuard.validateNavigation(currentUrl)
                    if (guard.decision != PublicDestinationDecision.Allow) {
                        return@withContext rejected("destination_blocked")
                    }
                    val request =
                        Request
                            .Builder()
                            .url(currentUrl)
                            .get()
                            .header("Accept", AllowedAccept)
                            .header("Referer", candidate.documentOrigin)
                            .header("Cache-Control", "no-store")
                            .apply {
                                CookieManager.getInstance().getCookie(currentUrl)
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { header("Cookie", it) }
                            }.build()
                    val response =
                        runCatching { client.newCall(request).execute() }
                            .getOrElse { return@withContext rejected("network_failure") }
                    response.use {
                        if (it.isRedirect) {
                            if (redirectCount >= MaxRedirects) return@withContext rejected("too_many_redirects")
                            val location = it.header("Location") ?: return@withContext rejected("invalid_redirect")
                            currentUrl =
                                runCatching { URI(currentUrl).resolve(location).toString() }
                                    .getOrElse { return@withContext rejected("invalid_redirect") }
                            return@repeat
                        }
                        if (!it.isSuccessful) return@withContext rejected("http_failure")
                        val contentLength = it.body?.contentLength() ?: -1L
                        if (contentLength > MaxSourceBytes) return@withContext rejected("source_too_large")
                        val declaredMime =
                            it.header("Content-Type")
                                ?.substringBefore(';')
                                ?.trim()
                                ?.lowercase()
                                .orEmpty()
                        if (declaredMime !in AllowedMimeTypes) return@withContext rejected("mime_not_allowed")
                        val body = it.body ?: return@withContext rejected("empty_body")
                        val bytes =
                            body.byteStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (output.size() + read > MaxSourceBytes) {
                                        buffer.fill(0)
                                        output.reset()
                                        return@withContext rejected("source_too_large")
                                    }
                                    output.write(buffer, 0, read)
                                }
                                buffer.fill(0)
                                output.toByteArray()
                            }
                        if (!bytes.matchesRasterSignature(declaredMime)) {
                            bytes.fill(0)
                            return@withContext rejected("invalid_image_signature")
                        }
                        return@withContext DagV2CalibrationFetchResult.Success(bytes, declaredMime)
                    }
                }
                rejected("redirect_failure")
            }

        fun cancelAll() {
            client.dispatcher.cancelAll()
        }

        private fun rejected(reason: String) = DagV2CalibrationFetchResult.Rejected(reason)

        private fun ByteArray.matchesRasterSignature(mime: String): Boolean =
            when (mime) {
                "image/jpeg" -> size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte()
                "image/png" ->
                    size >= 8 &&
                        copyOfRange(0, 8).contentEquals(
                            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
                        )
                "image/gif" -> size >= 6 && String(this, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
                "image/webp" ->
                    size >= 12 &&
                        String(this, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(this, 8, 4, Charsets.US_ASCII) == "WEBP"
                "image/bmp" -> size >= 2 && this[0] == 'B'.code.toByte() && this[1] == 'M'.code.toByte()
                else -> false
            }

        private companion object {
            const val MaxSourceBytes = 4 * 1024 * 1024
            const val MaxRedirects = 5
            const val AllowedAccept = "image/jpeg,image/png,image/webp,image/gif,image/bmp"
            val AllowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
        }
    }

private object DagV2PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !PublicNetworkDestinationGuard.isPublicAddress(it) }) {
            throw UnknownHostException("DAG v2 blocked a private or special destination")
        }
        return addresses
    }
}
