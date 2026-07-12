package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchEngineCatalogTest {
    @Test
    fun `search engine domains are normalized and unique`() {
        val domains = SearchEngineCatalog.searchEngineDomains

        assertEquals(domains.distinct(), domains)
        assertTrue(domains.all { it == it.lowercase() && !it.startsWith("www.") && !it.endsWith(".") })
    }

    @Test
    fun `catalog includes regional Google and major search engines`() {
        assertTrue("google.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("google.com.ar" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("search.google.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("clients4.google.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("clientservices.googleapis.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("bing.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("search.yahoo.com" in SearchEngineCatalog.searchEngineDomains)
        assertTrue("duckduckgo.com" in SearchEngineCatalog.searchEngineDomains)
    }

    @Test
    fun `catalog identifies each supported search engine without treating support hosts as result pages`() {
        assertEquals("google", SearchEngineCatalog.engineForDomain("www.google.com.ar")?.id)
        assertEquals("bing", SearchEngineCatalog.engineForDomain("www.bing.com")?.id)
        assertEquals("yahoo", SearchEngineCatalog.engineForDomain("search.yahoo.com")?.id)
        assertEquals("duckduckgo", SearchEngineCatalog.engineForDomain("duckduckgo.com")?.id)
        assertNull(SearchEngineCatalog.engineForDomain("gstatic.com"))
        assertNull(SearchEngineCatalog.engineForDomain("images.google.com"))
        assertNull(SearchEngineCatalog.engineForDomain("example.com"))
    }

    @Test
    fun `safe search DNS mappings exist only for compatible engines`() {
        assertEquals("forcesafesearch.google.com", SearchEngineCatalog.safeSearchDnsTarget("google.com"))
        assertEquals("strict.bing.com", SearchEngineCatalog.safeSearchDnsTarget("bing.com"))
        assertEquals("safe.duckduckgo.com", SearchEngineCatalog.safeSearchDnsTarget("duckduckgo.com"))
        assertNull(SearchEngineCatalog.safeSearchDnsTarget("search.yahoo.com"))
        assertNull(SearchEngineCatalog.safeSearchDnsTarget("forcesafesearch.google.com"))
        assertNull(SearchEngineCatalog.safeSearchDnsTarget("strict.bing.com"))
        assertNull(SearchEngineCatalog.safeSearchDnsTarget("safe.duckduckgo.com"))
    }

    @Test
    fun `catalog includes secure DNS providers for blocking phase`() {
        assertTrue("dns.google" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("chrome.cloudflare-dns.com" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("dns.quad9.net" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("doh.cleanbrowsing.org" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("doh.opendns.com" in SearchEngineCatalog.secureDnsDomains)
        assertTrue(SearchEngineCatalog.isSecureDnsProviderDomain("dns10.quad9.net"))
        assertTrue(SearchEngineCatalog.isSecureDnsProviderDomain("www.dns.google"))
        assertFalse(SearchEngineCatalog.isSecureDnsProviderDomain("google.com"))
    }

    @Test
    fun `catalog includes IPv4 and IPv6 resolver routes used by Chromium providers`() {
        assertTrue("8.8.8.8" in SearchEngineCatalog.encryptedDnsResolverAddresses)
        assertTrue("1.1.1.1" in SearchEngineCatalog.encryptedDnsResolverAddresses)
        assertTrue("9.9.9.9" in SearchEngineCatalog.encryptedDnsResolverAddresses)
        assertTrue("2001:4860:4860::8888" in SearchEngineCatalog.encryptedDnsResolverAddresses)
        assertTrue("2606:4700:4700::1111" in SearchEngineCatalog.encryptedDnsResolverAddresses)
    }
}
