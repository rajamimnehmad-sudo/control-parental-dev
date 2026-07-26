package com.contentfilter.user

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProcessIsolationTest {
    @Test
    fun `dag2 process does not start primary application work`() {
        assertTrue(UserProcessIsolation.isDagV2Process("com.contentfilter.user.dev", "com.contentfilter.user.dev:dag2"))
        assertFalse(
            UserProcessIsolation.shouldStartPrimaryProcessWork(
                "com.contentfilter.user.dev",
                "com.contentfilter.user.dev:dag2",
            ),
        )
    }

    @Test
    fun `main process keeps existing startup behavior`() {
        assertFalse(UserProcessIsolation.isDagV2Process("com.contentfilter.user.dev", "com.contentfilter.user.dev"))
        assertTrue(
            UserProcessIsolation.shouldStartPrimaryProcessWork(
                "com.contentfilter.user.dev",
                "com.contentfilter.user.dev",
            ),
        )
    }
}
