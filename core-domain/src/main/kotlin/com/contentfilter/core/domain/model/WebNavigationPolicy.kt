package com.contentfilter.core.domain.model

object WebNavigationPolicy {
    const val RuleTarget = "__web_navigation_blocked__"
    const val ExternalSearchResultsAllowedTarget = "__web_external_search_results_allowed__"
    const val LegacyGoogleResultsAllowedTarget = "__web_google_results_allowed__"
    const val ImagesBlockedTarget = "__web_images_blocked__"
    const val SafeSearchTarget = "__web_safe_search_enabled__"
    const val RulePriority = 5_000

    val GoogleSearchDomains: Set<String> =
        setOf(
            "google.com",
            "google.com.ar",
            "google.com.br",
            "google.com.mx",
            "google.com.co",
            "google.com.uy",
            "google.com.py",
            "google.cl",
            "google.es",
            "google.co.uk",
            "google.co.in",
            "google.ca",
            "search.google.com",
            "gstatic.com",
            "googleapis.com",
            "googleusercontent.com",
        )

    val UnsafeSearchDomains: Set<String> =
        setOf(
            "bing.com",
            "yahoo.com",
            "search.yahoo.com",
            "duckduckgo.com",
        )

    val ImageDomains: Set<String> = WebMediaCatalog.legacyImageRuleTargets

    fun isWebNavigationDomain(domain: String): Boolean {
        val normalized = domain.normalizedHost()
        return WebNavigationDomains.any { normalized.matchesDomainTarget(it) }
    }

    fun isGoogleSearchDomain(domain: String): Boolean {
        val normalized = domain.normalizedHost()
        return GoogleSearchDomains.any { normalized.matchesDomainTarget(it) }
    }

    fun isSearchEngineDomain(domain: String): Boolean = SearchEngineCatalog.isSearchEngineDomain(domain)

    fun isExternalSearchNavigation(
        sourceDomain: String?,
        targetDomain: String,
    ): Boolean =
        SearchEngineCatalog.isSearchEngineDomain(sourceDomain) &&
            !SearchEngineCatalog.isSearchEngineDomain(targetDomain)

    fun isUnsafeSearchDomain(domain: String): Boolean {
        val normalized = domain.normalizedHost()
        return UnsafeSearchDomains.any { normalized.matchesDomainTarget(it) }
    }

    fun isImageDomain(domain: String): Boolean {
        return WebMediaCatalog.isImageAssetHost(domain)
    }

    private val WebNavigationDomains: Set<String> =
        (
            SearchEngineCatalog.searchEngineDomains +
                SearchEngineCatalog.secureDnsDomains
        )
            .toSet()

    private fun String.normalizedHost(): String =
        trim()
            .lowercase()
            .substringBefore("/")
            .substringBefore("?")
            .removeSuffix(".")
            .removePrefix("www.")

    private fun String.matchesDomainTarget(target: String): Boolean = this == target || endsWith(".$target")
}

fun Iterable<PolicyRule>.webNavigationBlocked(): Boolean =
    any {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Block &&
            it.target == WebNavigationPolicy.RuleTarget
    }

fun Iterable<PolicyRule>.externalSearchResultsAllowed(): Boolean {
    val canonicalRules =
        filter {
            it.scope == RuleScope.Domain &&
                it.action == RuleAction.Allow &&
                it.target == WebNavigationPolicy.ExternalSearchResultsAllowedTarget
        }
    if (canonicalRules.any()) return canonicalRules.any { it.enabled }
    return any {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Allow &&
            it.target == WebNavigationPolicy.LegacyGoogleResultsAllowedTarget
    }
}

fun Iterable<PolicyRule>.webImagesBlocked(): Boolean =
    any {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Block &&
            it.target == WebNavigationPolicy.ImagesBlockedTarget
    }

fun Iterable<PolicyRule>.safeSearchEnabled(): Boolean =
    any {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Allow &&
            it.target == WebNavigationPolicy.SafeSearchTarget
    }
