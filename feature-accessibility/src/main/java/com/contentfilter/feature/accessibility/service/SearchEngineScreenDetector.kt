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
        val blockRuleCount = snapshot.searchEngineBlockRuleCount()
        if (packageCategory == SearchSurfaceCategory.NonBrowser) {
            return SearchEngineScreenDiagnosis(
                shouldLeave = false,
                reason = "non-browser",
                webNavigationBlocked = webNavigationBlocked,
                packageCategory = packageCategory.label,
                recentDnsBlockHost = null,
                searchBlockRules = blockRuleCount,
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
        return SearchEngineScreenDiagnosis(
            shouldLeave = false,
            reason = "web-navigation-open",
            webNavigationBlocked = webNavigationBlocked,
            packageCategory = packageCategory.label,
            recentDnsBlockHost = null,
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

    private fun String.indicatesGoogleSearchScreen(): Boolean =
        contains("google") &&
            (contains("search") ||
                contains("buscar") ||
                contains("resultados") ||
                contains("resultados de búsqueda") ||
                contains("resultados de busqueda"))

    private fun String.hasImageSignal(): Boolean =
        ImageSignals.any { it in this } || ImageExtensions.any { it in this }

    private fun String.normalizedHost(): String =
        lowercase()
            .substringBefore("/")
            .substringBefore("?")
            .removeSuffix(".")
            .removePrefix("www.")

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
