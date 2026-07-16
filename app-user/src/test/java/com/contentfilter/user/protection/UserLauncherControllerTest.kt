package com.contentfilter.user.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserLauncherControllerTest {
    @Test
    fun `hides launcher only while effective protection is armed`() {
        assertFalse(
            userLauncherShouldBeEnabled(
                hasActivation = true,
                armed = true,
                protectionAvailable = true,
                maintenanceAuthorized = false,
            ),
        )
    }

    @Test
    fun `keeps recovery access before activation or during degradation`() {
        assertTrue(userLauncherShouldBeEnabled(false, true, true, false))
        assertTrue(userLauncherShouldBeEnabled(true, true, false, false))
        assertTrue(userLauncherShouldBeEnabled(true, false, true, false))
    }

    @Test
    fun `restores launcher during authorized maintenance`() {
        assertTrue(userLauncherShouldBeEnabled(true, true, true, true))
    }
}
