package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagBrowserV3BridgeTest {
    @Test
    fun `bridge targets only the isolated DEV browser`() {
        assertEquals("com.contentfilter.dagbrowser.dev", DagBrowserV3Target.packageName)
        assertEquals("com.contentfilter.dagbrowser.DagBrowserActivity", DagBrowserV3Target.activityClassName)
    }
}
