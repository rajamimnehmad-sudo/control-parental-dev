package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.webNavigationBlocked
import java.net.URI

class SearchEngineScreenDetector(
    private val searchSessionWindowMillis: Long = DefaultSearchSessionWindowMillis,
) {
    private var searchOrigin: SearchOrigin? = null

    fun diagnose(
        packageName: String,
        snapshot: PolicySnapshot,
        currentHost: String?,
        addressBarFocused: Boolean = false,
        recentSearchEngineId: String? = null,
        elapsedRealtimeMillis: Long = System.nanoTime() / NanosPerMillisecond,
    ): SearchEngineScreenDiagnosis {
        val packageCategory = packageName.searchSurfaceCategory()
        val webNavigationBlocked = snapshot.rules.webNavigationBlocked()
        val externalResultsAllowed = snapshot.rules.externalSearchResultsAllowed()
        val detectedEngine = SearchEngineCatalog.engineForDomain(currentHost)
        val origin = searchOrigin?.takeIf { elapsedRealtimeMillis - it.observedAtMillis <= searchSessionWindowMillis }
        if (origin == null) searchOrigin = null

        if (packageCategory == SearchSurfaceCategory.NonBrowser) {
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "non-browser",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        if (webNavigationBlocked) {
            searchOrigin = null
            return diagnosis(
                action = SearchNavigationAction.GoHome,
                reason = "web-navigation-blocked",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id ?: recentSearchEngineId,
            )
        }
        if (externalResultsAllowed) {
            searchOrigin = null
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "external-results-allowed",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        if (addressBarFocused) {
            searchOrigin = null
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "address-bar-navigation",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        if (detectedEngine != null) {
            searchOrigin = SearchOrigin(detectedEngine.id, elapsedRealtimeMillis)
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "search-results-visible",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine.id,
            )
        }
        val effectiveOrigin =
            origin
                ?: recentSearchEngineId?.let { SearchOrigin(it, elapsedRealtimeMillis) }
        if (effectiveOrigin != null) searchOrigin = effectiveOrigin
        if (currentHost != null && effectiveOrigin != null) {
            return diagnosis(
                action = SearchNavigationAction.GoBack,
                reason = "external-result-restricted",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = effectiveOrigin.engineId,
            )
        }
        return diagnosis(
            action = SearchNavigationAction.Allow,
            reason = "no-search-transition",
            snapshot = snapshot,
            packageCategory = packageCategory,
            engineId = effectiveOrigin?.engineId,
        )
    }

    private fun diagnosis(
        action: SearchNavigationAction,
        reason: String,
        snapshot: PolicySnapshot,
        packageCategory: SearchSurfaceCategory,
        engineId: String?,
    ): SearchEngineScreenDiagnosis =
        SearchEngineScreenDiagnosis(
            action = action,
            reason = reason,
            webNavigationBlocked = snapshot.rules.webNavigationBlocked(),
            externalSearchResultsAllowed = snapshot.rules.externalSearchResultsAllowed(),
            packageCategory = packageCategory.label,
            searchEngineId = engineId,
            policyRevision = snapshot.version,
        )

    private fun String.searchSurfaceCategory(): SearchSurfaceCategory =
        when (this) {
            in BrowserPackageNames -> SearchSurfaceCategory.Browser
            in SearchAppPackageNames -> SearchSurfaceCategory.SearchApp
            else -> SearchSurfaceCategory.NonBrowser
        }

    private data class SearchOrigin(
        val engineId: String,
        val observedAtMillis: Long,
    )

    companion object {
        fun hostFromAddressBarText(value: CharSequence?): String? {
            val raw = value?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (raw.any(Char::isWhitespace) && "://" !in raw) return null
            val candidate = if ("://" in raw) raw else "https://$raw"
            return runCatching { URI(candidate).host }
                .getOrNull()
                ?.lowercase()
                ?.removeSuffix(".")
                ?.removePrefix("www.")
                ?.takeIf { host -> host.contains('.') && host.none(Char::isWhitespace) }
        }

        private const val DefaultSearchSessionWindowMillis = 10 * 60 * 1_000L
        private const val NanosPerMillisecond = 1_000_000L
        private val BrowserPackageNames =
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
        private val SearchAppPackageNames = setOf("com.google.android.googlequicksearchbox")
    }
}

data class SearchEngineScreenDiagnosis(
    val action: SearchNavigationAction,
    val reason: String,
    val webNavigationBlocked: Boolean,
    val externalSearchResultsAllowed: Boolean,
    val packageCategory: String,
    val searchEngineId: String?,
    val policyRevision: Long,
)

enum class SearchNavigationAction {
    Allow,
    GoBack,
    GoHome,
}

private enum class SearchSurfaceCategory(val label: String) {
    Browser("browser"),
    SearchApp("searchApp"),
    NonBrowser("non-browser"),
}
