package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.net.IDN
import java.util.Locale

internal object ChromePhotosRealWebLabConfig {
    const val HttpBingoHost = "httpbingo.org"
    const val GoogleStaticHost = "www.gstatic.com"
    const val GitHubHost = "github.com"
    const val GitHubRawHost = "raw.githubusercontent.com"

    const val SafePngUrl = "https://httpbingo.org/image/png"
    const val UnknownJpegUrl = "https://httpbingo.org/image/jpeg"
    const val UnknownWebpUrl = "https://httpbingo.org/image/webp"
    const val BlockWebpUrl = "https://www.gstatic.com/webp/gallery/1.webp"
    const val PublicHtmlUrl = "https://httpbingo.org/html"

    const val AvifRepositoryCommit = "bf4c18d1f3971069b75e87d6ee469790589f4f09"
    const val UnknownAvifUrl =
        "https://raw.githubusercontent.com/AOMediaCodec/av1-avif/" +
            "$AvifRepositoryCommit/testFiles/Microsoft/Irvine_CA.avif"
    const val AllowedRedirectUrl =
        "https://github.com/AOMediaCodec/av1-avif/raw/" +
            "$AvifRepositoryCommit/testFiles/Microsoft/Irvine_CA.avif"
    const val DisallowedRedirectUrl =
        "https://httpbingo.org/redirect-to?" +
            "url=https%3A%2F%2Fexample.com%2F&status_code=302"

    const val SafePngSha256 = "541a1ef5373be3dc49fc542fd9a65177b664aec01c8d8608f99e6ec95577d8c1"
    const val BlockWebpSha256 = "4a5afeaff8483923da964bc7896f02d0283e8bff99b5b8f82a31ae3214dab1d0"

    val allowedHosts: Set<String> =
        setOf(
            ChromePhotosDataPlaneLabContract.FixtureHost,
            HttpBingoHost,
            GoogleStaticHost,
            GitHubHost,
            GitHubRawHost,
        )

    val realHosts: Set<String> = allowedHosts - ChromePhotosDataPlaneLabContract.FixtureHost

    val safeHashes: Set<String> = setOf(SafePngSha256)
    val blockedHashes: Set<String> = setOf(BlockWebpSha256)
}

internal class ChromePhotosHostAllowlist(
    hosts: Collection<String>,
) {
    private val normalizedHosts = hosts.mapTo(linkedSetOf(), ::normalizeDnsHost)

    init {
        require(normalizedHosts.isNotEmpty())
    }

    fun normalizeAllowed(rawHost: String): String? =
        runCatching { normalizeDnsHost(rawHost) }
            .getOrNull()
            ?.takeIf(normalizedHosts::contains)

    fun isAllowed(rawHost: String): Boolean = normalizeAllowed(rawHost) != null

    fun hosts(): Set<String> = normalizedHosts.toSet()
}

internal data class ChromePhotosConnectTarget(
    val host: String,
    val port: Int,
) {
    companion object {
        fun parse(
            requestLine: String,
            allowlist: ChromePhotosHostAllowlist,
        ): ChromePhotosConnectTarget? {
            val parts = requestLine.trim().split(Regex("\\s+"))
            if (
                parts.size != 3 ||
                !parts[0].equals("CONNECT", ignoreCase = true) ||
                !parts[2].startsWith("HTTP/1.")
            ) {
                return null
            }
            val authority = parts[1]
            if (authority.count { it == ':' } != 1) return null
            val rawHost = authority.substringBefore(':')
            val port = authority.substringAfter(':').toIntOrNull() ?: return null
            if (port != HttpsPort) return null
            val host = allowlist.normalizeAllowed(rawHost) ?: return null
            return ChromePhotosConnectTarget(host = host, port = port)
        }

        private const val HttpsPort = 443
    }
}

internal fun normalizeDnsHost(rawHost: String): String {
    require(rawHost.isNotBlank())
    require(!rawHost.endsWith('.'))
    require('*' !in rawHost)
    require(':' !in rawHost)
    require(!rawHost.matches(Regex("[0-9.]+")))
    val ascii = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US)
    require(ascii.length in 1..253)
    require(ascii.split('.').all { label -> label.length in 1..63 })
    return ascii
}
