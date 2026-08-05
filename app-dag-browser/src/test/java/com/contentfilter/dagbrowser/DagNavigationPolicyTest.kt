package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagNavigationPolicyTest {
    @Test
    fun `plain words become a strict Google search`() {
        assertEquals(
            "https://www.google.com/search?safe=active&q=ropa+formal",
            DagNavigationPolicy.fromUserInput("ropa formal"),
        )
    }

    @Test
    fun `host becomes HTTPS`() {
        assertEquals(
            "https://fravega.com",
            DagNavigationPolicy.fromUserInput("fravega.com"),
        )
    }

    @Test
    fun `HTTP input is upgraded to HTTPS`() {
        assertEquals(
            "https://example.com/catalogo",
            DagNavigationPolicy.fromUserInput("http://example.com/catalogo"),
        )
    }

    @Test
    fun `Google Search receives strict SafeSearch`() {
        assertEquals(
            "https://www.google.com/search?q=camisas&safe=active",
            DagNavigationPolicy.sanitizeTopLevel("https://www.google.com/search?q=camisas"),
        )
    }

    @Test
    fun `existing strict SafeSearch is not duplicated`() {
        val url = "https://www.google.com.ar/search?q=camisas&safe=active"
        assertEquals(url, DagNavigationPolicy.sanitizeTopLevel(url))
    }

    @Test
    fun `unsafe Google preference is replaced rather than duplicated`() {
        assertEquals(
            "https://www.google.com/search?q=camisas&safe=active",
            DagNavigationPolicy.sanitizeTopLevel("https://www.google.com/search?q=camisas&safe=off"),
        )
    }

    @Test
    fun `non HTTPS top level navigation fails closed`() {
        assertNull(DagNavigationPolicy.sanitizeTopLevel("http://example.com"))
        assertNull(DagNavigationPolicy.sanitizeTopLevel("javascript:alert(1)"))
        assertNull(DagNavigationPolicy.sanitizeTopLevel("intent://example.com"))
    }

    @Test
    fun `empty input has no navigation`() {
        assertNull(DagNavigationPolicy.fromUserInput("   "))
    }

    @Test
    fun `ordinary HTTPS page is preserved`() {
        val url = "https://cheeky.com.ar/coleccion"
        assertTrue(DagNavigationPolicy.sanitizeTopLevel(url) === url)
    }

    @Test
    fun `HTTP fixture is limited to the isolated lab flavor`() {
        val url = "http://localhost:8765/fixture/"
        val loopbackUrl = "http://127.0.0.1:8765/fixture/"
        if (BuildConfig.GLOSHIA_LAB_FIXTURE) {
            assertEquals(url, DagNavigationPolicy.sanitizeTopLevel(url))
            assertEquals(loopbackUrl, DagNavigationPolicy.sanitizeTopLevel(loopbackUrl))
        } else {
            assertNull(DagNavigationPolicy.sanitizeTopLevel(url))
            assertNull(DagNavigationPolicy.sanitizeTopLevel(loopbackUrl))
        }
    }

    @Test
    fun `new window is redirected into the protected session`() {
        assertEquals(
            DagLoadDecision.Redirect("https://example.com/new"),
            DagNavigationPolicy.decideLoad("https://example.com/new", opensNewWindow = true),
        )
    }

    @Test
    fun `unsafe new window is blocked`() {
        assertIs<DagLoadDecision.BlockExternalApp>(
            DagNavigationPolicy.decideLoad("intent://external-app", opensNewWindow = true),
        )
    }

    @Test
    fun `intent link exposes only a valid encoded HTTPS fallback`() {
        assertEquals(
            "https://www.instagram.com/",
            DagNavigationPolicy.httpsFallbackFromExternalLink(
                "intent://profile/#Intent;scheme=instagram;" +
                    "S.browser_fallback_url=https%3A%2F%2Fwww.instagram.com%2F;end",
            ),
        )
        assertNull(
            DagNavigationPolicy.httpsFallbackFromExternalLink(
                "intent://profile/#Intent;scheme=instagram;" +
                    "S.browser_fallback_url=javascript%3Aalert%281%29;end",
            ),
        )
    }

    @Test
    fun `HTTPS intent can fall back to the same protected web destination`() {
        assertEquals(
            "https://www.instagram.com/accounts/login/",
            DagNavigationPolicy.httpsFallbackFromExternalLink(
                "intent://www.instagram.com/accounts/login/#Intent;scheme=https;" +
                    "package=com.instagram.android;end",
            ),
        )
    }

    @Test
    fun `ordinary same-window request is allowed`() {
        assertIs<DagLoadDecision.Allow>(
            DagNavigationPolicy.decideLoad("https://example.com", opensNewWindow = false),
        )
    }

    @Test
    fun `internal home is allowed only in the current tab`() {
        assertIs<DagLoadDecision.Allow>(
            DagNavigationPolicy.decideLoad("about:blank", opensNewWindow = false),
        )
        assertIs<DagLoadDecision.Block>(
            DagNavigationPolicy.decideLoad("about:blank", opensNewWindow = true),
        )
    }
}
