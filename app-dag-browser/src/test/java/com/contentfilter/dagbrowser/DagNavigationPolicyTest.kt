package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
