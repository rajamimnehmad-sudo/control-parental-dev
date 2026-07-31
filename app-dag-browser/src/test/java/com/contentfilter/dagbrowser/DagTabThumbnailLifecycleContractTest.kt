package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class DagTabThumbnailLifecycleContractTest {
    @Test
    fun `a bitmap already exposed to the tab switcher is never recycled manually`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertFalse(activity.contains("tab.thumbnail?.recycle()"))
        assertFalse(activity.contains("tab.thumbnail?.takeIf { it !== scaled }?.recycle()"))
    }
}
