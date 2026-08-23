package com.contentfilter.user.chromedataplane

import kotlin.test.Test
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
}
