package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromePhotosFixtureOriginTest {
    @Test
    fun `fixture pages publish visible-only lease heartbeat`() {
        val script = ChromePhotosFixtureLeaseContract.script

        assertTrue(ChromePhotosFixtureLeaseContract.ScriptTag.contains("/fixture-lease.js"))
        assertTrue(script.contains("document.visibilityState === 'visible'"))
        assertTrue(script.contains("/__glosh_lease"))
    }

    @Test
    fun `lease heartbeat path match is exact and query tolerant`() {
        assertTrue(ChromePhotosFixtureLeaseContract.isHeartbeatTarget("/__glosh_lease?nonce=ignored"))
        assertTrue(!ChromePhotosFixtureLeaseContract.isHeartbeatTarget("/other"))
    }

    @Test
    fun `fixture publishes bounded public GloshIA matrix and one repeat`() {
        val html = chromePhotosGloshiaPublicMatrixCards()

        assertEquals(17, ChromePhotosRealWebLabConfig.gloshiaPublicJpegUrls.distinct().size)
        ChromePhotosRealWebLabConfig.gloshiaPublicJpegUrls.forEach { url -> assertTrue(html.contains(url)) }
        val repeated = ChromePhotosRealWebLabConfig.gloshiaPublicJpegUrls.first()
        assertEquals(2, Regex(Regex.escape(repeated)).findAll(html).count())
        assertTrue(html.contains("loading=\"lazy\""))
    }

    @Test
    fun `web semantics fixture deterministically covers methods cookies auth range validators and streaming`() {
        val origin = ChromePhotosFixtureOrigin("safe".toByteArray(), "block".toByteArray(), "placeholder".toByteArray())
        val body = "{\"ok\":true}".toByteArray()
        val echo =
            origin.responseFor(
                ChromePhotosProxyRequest(
                    method = "PATCH",
                    target = "/web11a/echo",
                    headers = listOf(ChromeHttpHeader("Content-Type", "application/json")),
                    body = body,
                ),
            )
        assertEquals("PATCH", echo.headers.firstValue("X-Glosh-Method"))
        assertContentEquals(body, echo.originalBytes)

        val cookies =
            origin.responseFor(
                ChromePhotosProxyRequest(
                    "GET",
                    "/web11a/cookies",
                    headers = listOf(ChromeHttpHeader("Cookie", "gloshA=alpha; gloshB=beta")),
                ),
            )
        assertEquals("cookie-pass", cookies.originalBytes.toString(Charsets.UTF_8))

        val range =
            origin.responseFor(
                ChromePhotosProxyRequest(
                    "GET",
                    "/web11a/range",
                    headers = listOf(ChromeHttpHeader("Range", "bytes=10-31")),
                ),
            )
        assertEquals(206, range.statusCode)
        assertEquals(22, range.originalBytes.size)
        assertEquals("bytes 10-31/4096", range.headers.firstValue("Content-Range"))

        val first = origin.responseFor(ChromePhotosProxyRequest("GET", "/web11a/etag"))
        val notModified =
            origin.responseFor(
                ChromePhotosProxyRequest(
                    "GET",
                    "/web11a/etag",
                    headers = listOf(ChromeHttpHeader("If-None-Match", first.headers.firstValue("ETag")!!)),
                ),
            )
        assertEquals(304, notModified.statusCode)
        assertEquals(0, notModified.originalBytes.size)
        assertTrue(origin.responseFor(ChromePhotosProxyRequest("GET", "/web11a/large")).chunked)

        origin.responseFor(
            ChromePhotosProxyRequest(
                "POST",
                "/web11a/report",
                body = "GET:PASS,POST:PASS".toByteArray(),
            ),
        )
        assertEquals("GET:PASS,POST:PASS", origin.webSemanticsReport())
    }

    @Test
    fun `image authority fixture records normalized image request and bounded report`() {
        val origin = ChromePhotosFixtureOrigin("safe".toByteArray(), "block".toByteArray(), "placeholder".toByteArray())
        val normalized =
            ChromePhotosProxyRequest(
                method = "GET",
                target = "/web11b/normalized.png",
                headers = listOf(ChromeHttpHeader("Accept-Encoding", "identity")),
            )

        assertEquals("web11b-normalization-pass", origin.responseFor(normalized).resourceId)
        assertContentEquals(
            "NORMALIZATION_PASS".toByteArray(),
            origin.responseFor(ChromePhotosProxyRequest("GET", "/web11b/state")).originalBytes,
        )
        origin.responseFor(
            ChromePhotosProxyRequest(
                method = "POST",
                target = "/web11b/report",
                body = "SAFE:PASS,MISLABELED:PASS".toByteArray(),
            ),
        )
        assertEquals("SAFE:PASS,MISLABELED:PASS", origin.imageAuthorityReport())
    }
}
