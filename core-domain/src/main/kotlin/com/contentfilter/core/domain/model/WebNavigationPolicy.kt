package com.contentfilter.core.domain.model

object WebNavigationPolicy {
    const val RuleTarget = "__web_navigation_blocked__"
    const val RulePriority = 5_000

    fun isWebNavigationDomain(domain: String): Boolean {
        val normalized = domain.normalizedHost()
        return WebNavigationDomains.any { normalized.matchesDomainTarget(it) }
    }

    private val WebNavigationDomains: Set<String> =
        (SearchEngineCatalog.searchEngineDomains +
            SearchEngineCatalog.secureDnsDomains)
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
