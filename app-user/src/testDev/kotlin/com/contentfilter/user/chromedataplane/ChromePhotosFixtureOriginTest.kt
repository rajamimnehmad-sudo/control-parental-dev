package com.contentfilter.user.chromedataplane

import kotlin.test.Test
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
}
