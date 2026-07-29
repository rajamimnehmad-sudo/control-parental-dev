package com.contentfilter.feature.accessibility.service

import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.ProtectedBrowserPolicy
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.onlySearchResultsEnabled
import com.contentfilter.core.domain.model.protectedBrowserRequired
import com.contentfilter.core.domain.model.webNavigationBlocked
import java.net.URI

class SearchEngineScreenDetector(
    private val searchSessionWindowMillis: Long = DefaultSearchSessionWindowMillis,
) {
    private val searchOrigins = mutableMapOf<String, SearchOrigin>()
    private val observedPolicies = mutableMapOf<String, ObservedWebPolicy>()

    fun diagnose(
        packageName: String,
        snapshot: PolicySnapshot,
        currentHost: String?,
        addressBarFocused: Boolean = false,
        recentSearchEngineId: String? = null,
        browserCandidate: Boolean = false,
        elapsedRealtimeMillis: Long = System.nanoTime() / NanosPerMillisecond,
    ): SearchEngineScreenDiagnosis {
        val packageCategory = packageName.searchSurfaceCategory(browserCandidate)
        val webNavigationBlocked = snapshot.rules.webNavigationBlocked()
        val protectedBrowserRequired = snapshot.rules.protectedBrowserRequired()
        val externalResultsAllowed = snapshot.rules.externalSearchResultsAllowed()
        val onlyResultsEnabled = snapshot.rules.onlySearchResultsEnabled()
        val detectedEngine = SearchEngineCatalog.engineForDomain(currentHost)
        val origin =
            searchOrigins[packageName]
                ?.takeIf { elapsedRealtimeMillis - it.observedAtMillis <= searchSessionWindowMillis }
        if (origin == null) searchOrigins.remove(packageName)
        val previousPolicy =
            observedPolicies.put(
                packageName,
                ObservedWebPolicy(
                    webNavigationBlocked = webNavigationBlocked,
                    onlyResultsEnabled = onlyResultsEnabled,
                ),
            )
        val onlyResultsJustActivated =
            !webNavigationBlocked &&
                onlyResultsEnabled &&
                previousPolicy != null &&
                (previousPolicy.webNavigationBlocked || previousPolicy.onlyResultsEnabled.not())

        if (packageCategory == SearchSurfaceCategory.ProtectedBrowser) {
            return diagnosis(
                action =
                    if (webNavigationBlocked) {
                        SearchNavigationAction.GoHome
                    } else {
                        SearchNavigationAction.Allow
                    },
                reason =
                    if (webNavigationBlocked) {
                        "web-navigation-blocked"
                    } else {
                        "protected-browser"
                    },
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        if (packageCategory == SearchSurfaceCategory.NonBrowser) {
            searchOrigins.remove(packageName)
            observedPolicies.remove(packageName)
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "non-browser",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        if (protectedBrowserRequired) {
            searchOrigins.remove(packageName)
            return diagnosis(
                action = SearchNavigationAction.GoHome,
                reason = "dag-browser-required",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id ?: recentSearchEngineId,
            )
        }
        if (webNavigationBlocked) {
            searchOrigins.remove(packageName)
            return diagnosis(
                action = SearchNavigationAction.GoHome,
                reason = "web-navigation-blocked",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id ?: recentSearchEngineId,
            )
        }
        if (detectedEngine != null) {
            searchOrigins[packageName] = SearchOrigin(detectedEngine.id, elapsedRealtimeMillis)
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = if (externalResultsAllowed) "external-results-allowed" else "search-results-visible",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine.id,
            )
        }
        if (externalResultsAllowed || !onlyResultsEnabled) {
            return diagnosis(
                action = SearchNavigationAction.Allow,
                reason = "external-results-allowed",
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = detectedEngine?.id,
            )
        }
        val effectiveOrigin =
            origin
                ?: recentSearchEngineId?.let { SearchOrigin(it, elapsedRealtimeMillis) }
        if (effectiveOrigin != null) searchOrigins[packageName] = effectiveOrigin
        if (currentHost != null && !SearchEngineCatalog.isSearchResultsAllowedDomain(currentHost)) {
            return diagnosis(
                action =
                    when {
                        !onlyResultsJustActivated -> SearchNavigationAction.Allow
                        effectiveOrigin != null -> SearchNavigationAction.GoBack
                        else -> SearchNavigationAction.Allow
                    },
                reason =
                    if (onlyResultsJustActivated) {
                        "existing-external-page-after-search-only-enabled"
                    } else {
                        "external-domain-blocked-by-vpn"
                    },
                snapshot = snapshot,
                packageCategory = packageCategory,
                engineId = effectiveOrigin?.engineId,
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
            protectedBrowserRequired = snapshot.rules.protectedBrowserRequired(),
            packageCategory = packageCategory.label,
            searchEngineId = engineId,
            policyRevision = snapshot.version,
        )

    private fun String.searchSurfaceCategory(browserCandidate: Boolean): SearchSurfaceCategory =
        when (this) {
            in ProtectedBrowserPolicy.ProtectedBrowserPackages -> SearchSurfaceCategory.ProtectedBrowser
            in ProtectedBrowserPolicy.KnownAlternativeBrowserPackages -> SearchSurfaceCategory.Browser
            in ProtectedBrowserPolicy.SearchAppPackages -> SearchSurfaceCategory.SearchApp
            else -> if (browserCandidate) SearchSurfaceCategory.Browser else SearchSurfaceCategory.NonBrowser
        }

    private data class SearchOrigin(
        val engineId: String,
        val observedAtMillis: Long,
    )

    private data class ObservedWebPolicy(
        val webNavigationBlocked: Boolean,
        val onlyResultsEnabled: Boolean,
    )

    companion object {
        fun isBrowserPackage(packageName: String): Boolean =
            packageName in ProtectedBrowserPolicy.KnownAlternativeBrowserPackages

        fun addressObservationFromAddressBarText(value: CharSequence?): BrowserAddressObservation? {
            val raw = value?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (raw.any(Char::isWhitespace) && "://" !in raw) return null
            val candidate = if ("://" in raw) raw else "https://$raw"
            val uri =
                runCatching { URI(candidate) }.getOrNull()
                    ?: runCatching { URI(candidate.replace(" ", "%20")) }.getOrNull()
                    ?: return null
            val host =
                uri.host
                    ?.lowercase()
                    ?.removeSuffix(".")
                    ?.removePrefix("www.")
                    ?.takeIf { it.contains('.') && it.none(Char::isWhitespace) }
                    ?: return null
            return BrowserAddressObservation(host = host)
        }

        fun hostFromAddressBarText(value: CharSequence?): String? {
            return addressObservationFromAddressBarText(value)?.host
        }

        private const val DefaultSearchSessionWindowMillis = 10 * 60 * 1_000L
        private const val NanosPerMillisecond = 1_000_000L
    }
}

data class SearchEngineScreenDiagnosis(
    val action: SearchNavigationAction,
    val reason: String,
    val webNavigationBlocked: Boolean,
    val externalSearchResultsAllowed: Boolean,
    val protectedBrowserRequired: Boolean,
    val packageCategory: String,
    val searchEngineId: String?,
    val policyRevision: Long,
)

data class BrowserAddressObservation(val host: String)

enum class SearchNavigationAction {
    Allow,
    GoBack,
    GoHome,
}

private enum class SearchSurfaceCategory(val label: String) {
    Browser("browser"),
    ProtectedBrowser("protectedBrowser"),
    SearchApp("searchApp"),
    NonBrowser("non-browser"),
}
