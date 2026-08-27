package com.contentfilter.user.chromedataplane

import java.net.IDN
import java.util.Locale

internal object ChromePhotosRealWebLabConfig {
    const val HttpBingoHost = "httpbingo.org"
    const val GoogleStaticHost = "www.gstatic.com"
    const val GitHubHost = "github.com"
    const val GitHubRawHost = "raw.githubusercontent.com"
    const val FlickrStaticHost = "farm6.staticflickr.com"

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

    val gloshiaPublicJpegUrls: List<String> =
        listOf(
            "https://$FlickrStaticHost/5822/20582092196_9d95b6f648_o.jpg",
            "https://$FlickrStaticHost/5230/5638781189_0e6fce455f_o.jpg",
            "https://$FlickrStaticHost/4151/5054191013_66512b5c4c_o.jpg",
            "https://$FlickrStaticHost/3103/2382183276_3318f8e85f_o.jpg",
            "https://$FlickrStaticHost/2552/3851641637_6be328885c_o.jpg",
            "https://$FlickrStaticHost/41/85785791_72010e47eb_o.jpg",
            "https://$FlickrStaticHost/3850/14340510738_fa7c27b4e1_o.jpg",
            "https://$FlickrStaticHost/2926/14054216649_855e7f912b_o.jpg",
            "https://$FlickrStaticHost/210/474180770_15c72a6696_o.jpg",
            "https://$FlickrStaticHost/5600/15526796846_f43d9eb869_o.jpg",
            "https://$FlickrStaticHost/3560/3469462979_ccc4840905_o.jpg",
            "https://$FlickrStaticHost/1132/1306825778_63caee2b0a_o.jpg",
            "https://$FlickrStaticHost/3690/12022741784_9f8f0abc1e_o.jpg",
            "https://$FlickrStaticHost/3256/2858049912_ef32c5bc5f_o.jpg",
            "https://$FlickrStaticHost/3501/4069272516_1f0bdff9f8_o.jpg",
            "https://$FlickrStaticHost/5236/5829923957_5045aba7f4_o.jpg",
            "https://$FlickrStaticHost/3200/2970012318_98f7c80583_o.jpg",
        )

    /** Historical /32 fallback only. This is not CONNECT or web-navigation authority. */
    val controlledRouteHosts: Set<String> =
        setOf(
            HttpBingoHost,
            GoogleStaticHost,
            GitHubHost,
            GitHubRawHost,
            FlickrStaticHost,
        )

    val safeHashes: Set<String> = setOf(SafePngSha256)
    val blockedHashes: Set<String> = setOf(BlockWebpSha256)
}

internal data class ChromePhotosConnectTarget(
    val host: String,
    val port: Int,
) {
    companion object {
        fun parseSyntax(requestLine: String): ChromePhotosConnectTarget? {
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
            val host = runCatching { normalizeDnsHost(rawHost) }.getOrNull() ?: return null
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
