package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldCspPolicyTest {
    private val policy = ChromeMediaShieldCspPolicy()

    @Test
    fun `self shield admits same-origin capability channel without fixed fixture origin`() {
        val rewritten =
            ChromeMediaShieldCspPolicy(sameOriginReady = true).admitBootstrap(
                "default-src 'none'; connect-src 'none'",
                ScriptNonce,
                StyleNonce,
            )

        assertTrue(rewritten.contains("connect-src 'self'"))
        assertFalse(rewritten.contains("https://glosh-photos.test"))
    }

    @Test
    fun `absent site policy receives a separate bounded media envelope`() {
        val output = policy.apply(emptyList(), ScriptNonce, StyleNonce)
        val policies = output.values("Content-Security-Policy")

        assertEquals(1, policies.size)
        assertEquals(
            "img-src https: http: https://glosh-ui-svg.test; media-src https: http:; object-src 'none'; worker-src https: http:; " +
                "frame-src https: http:; child-src https: http:; fenced-frame-src 'none'",
            policies.single(),
        )
        assertTrue(policies.single().contains("media-src https: http:"))
        assertFalse(policies.single().contains("media-src 'none'"))
        assertEquals("no-store", output.value("Cache-Control"))
        assertEquals("nosniff", output.value("X-Content-Type-Options"))
        assertEquals("text/html; charset=utf-8", output.value("Content-Type"))
    }

    @Test
    fun `each effective site policy preserves restrictions and admits only exact nonces`() {
        val output =
            policy.apply(
                sourceHeaders =
                    listOf(
                        ChromeHttpHeader("Content-Security-Policy", "default-src 'self'; object-src 'none'"),
                        ChromeHttpHeader(
                            "Content-Security-Policy",
                            "script-src-elem https://scripts.example; style-src-elem https://css.example; form-action 'self'",
                        ),
                    ),
                scriptNonce = ScriptNonce,
                styleNonce = StyleNonce,
            )
        val policies = output.values("Content-Security-Policy")

        assertEquals(3, policies.size)
        assertTrue(policies[0].contains("default-src 'self'"))
        assertTrue(policies[0].contains("object-src 'none'"))
        assertTrue(policies[0].contains("script-src 'self' 'nonce-$ScriptNonce'"))
        assertTrue(policies[0].contains("style-src 'self' 'nonce-$StyleNonce'"))
        assertTrue(policies[0].contains("connect-src 'self' https://glosh-photos.test"))
        assertTrue(policies[1].contains("script-src-elem https://scripts.example 'nonce-$ScriptNonce'"))
        assertTrue(policies[1].contains("style-src-elem https://css.example 'nonce-$StyleNonce'"))
        assertFalse(policies[1].contains("connect-src"))
        assertTrue(policies[1].contains("form-action 'self'"))
        assertFalse(policies.any { it.contains("unsafe-inline") || it.contains("unsafe-eval") || it.contains("*") })
    }

    @Test
    fun `unrestricted script style and connect remain unrestricted when site omits those directives`() {
        val rewritten =
            policy.admitBootstrap(
                "img-src https://images.example; object-src 'none'",
                ScriptNonce,
                StyleNonce,
            )

        assertEquals(
            "img-src https://images.example https://glosh-ui-svg.test; object-src 'none'",
            rewritten,
        )
    }

    @Test
    fun `none is removed only from the directive that receives a nonce`() {
        val rewritten =
            policy.admitBootstrap(
                "default-src 'none'; object-src 'none'; frame-ancestors 'none'",
                ScriptNonce,
                StyleNonce,
            )

        assertTrue(rewritten.contains("script-src 'nonce-$ScriptNonce'"))
        assertTrue(rewritten.contains("style-src 'nonce-$StyleNonce'"))
        assertTrue(rewritten.contains("connect-src https://glosh-photos.test"))
        assertTrue(rewritten.contains("object-src 'none'"))
        assertTrue(rewritten.contains("frame-ancestors 'none'"))
    }

    @Test
    fun `site unrestricted inline semantics remain unchanged by bootstrap admission`() {
        val rewritten =
            policy.admitBootstrap(
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline' https://css.example",
                ScriptNonce,
                StyleNonce,
            )

        assertTrue(rewritten.contains("script-src 'self' 'unsafe-inline'"))
        assertTrue(rewritten.contains("style-src 'unsafe-inline' https://css.example"))
        assertFalse(rewritten.contains("'nonce-$ScriptNonce'"))
        assertFalse(rewritten.contains("'nonce-$StyleNonce'"))
    }

    @Test
    fun `site inline hash semantics still receive exact bootstrap nonce`() {
        val rewritten =
            policy.admitBootstrap(
                "script-src 'unsafe-inline' 'sha256-siteHash'; style-src 'unsafe-inline' 'nonce-siteNonce'",
                ScriptNonce,
                StyleNonce,
            )

        assertTrue(rewritten.contains("script-src 'unsafe-inline' 'sha256-siteHash' 'nonce-$ScriptNonce'"))
        assertTrue(rewritten.contains("style-src 'unsafe-inline' 'nonce-siteNonce' 'nonce-$StyleNonce'"))
    }

    @Test
    fun `comma combined effective policies each receive exact boot nonces`() {
        val output =
            policy.apply(
                sourceHeaders =
                    listOf(
                        ChromeHttpHeader(
                            "Content-Security-Policy",
                            "default-src 'self'; object-src 'none', script-src https://scripts.example; style-src 'self'",
                        ),
                    ),
                scriptNonce = ScriptNonce,
                styleNonce = StyleNonce,
            )
        val policies = output.values("Content-Security-Policy")

        assertEquals(3, policies.size)
        assertTrue(policies[0].contains("script-src 'self' 'nonce-$ScriptNonce'"))
        assertTrue(policies[0].contains("style-src 'self' 'nonce-$StyleNonce'"))
        assertTrue(policies[1].contains("script-src https://scripts.example 'nonce-$ScriptNonce'"))
        assertTrue(policies[1].contains("style-src 'self' 'nonce-$StyleNonce'"))
        assertTrue(policies[0].contains("connect-src 'self' https://glosh-photos.test"))
        assertFalse(policies[1].contains("connect-src"))
        assertTrue(policies.take(2).all { !it.contains(',') })
    }

    @Test
    fun `meta CSP rewrite preserves policy and admits only fixed ready origin`() {
        val rewritten = policy.rewriteMetaPolicy("default-src 'self'; connect-src 'none'; object-src 'none'")

        assertEquals(
            "default-src 'self'; connect-src https://glosh-photos.test; object-src 'none'; " +
                "img-src 'self' https://glosh-ui-svg.test",
            rewritten,
        )
        assertEquals(null, policy.rewriteMetaPolicy("default-src &apos;self&apos;"))
    }

    @Test
    fun `every stale entity header is removed after transformation`() {
        val staleNames =
            listOf(
                "Content-Length",
                "Content-Encoding",
                "ETag",
                "Last-Modified",
                "Digest",
                "Content-MD5",
                "Content-Range",
                "Accept-Ranges",
                "Vary",
                "Cache-Control",
                "Expires",
            )
        val output =
            policy.apply(
                staleNames.map { ChromeHttpHeader(it, "stale") } + ChromeHttpHeader("X-Site", "preserved"),
                ScriptNonce,
                StyleNonce,
            )

        staleNames.forEach { stale ->
            assertFalse(output.any { it.name.equals(stale, ignoreCase = true) && it.value == "stale" })
        }
        assertEquals("preserved", output.value("X-Site"))
    }

    @Test
    fun `report-only policy is removed so bootstrap source and capability cannot be sampled`() {
        val output =
            policy.apply(
                sourceHeaders =
                    listOf(
                        ChromeHttpHeader(
                            "Content-Security-Policy-Report-Only",
                            "script-src 'none' 'report-sample'; report-uri https://report.example",
                        ),
                    ),
                scriptNonce = ScriptNonce,
                styleNonce = StyleNonce,
            )

        assertFalse(output.any { it.name.equals("Content-Security-Policy-Report-Only", true) })
    }

    @Test
    fun `document headers remove alt svc and canonicalize hostile nosniff duplicates`() {
        val output =
            policy.apply(
                sourceHeaders =
                    listOf(
                        ChromeHttpHeader("Alt-Svc", "h3=\":443\"; ma=86400"),
                        ChromeHttpHeader("X-Content-Type-Options", "sniff"),
                        ChromeHttpHeader("x-content-type-options", "nosniff, hostile"),
                    ),
                scriptNonce = ScriptNonce,
                styleNonce = StyleNonce,
            )

        assertFalse(output.any { it.name.equals("Alt-Svc", ignoreCase = true) })
        assertEquals(listOf("nosniff"), output.values("X-Content-Type-Options"))
    }

    private fun List<ChromeHttpHeader>.values(name: String): List<String> =
        filter { it.name.equals(name, ignoreCase = true) }.map(ChromeHttpHeader::value)

    private fun List<ChromeHttpHeader>.value(name: String): String? =
        firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val ScriptNonce = "scriptNonce123456789012"
        const val StyleNonce = "styleNonce1234567890123"
    }
}
