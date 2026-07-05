package com.contentfilter.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `catalog includes secure DNS providers for blocking phase`() {
        assertTrue("dns.google" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("cloudflare-dns.com" in SearchEngineCatalog.secureDnsDomains)
        assertTrue("dns.quad9.net" in SearchEngineCatalog.secureDnsDomains)
    }
}
