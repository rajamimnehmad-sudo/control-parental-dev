package com.contentfilter.core.network.security

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicNetworkDestinationGuardTest {
    private val guard = PublicNetworkDestinationGuard()

    @Test
    fun `private reserved documentation and special literals fail closed`() {
        listOf(
            "https://127.0.0.1/",
            "https://10.0.0.1/",
            "https://100.64.0.1/",
            "https://169.254.1.1/",
            "https://172.16.0.1/",
            "https://192.0.2.10/",
            "https://192.168.1.1/",
            "https://198.18.0.1/",
            "https://198.51.100.2/",
            "https://203.0.113.2/",
            "https://224.0.0.1/",
            "https://240.0.0.1/",
            "https://127.1/",
            "https://0177.0.0.1/",
            "https://2130706433/",
            "https://localhost/",
            "https://service.internal/",
            "https://device.local/",
            "https://fixture.test/",
            "https://hidden.onion/",
            "https://[::1]/",
            "https://[fc00::1]/",
            "https://[fe80::1]/",
            "https://[2001:db8::1]/",
            "https://[64:ff9b::c0a8:101]/",
            "https://[100::1]/",
            "https://[2001:20::1]/",
            "https://[2002:c000:0201::1]/",
            "https://[3fff::1]/",
            "https://[5f00::1]/",
            "https://[4000::1]/",
            "https://[ff02::1]/",
        ).forEach { url ->
            assertEquals(PublicDestinationDecision.Block, guard.validateImmediate(url).decision, url)
        }
    }

    @Test
    fun `public literals pass and hostnames require asynchronous dns validation`() {
        assertEquals(PublicDestinationDecision.Allow, guard.validateImmediate("https://8.8.8.8/").decision)
        assertEquals(
            PublicDestinationDecision.Allow,
            guard.validateImmediate("https://[2606:4700:4700::1111]/").decision,
        )
        assertEquals(
            PublicDestinationDecision.NeedsDnsValidation,
            guard.validateImmediate("https://example.com/").decision,
        )
    }

    @Test
    fun `address predicate covers ipv4 and ipv6 special ranges`() {
        assertTrue(PublicNetworkDestinationGuard.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertFalse(PublicNetworkDestinationGuard.isPublicAddress(InetAddress.getByName("192.168.0.1")))
        assertFalse(PublicNetworkDestinationGuard.isPublicAddress(InetAddress.getByName("2001:db8::1")))
    }

    @Test
    fun `non https and malformed destinations fail immediately`() {
        assertEquals(PublicDestinationDecision.Block, guard.validateImmediate("http://example.com").decision)
        assertEquals(PublicDestinationDecision.Block, guard.validateImmediate("not a url").decision)
        assertEquals(PublicDestinationDecision.Block, guard.validateImmediate("https://999.999.999.999/").decision)
    }
}
