package com.contentfilter.user.protection

import kotlin.test.Test
import kotlin.test.assertTrue

class UserLauncherControllerTest {
    @Test
    fun `keeps launcher visible to avoid locking the user out`() {
        assertTrue(userLauncherShouldBeEnabled())
    }
}
