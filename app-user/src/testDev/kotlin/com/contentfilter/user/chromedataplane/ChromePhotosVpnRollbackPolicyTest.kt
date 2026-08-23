package com.contentfilter.user.chromedataplane

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromePhotosVpnRollbackPolicyTest {
    @Test
    fun `inactive vpn before lab is stopped during rollback`() {
        assertEquals(
            ChromePhotosVpnRollbackAction.Stop,
            chromePhotosVpnRollbackAction(vpnWasRunningBeforeLab = false),
        )
    }

    @Test
    fun `active vpn before lab is refreshed during rollback`() {
        assertEquals(
            ChromePhotosVpnRollbackAction.RefreshRoutes,
            chromePhotosVpnRollbackAction(vpnWasRunningBeforeLab = true),
        )
    }
}
