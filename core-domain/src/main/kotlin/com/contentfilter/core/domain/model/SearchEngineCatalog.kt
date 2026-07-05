package com.contentfilter.core.domain.model

object SearchEngineCatalog {
    val searchEngineDomains: List<String> =
        listOf(
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
            "clients1.google.com",
            "clients2.google.com",
            "clients4.google.com",
            "clientservices.googleapis.com",
            "bing.com",
            "yahoo.com",
            "search.yahoo.com",
            "duckduckgo.com",
        )

    val searchSupportDomains: Set<String> =
        setOf(
            "gstatic.com",
            "googleapis.com",
            "googleusercontent.com",
            "yimg.com",
            "bing.net",
            "duck.com",
        )

    val secureDnsDomains: List<String> =
        listOf(
            "dns.google",
            "cloudflare-dns.com",
            "dns.quad9.net",
            "mozilla.cloudflare-dns.com",
            "security.cloudflare-dns.com",
            "family.cloudflare-dns.com",
        )
}
