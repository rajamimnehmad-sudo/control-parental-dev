package com.contentfilter.user.chromedataplane

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChromePhotosRealWebHostPolicyTest {
    @Test
    fun `CONNECT admits normalized dynamic public hostname on port 443`() {
        val authority = authority("example.com" to listOf("93.184.216.34"))

        assertEquals(
            ChromePhotosConnectTarget("example.com", 443),
            authority.admitConnect("CONNECT EXAMPLE.COM:443 HTTP/1.1"),
        )
    }

    @Test
    fun `CONNECT normalizes IDNA without wildcard authority`() {
        val ascii = normalizeDnsHost("bücher.example")
        val authority = authority(ascii to listOf("93.184.216.34"))

        assertEquals(ascii, authority.admitConnect("CONNECT bücher.example:443 HTTP/1.1")?.host)
        assertNull(authority.admitConnect("CONNECT *.example.com:443 HTTP/1.1"))
    }

    @Test
    fun `CONNECT rejects literal malformed and non TLS targets`() {
        val authority = authority("example.com" to listOf("93.184.216.34"))

        assertNull(authority.admitConnect("CONNECT example.com:80 HTTP/1.1"))
        assertNull(authority.admitConnect("CONNECT :443 HTTP/1.1"))
        assertNull(authority.admitConnect("CONNECT 1.1.1.1:443 HTTP/1.1"))
        assertNull(authority.admitConnect("CONNECT [::1]:443 HTTP/1.1"))
        assertNull(authority.admitConnect("GET example.com:443 HTTP/1.1"))
        assertNull(authority.admitConnect("garbage"))
    }

    @Test
    fun `all DNS candidates must be public on every lookup`() {
        var calls = 0
        val authority =
            ChromePublicDestinationAuthority(
                ChromeHostResolver {
                    calls++
                    if (calls == 1) {
                        listOf(InetAddress.getByName("93.184.216.34"))
                    } else {
                        listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.7"))
                    }
                },
            )

        assertEquals(1, authority.resolvePublic("example.com").size)
        assertFailsWith<UnknownHostException> { authority.resolvePublic("example.com") }
        assertEquals(2, calls)
    }

    @Test
    fun `private loopback link local multicast and reserved destinations fail closed`() {
        listOf(
            "10.1.2.3",
            "127.0.0.1",
            "169.254.1.1",
            "224.0.0.1",
            "192.0.2.1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "64:ff9b::a00:7",
            "64:ff9b:1::c0a8:107",
        )
            .forEach { address ->
                val authority = authority("blocked.example" to listOf(address))
                assertFailsWith<UnknownHostException>(address) { authority.resolvePublic("blocked.example") }
            }
    }

    @Test
    fun `public NAT64 candidates retain public destination semantics`() {
        val authority = authority("nat64.example" to listOf("64:ff9b::5db8:d822"))

        assertEquals(1, authority.resolvePublic("nat64.example").size)
    }

    private fun authority(vararg entries: Pair<String, List<String>>): ChromePublicDestinationAuthority {
        val answers = entries.toMap()
        return ChromePublicDestinationAuthority(
            ChromeHostResolver { host ->
                answers[host]?.map(InetAddress::getByName) ?: throw UnknownHostException(host)
            },
        )
    }
}
