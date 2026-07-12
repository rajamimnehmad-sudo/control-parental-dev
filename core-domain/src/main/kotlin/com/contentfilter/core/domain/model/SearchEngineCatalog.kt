package com.contentfilter.core.domain.model

data class SearchEngineDefinition(
    val id: String,
    val domains: Set<String>,
    val supportDomains: Set<String> = emptySet(),
    val safeSearchDnsTarget: String? = null,
)

data class EncryptedDnsProviderDefinition(
    val id: String,
    val domains: Set<String>,
    val resolverAddresses: Set<String>,
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
            "images.google.com",
            "images.search.yahoo.com",
            "video.search.yahoo.com",
            "ogs.google.com",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "ssl.gstatic.com",
        )

    private val searchResultsTechnicalDomains =
        searchNavigationSupportDomains +
            setOf(
                "gstatic.com",
                "googleusercontent.com",
                "bing.net",
                "yimg.com",
                "duck.com",
            )

    val searchEngineDomains: List<String> =
        (engines.flatMap { it.domains } + searchNavigationSupportDomains).distinct()

    val searchSupportDomains: Set<String> = engines.flatMapTo(linkedSetOf()) { it.supportDomains }

    val safeSearchDnsTargets: Set<String> = engines.mapNotNullTo(linkedSetOf()) { it.safeSearchDnsTarget }

    val encryptedDnsProviders: List<EncryptedDnsProviderDefinition> =
        listOf(
            EncryptedDnsProviderDefinition(
                id = "google",
                domains = setOf("dns.google", "dns.google.com", "8888.google"),
                resolverAddresses =
                    setOf(
                        "8.8.8.8",
                        "8.8.4.4",
                        "2001:4860:4860::8888",
                        "2001:4860:4860::8844",
                    ),
            ),
            EncryptedDnsProviderDefinition(
                id = "cloudflare",
                domains =
                    setOf(
                        "chrome.cloudflare-dns.com",
                        "cloudflare-dns.com",
                        "one.one.one.one",
                        "1dot1dot1dot1.cloudflare-dns.com",
                        "mozilla.cloudflare-dns.com",
                        "security.cloudflare-dns.com",
                        "family.cloudflare-dns.com",
                    ),
                resolverAddresses =
                    setOf(
                        "1.1.1.1",
                        "1.0.0.1",
                        "1.1.1.2",
                        "1.0.0.2",
                        "1.1.1.3",
                        "1.0.0.3",
                        "2606:4700:4700::1111",
                        "2606:4700:4700::1001",
                        "2606:4700:4700::1112",
                        "2606:4700:4700::1002",
                        "2606:4700:4700::1113",
                        "2606:4700:4700::1003",
                    ),
            ),
            EncryptedDnsProviderDefinition(
                id = "cleanbrowsing",
                domains =
                    setOf(
                        "doh.cleanbrowsing.org",
                        "family-filter-dns.cleanbrowsing.org",
                        "adult-filter-dns.cleanbrowsing.org",
                        "security-filter-dns.cleanbrowsing.org",
                    ),
                resolverAddresses =
                    setOf(
                        "185.228.168.9",
                        "185.228.169.9",
                        "185.228.168.10",
                        "185.228.169.11",
                        "185.228.168.168",
                        "185.228.169.168",
                        "2a0d:2a00:1::2",
                        "2a0d:2a00:2::2",
                        "2a0d:2a00:1::1",
                        "2a0d:2a00:2::1",
                        "2a0d:2a00:1::",
                        "2a0d:2a00:2::",
                    ),
            ),
            EncryptedDnsProviderDefinition(
                id = "opendns",
                domains = setOf("doh.opendns.com", "doh.familyshield.opendns.com"),
                resolverAddresses =
                    setOf(
                        "208.67.222.222",
                        "208.67.220.220",
                        "208.67.222.123",
                        "208.67.220.123",
                        "2620:119:35::35",
                        "2620:119:53::53",
                        "2620:119:35::123",
                        "2620:119:53::123",
                    ),
            ),
            EncryptedDnsProviderDefinition(
                id = "quad9",
                domains = setOf("dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net"),
                resolverAddresses =
                    setOf(
                        "9.9.9.9",
                        "149.112.112.112",
                        "9.9.9.10",
                        "149.112.112.10",
                        "9.9.9.11",
                        "149.112.112.11",
                        "2620:fe::fe",
                        "2620:fe::9",
                        "2620:fe::10",
                        "2620:fe::fe:10",
                        "2620:fe::11",
                        "2620:fe::fe:11",
                    ),
            ),
        )

    val secureDnsDomains: List<String> = encryptedDnsProviders.flatMap { it.domains }.distinct()

    val encryptedDnsResolverAddresses: Set<String> =
        encryptedDnsProviders.flatMapTo(linkedSetOf()) { it.resolverAddresses }

    fun engineForDomain(domain: String?): SearchEngineDefinition? {
        val normalized = domain.normalizedSearchHost() ?: return null
        return engines.firstOrNull { engine ->
            normalized in engine.domains
        }
    }

    fun isSearchEngineDomain(domain: String?): Boolean = engineForDomain(domain) != null

    fun isSearchResultsAllowedDomain(domain: String?): Boolean {
        val normalized = domain.normalizedSearchHost() ?: return false
        return isSearchEngineDomain(normalized) ||
            searchSupportDomains.any { normalized.matchesSearchTarget(it) } ||
            searchResultsTechnicalDomains.any { normalized.matchesSearchTarget(it) } ||
            safeSearchDnsTargets.any { normalized.matchesSearchTarget(it) }
    }

    fun isSecureDnsProviderDomain(domain: String?): Boolean {
        val normalized = domain.normalizedSearchHost() ?: return false
        return secureDnsDomains.any { normalized == it || normalized.endsWith(".$it") }
    }

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

    private fun String.matchesSearchTarget(target: String): Boolean = this == target || endsWith(".$target")
}
