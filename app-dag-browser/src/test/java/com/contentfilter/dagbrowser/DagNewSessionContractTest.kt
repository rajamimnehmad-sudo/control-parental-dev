package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DagNewSessionContractTest {
    @Test
    fun `new windows keep the original Gecko navigation`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
        val delegate =
            activity.substringAfter("override fun onNewSession(")
                .substringBefore("tab.session.progressDelegate")

        assertContains(delegate, "reuseBlank = false")
        assertContains(delegate, "GeckoResult.fromValue(newTab.session)")
        assertFalse(delegate.contains("openNewTabForUri(uri)"))
        assertFalse(delegate.contains("newTab.session.loadUri"))
    }
}
