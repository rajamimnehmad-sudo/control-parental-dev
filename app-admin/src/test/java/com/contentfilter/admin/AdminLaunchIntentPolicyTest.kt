package com.contentfilter.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminLaunchIntentPolicyTest {
    @Test
    fun `initial launch handles its intent`() {
        assertTrue(shouldHandleInitialAdminIntent(hasSavedInstanceState = false))
    }

    @Test
    fun `activity recreation does not handle the launch intent again`() {
        assertFalse(shouldHandleInitialAdminIntent(hasSavedInstanceState = true))
    }
}
