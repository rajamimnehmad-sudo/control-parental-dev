package com.contentfilter.user

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLaunchIntentPolicyTest {
    @Test
    fun `initial launch handles its intent`() {
        assertTrue(shouldHandleInitialUserIntent(hasSavedInstanceState = false))
    }

    @Test
    fun `activity recreation does not handle the launch intent again`() {
        assertFalse(shouldHandleInitialUserIntent(hasSavedInstanceState = true))
    }
}
