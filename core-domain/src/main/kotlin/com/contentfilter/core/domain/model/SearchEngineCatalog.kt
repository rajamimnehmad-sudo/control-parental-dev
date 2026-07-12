package com.contentfilter.core.domain.model

data class SearchEngineDefinition(
    val id: String,
    val domains: Set<String>,
    val supportDomains: Set<String> = emptySet(),
    val safeSearchDnsTarget: String? = null,
)

object SearchEngineCatalog {
    private val googleDomains =
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
        )

    val engines: List<SearchEngineDefinition> =
        listOf(
            SearchEngineDefinition(
                id = "google",
                domains = googleDomains,
                supportDomains =
                    setOf(
                        "clients1.google.com",
                        "clients2.google.com",
                        "clients4.google.com",
                        "clientservices.googleapis.com",
                        "gstatic.com",
                        "googleapis.com",
                        "googleusercontent.com",
                    ),
                safeSearchDnsTarget = "forcesafesearch.google.com",
            ),
            SearchEngineDefinition(
                id = "bing",
                domains = setOf("bing.com"),
                supportDomains = setOf("bing.net"),
                safeSearchDnsTarget = "strict.bing.com",
            ),
            SearchEngineDefinition(
                id = "yahoo",
                domains = setOf("yahoo.com", "search.yahoo.com"),
                supportDomains = setOf("yimg.com"),
            ),
            SearchEngineDefinition(
                id = "duckduckgo",
                domains = setOf("duckduckgo.com"),
                supportDomains = setOf("duck.com"),
                safeSearchDnsTarget = "safe.duckduckgo.com",
            ),
        )

    private val searchNavigationSupportDomains =
        listOf(
            "clients1.google.com",
            "clients2.google.com",
            "clients4.google.com",
            "clientservices.googleapis.com",
        )

    val searchEngineDomains: List<String> =
        (engines.flatMap { it.domains } + searchNavigationSupportDomains).distinct()

    val searchSupportDomains: Set<String> = engines.flatMapTo(linkedSetOf()) { it.supportDomains }

    val secureDnsDomains: List<String> =
        listOf(
            "dns.google",
            "cloudflare-dns.com",
            "dns.quad9.net",
            "mozilla.cloudflare-dns.com",
            "security.cloudflare-dns.com",
            "family.cloudflare-dns.com",
        )

    fun engineForDomain(domain: String?): SearchEngineDefinition? {
        val normalized = domain.normalizedSearchHost() ?: return null
        return engines.firstOrNull { engine ->
            normalized in engine.domains
        }
    }

    fun isSearchEngineDomain(domain: String?): Boolean = engineForDomain(domain) != null

    fun safeSearchDnsTarget(domain: String): String? {
        val normalized = domain.normalizedSearchHost() ?: return null
        return engineForDomain(normalized)
            ?.safeSearchDnsTarget
            ?.takeUnless { it == normalized }
    }

    private fun String?.normalizedSearchHost(): String? =
        this
            ?.trim()
            ?.lowercase()
            ?.substringBefore("/")
            ?.substringBefore("?")
            ?.removeSuffix(".")
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
}
