package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

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

    @Test
    fun `download provider is private and grants only temporary URIs`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val paths = File("src/main/res/xml/dag_download_paths.xml").readText()

        assertContains(manifest, "androidx.core.content.FileProvider")
        assertContains(manifest, "android:exported=\"false\"")
        assertContains(manifest, "\${applicationId}.downloads.fileprovider")
        assertContains(paths, "path=\"downloads/\"")
    }

    @Test
    fun `page scrolling is not intercepted by pull refresh`() {
        val activity = File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "swipeRefresh.isEnabled = false")
        assertContains(activity, "R.id.menu_reload")
        assertFalse(activity.contains("setOnRefreshListener"))
    }

    @Test
    fun `address action reloads a loaded page without intercepting scroll`() {
        val activity = File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "if (shouldShowReloadAction()) reloadActivePage() else navigateFromInput()")
        assertContains(activity, "R.drawable.ic_dag_reload")
        assertContains(activity, "addressInput.setOnFocusChangeListener")
        assertContains(activity, "R.id.menu_reload")
    }
}
