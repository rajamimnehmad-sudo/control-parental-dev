package com.contentfilter.user.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectedBrowserLauncherTest {
    @Test
    fun `launcher targets only the isolated browser`() {
        assertEquals("com.contentfilter.dagbrowser.dev", ProtectedBrowserLauncher.BrowserPackageName)
        assertEquals(
            "com.contentfilter.dagbrowser.DagBrowserActivity",
            ProtectedBrowserLauncher.BrowserActivityClassName,
        )
    }
}
