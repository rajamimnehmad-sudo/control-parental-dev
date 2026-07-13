package com.contentfilter.feature.vpn.service

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnStartupPolicyTest {
    @Test
    fun `starts after boot or package replacement when permission remains granted`() {
        assertTrue(VpnStartupPolicy.shouldStart(Intent.ACTION_BOOT_COMPLETED, true, false))
        assertTrue(VpnStartupPolicy.shouldStart(Intent.ACTION_MY_PACKAGE_REPLACED, true, false))
    }

    @Test
    fun `does not start without permission or after explicit DEV disable`() {
        assertFalse(VpnStartupPolicy.shouldStart(Intent.ACTION_BOOT_COMPLETED, false, false))
        assertFalse(VpnStartupPolicy.shouldStart(Intent.ACTION_MY_PACKAGE_REPLACED, true, true))
        assertFalse(VpnStartupPolicy.shouldStart("unexpected", true, false))
    }
}
