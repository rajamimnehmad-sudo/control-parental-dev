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

    @Test
    fun `inline PDF stays hidden but remains available to the guarded saver`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "session.isPdfJs()")
        assertContains(activity, "TabDisplayState.PdfReady")
        assertContains(activity, "if (!tab.session.isOpen || !tab.pdfDocumentReady)")
        assertContains(activity, "tab.session.pdfFileSaver.save()")
    }
}
