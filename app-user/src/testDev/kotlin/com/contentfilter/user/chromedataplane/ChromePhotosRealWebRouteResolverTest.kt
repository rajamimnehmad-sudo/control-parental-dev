package com.contentfilter.user.chromedataplane

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChromePhotosRealWebRouteResolverTest {
    @Test
    fun `resolver keeps all public A and AAAA results for exact authorized hosts`() {
        val answers =
            mapOf(
                "one.example" to arrayOf(address("203.0.113.10"), address("2001:db8::10")),
                "two.example" to arrayOf(address("203.0.113.20")),
            )
        val resolver = ChromePhotosRealWebRouteResolver(lookup = answers::getValue)

        assertEquals(
            setOf("203.0.113.10", "2001:db8:0:0:0:0:0:10", "203.0.113.20"),
            resolver.resolve(answers.keys),
        )
    }

    @Test
    fun `resolver fails closed for private missing or excessive route scope`() {
        val privateOnly = ChromePhotosRealWebRouteResolver(lookup = { arrayOf(address("192.168.1.2")) })
        val bounded =
            ChromePhotosRealWebRouteResolver(
                lookup = { arrayOf(address("203.0.113.1"), address("203.0.113.2")) },
                maximumAddresses = 1,
            )

        assertFailsWith<IllegalStateException> { privateOnly.resolve(listOf("private.example")) }
        assertFailsWith<IllegalStateException> { bounded.resolve(listOf("public.example")) }
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
