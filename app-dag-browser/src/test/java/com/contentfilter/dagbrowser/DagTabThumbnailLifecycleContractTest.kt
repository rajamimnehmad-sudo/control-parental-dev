package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DagTabThumbnailLifecycleContractTest {
    @Test
    fun `a bitmap already exposed to the tab switcher is never recycled manually`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertFalse(activity.contains("tab.thumbnail?.recycle()"))
        assertFalse(activity.contains("tab.thumbnail?.takeIf { it !== scaled }?.recycle()"))
    }

    @Test
    fun `persisted thumbnails stay resident only while the switcher needs them`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "private var tabThumbnailResidencyRequested = false")
        assertContains(activity, "tabs.filter { it.thumbnail == null }.forEach(::restoreTabThumbnail)")
        assertContains(activity, "!tabThumbnailResidencyRequested")
        assertContains(activity, "private fun hideTabSwitcher()")
        assertFalse(
            activity.contains(
                "override fun onStart() {\n        super.onStart()\n" +
                    "        tabs.filter { it.thumbnail == null }.forEach(::restoreTabThumbnail)",
            ),
        )
    }
}
