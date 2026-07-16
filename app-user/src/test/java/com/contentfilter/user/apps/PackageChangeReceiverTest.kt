package com.contentfilter.user.apps

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageChangeReceiverTest {
    @Test
    fun `new third party package requires approval`() {
        assertTrue(shouldRequireInstallApproval("com.example.new", false, false, "com.contentfilter.user.dev"))
    }

    @Test
    fun `updates system apps and content filter apps are excluded`() {
        assertFalse(shouldRequireInstallApproval("com.example.known", false, true, "com.contentfilter.user.dev"))
        assertFalse(shouldRequireInstallApproval("com.example.system", true, false, "com.contentfilter.user.dev"))
        assertFalse(
            shouldRequireInstallApproval(
                "com.contentfilter.user.dev",
                false,
                false,
                "com.contentfilter.user.dev",
            ),
        )
        assertFalse(
            shouldRequireInstallApproval(
                "com.contentfilter.admin.dev",
                false,
                false,
                "com.contentfilter.user.dev",
            ),
        )
    }
}
