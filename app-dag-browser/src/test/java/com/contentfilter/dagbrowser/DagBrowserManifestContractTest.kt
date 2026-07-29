package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class DagBrowserManifestContractTest {
    @Test
    fun `activity qualifies for Android browser role`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertContains(manifest, "android.intent.action.VIEW")
        assertContains(manifest, "android.intent.category.DEFAULT")
        assertContains(manifest, "android.intent.category.BROWSABLE")
        assertContains(manifest, "android:scheme=\"http\"")
        assertContains(manifest, "android:scheme=\"https\"")
    }
}
