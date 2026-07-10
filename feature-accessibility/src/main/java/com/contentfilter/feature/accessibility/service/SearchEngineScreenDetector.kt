package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.WebNavigationPolicy
import com.contentfilter.core.domain.model.googleResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webImagesBlocked
import com.contentfilter.core.domain.model.webNavigationBlocked

class SearchEngineScreenDetector {
    fun shouldLeaveSearchEngine(
        packageName: String,
        snapshot: PolicySnapshot,
        visibleText: String,
        recentDnsBlockHost: String? = null,
    ): Boolean = diagnose(packageName, snapshot, visibleText, recentDnsBlockHost).shouldLeave

    fun diagnose(
        packageName: String,
        snapshot: PolicySnapshot,
        visibleText: String,
        recentDnsBlockHost: String? = null,
    ): SearchEngineScreenDiagnosis {
        val packageCategory = packageName.searchSurfaceCategory()
        val webNavigationBlocked = snapshot.rules.webNavigationBlocked()
        if (packageCategory == SearchSurfaceCategory.NonBrowser) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "non-browser",
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = null,
                searchBlockRules = 0,
                visibleTextLength = visibleText.length,
            )
        }
        if (webNavigationBlocked) {
            if (snapshot.rules.googleResultsAllowed() && packageCategory != SearchSurfaceCategory.NonBrowser) {
                val normalizedText = visibleText.lowercase()
                val recentHost = recentDnsBlockHost?.normalizedHost()
                val imageBlocked =
                    snapshot.rules.webImagesBlocked() &&
                        (normalizedText.hasImageSignal() ||
                            recentHost?.let(WebNavigationPolicy::isImageDomain) == true)
                val unsafeSearchBlocked =
                    snapshot.rules.safeSearchEnabled() &&
                        recentHost?.let(WebNavigationPolicy::isUnsafeSearchDomain) == true
                val onGoogleSearch =
                    normalizedText.indicatesGoogleSearchScreen() ||
                        recentHost?.let(WebNavigationPolicy::isGoogleSearchDomain) == true
                val openedExternalResult =
                    recentHost != null &&
                        !WebNavigationPolicy.isGoogleSearchDomain(recentHost) &&
                        !WebNavigationPolicy.isImageDomain(recentHost)
                val shouldLeave = imageBlocked || unsafeSearchBlocked || openedExternalResult || !onGoogleSearch
                return SearchEngineScreenDiagnosis(
                    shouldLeave = shouldLeave,
                    reason =
                        when {
                            imageBlocked -> "images-blocked"
                            unsafeSearchBlocked -> "unsafe-search-blocked"
                            openedExternalResult -> "google-result-opened"
                            onGoogleSearch -> "google-results-allowed"
                            else -> "web-blocked-not-google-results"
                        },
                    webNavigationBlocked = webNavigationBlocked,
                    packageCategory = packageCategory.label,
                    recentDnsBlockHost = recentDnsBlockHost,
                    searchBlockRules = snapshot.searchEngineBlockRuleCount(),
                    visibleTextLength = visibleText.length,
                )
            }
            return SearchEngineScreenDiagnosis(
                shouldLeave = true,
                reason = "web-navigation-blocked",
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = recentDnsBlockHost,
                searchBlockRules = snapshot.searchEngineBlockRuleCount(),
                visibleTextLength = visibleText.length,
            )
        }
        val blockRuleCount = snapshot.searchEngineBlockRuleCount()
        if (blockRuleCount == 0) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "search-rules-not-blocked",
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = null,
                searchBlockRules = 0,
                visibleTextLength = visibleText.length,
            )
        }
        val hasVisibleSearchSignal = visibleText.indicatesSearchEngineScreen()
        val blockedRecentSearchHost = recentDnsBlockHost?.takeIf { it.isSearchProtectionHost() }
        if (!hasVisibleSearchSignal && blockedRecentSearchHost == null) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "no-search-signal",
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = blockedRecentSearchHost,
                searchBlockRules = blockRuleCount,
                visibleTextLength = visibleText.length,
            )
        }
        if (packageCategory == SearchSurfaceCategory.Browser) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = true,
                reason =
                    when {
                        hasVisibleSearchSignal -> "browser-search-signal-observed"
                        blockedRecentSearchHost != null -> "browser-recent-dns-block-observed"
                        else -> "browser-search-blocked"
                    },
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = blockedRecentSearchHost,
                searchBlockRules = blockRuleCount,
                visibleTextLength = visibleText.length,
            )
        }
        return SearchEngineScreenDiagnosis(
            shouldLeave = true,
            reason = "blocked-search-screen",
            webNavigationBlocked = webNavigationBlocked,
            packageCategory = packageCategory.label,
            recentDnsBlockHost = blockedRecentSearchHost,
            searchBlockRules = blockRuleCount,
            visibleTextLength = visibleText.length,
        )
    }

    private fun String.searchSurfaceCategory(): SearchSurfaceCategory =
        when (this) {
            in BrowserPackageNames -> SearchSurfaceCategory.Browser
            in SearchAppPackageNames -> SearchSurfaceCategory.SearchApp
            else -> SearchSurfaceCategory.NonBrowser
        }

    private fun PolicySnapshot.searchEngineBlockRuleCount(): Int =
        rules.count {
            it.enabled &&
                it.scope == RuleScope.Domain &&
                it.action == RuleAction.Block &&
                it.target in SearchEngineCatalog.searchEngineDomains
        }

    private fun String.indicatesSearchEngineScreen(): Boolean {
        val normalized = lowercase()
        if (SearchEngineCatalog.searchEngineDomains.any { normalized.contains(it.normalizedHost()) }) return true
        return SearchEngineSignals.any { it in normalized }
    }

    private fun String.indicatesGoogleSearchScreen(): Boolean =
        contains("google") &&
            (contains("search") ||
                contains("buscar") ||
                contains("resultados") ||
                contains("resultados de búsqueda") ||
                contains("resultados de busqueda"))

    private fun String.hasImageSignal(): Boolean =
        ImageSignals.any { it in this } || ImageExtensions.any { it in this }

    private fun String.isSearchProtectionHost(): Boolean {
        val normalized = normalizedHost()
        return SearchEngineCatalog.searchEngineDomains.any { normalized.matchesDomainTarget(it.normalizedHost()) } ||
            SearchEngineCatalog.secureDnsDomains.any { normalized.matchesDomainTarget(it.normalizedHost()) }
    }

    private fun String.normalizedHost(): String =
        lowercase()
            .substringBefore("/")
            .substringBefore("?")
            .removeSuffix(".")
            .removePrefix("www.")

    private fun String.matchesDomainTarget(target: String): Boolean = this == target || endsWith(".$target")

    private companion object {
        val BrowserPackageNames =
            setOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "com.microsoft.emmx",
                "com.brave.browser",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.duckduckgo.mobile.android",
                "com.vivaldi.browser",
                "com.kiwibrowser.browser",
                "com.UCMobile.intl",
                "mark.via.gp",
            )

        val SearchAppPackageNames =
            setOf(
                "com.google.android.googlequicksearchbox",
            )

        val SearchEngineSignals =
            setOf(
                "google search",
                "buscar con google",
                "bing search",
                "yahoo search",
                "duckduckgo search",
                "search results",
                "resultados de búsqueda",
                "resultados de busqueda",
            )

        val ImageSignals =
            setOf(
                "google imágenes",
                "google imagenes",
                "images",
                "imágenes",
                "imagenes",
            )

        val ImageExtensions =
            setOf(
                ".jpg",
                ".jpeg",
                ".png",
                ".webp",
                ".gif",
                ".bmp",
                ".svg",
            )
    }
}

data class SearchEngineScreenDiagnosis(
    val shouldLeave: Boolean,
    val reason: String,
    val webNavigationBlocked: Boolean,
    val packageCategory: String,
    val recentDnsBlockHost: String?,
    val searchBlockRules: Int,
    val visibleTextLength: Int,
)

private enum class SearchSurfaceCategory(val label: String) {
    Browser("browser"),
    SearchApp("searchApp"),
    NonBrowser("non-browser"),
}
