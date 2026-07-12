package com.contentfilter.core.domain.model

object WebMediaCatalog {
    val legacyImageRuleTargets: Set<String> =
        setOf(
            "images.google.com",
            "gstatic.com",
            "googleusercontent.com",
            "ggpht.com",
            "ytimg.com",
            "bing.net",
            "external-content.duckduckgo.com",
            "media.zenfs.com",
            "pinterest.com",
            "pinimg.com",
            "imgur.com",
            "staticflickr.com",
            "flickr.com",
            "unsplash.com",
            "pexels.com",
            "giphy.com",
            "tenor.com",
            "images.ctfassets.net",
            "imagekit.io",
            "imgix.net",
            "res.cloudinary.com",
            "cdninstagram.com",
            "fbcdn.net",
            "twimg.com",
            "gravatar.com",
            "wp.com",
        )

    private val dedicatedImageDomains =
        setOf(
            "ggpht.com",
            "ytimg.com",
            "pinimg.com",
            "staticflickr.com",
            "images.unsplash.com",
            "images.pexels.com",
            "media.giphy.com",
            "i.giphy.com",
            "media.tenor.com",
            "external-content.duckduckgo.com",
            "media.zenfs.com",
            "images.ctfassets.net",
            "imagekit.io",
            "imgix.net",
            "res.cloudinary.com",
            "cdninstagram.com",
            "fbcdn.net",
            "twimg.com",
            "gravatar.com",
            "wp.com",
        )

    fun isImageAssetHost(domain: String): Boolean {
        val host = domain.normalizedMediaHost()
        if (dedicatedImageDomains.any { host.matchesDomainTarget(it) }) return true
        val firstLabel = host.substringBefore('.')
        return when {
            host.matchesDomainTarget("gstatic.com") -> firstLabel.startsWith("encrypted-tbn")
            host.matchesDomainTarget("googleusercontent.com") ->
                firstLabel.matches(GoogleImageHostLabel) ||
                    firstLabel.startsWith("play-lh") ||
                    firstLabel.startsWith("yt")
            host.matchesDomainTarget("mm.bing.net") ->
                firstLabel.startsWith("tse") ||
                    firstLabel.startsWith("ts")
            host.matchesDomainTarget("bing.com") -> firstLabel == "th"
            host.matchesDomainTarget("imgur.com") -> firstLabel == "i"
            else -> false
        }
    }

    fun isMediaSearchView(
        domain: String,
        path: String?,
        rawQuery: String?,
    ): Boolean {
        val host = domain.normalizedMediaHost()
        val normalizedPath = path.orEmpty().lowercase()
        return when {
            host == "images.google.com" -> true
            SearchEngineCatalog.engineForDomain(host)?.id == "google" ->
                normalizedPath == "/imghp" ||
                    rawQuery.hasQueryValue("tbm", "isch", "vid") ||
                    rawQuery.hasQueryValue("udm", "2", "7")
            SearchEngineCatalog.engineForDomain(host)?.id == "bing" ->
                normalizedPath.startsWith("/images") || normalizedPath.startsWith("/videos")
            host == "images.search.yahoo.com" || host == "video.search.yahoo.com" -> true
            SearchEngineCatalog.engineForDomain(host)?.id == "yahoo" ->
                normalizedPath.startsWith("/images") || normalizedPath.startsWith("/video")
            SearchEngineCatalog.engineForDomain(host)?.id == "duckduckgo" ->
                rawQuery.hasQueryValue("ia", "images", "videos") ||
                    rawQuery.hasQueryValue("iax", "images", "videos")
            else -> false
        }
    }

    private fun String.normalizedMediaHost(): String =
        trim()
            .lowercase()
            .substringBefore('/')
            .substringBefore('?')
            .removeSuffix(".")
            .removePrefix("www.")

    private fun String.matchesDomainTarget(target: String): Boolean = this == target || endsWith(".$target")

    private fun String?.hasQueryValue(
        key: String,
        vararg expectedValues: String,
    ): Boolean =
        this
            ?.split('&')
            ?.any { parameter ->
                parameter.substringBefore('=', missingDelimiterValue = "").equals(key, ignoreCase = true) &&
                    expectedValues.any {
                        it.equals(parameter.substringAfter('=', missingDelimiterValue = ""), ignoreCase = true)
                    }
            } == true

    private val GoogleImageHostLabel = Regex("lh[0-9]+")
}
