package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromePhotosRealWebHostPolicyTest {
    private val allowlist = ChromePhotosHostAllowlist(ChromePhotosRealWebLabConfig.allowedHosts)

    @Test
    fun `CONNECT allows only exact normalized lab hosts on port 443`() {
        assertEquals(
            ChromePhotosConnectTarget(ChromePhotosRealWebLabConfig.HttpBingoHost, 443),
            ChromePhotosConnectTarget.parse(
                "CONNECT HTTPBINGO.ORG:443 HTTP/1.1",
                allowlist,
            ),
        )
        assertTrue(allowlist.isAllowed(ChromePhotosRealWebLabConfig.GoogleStaticHost))
        assertTrue(allowlist.isAllowed(ChromePhotosRealWebLabConfig.FlickrStaticHost))
        assertFalse(allowlist.isAllowed("sub.${ChromePhotosRealWebLabConfig.GoogleStaticHost}"))
        assertFalse(allowlist.isAllowed("staticflickr.com"))
    }

    @Test
    fun `CONNECT fails closed for non allowlisted malformed non TLS and literal targets`() {
        assertNull(ChromePhotosConnectTarget.parse("CONNECT example.com:443 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("CONNECT httpbingo.org:80 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("CONNECT :443 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("CONNECT 1.1.1.1:443 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("CONNECT [::1]:443 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("GET httpbingo.org:443 HTTP/1.1", allowlist))
        assertNull(ChromePhotosConnectTarget.parse("garbage", allowlist))
    }
}
