package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class DagAboutContractTest {
    @Test
    fun `about reads the installed package version instead of duplicating it`() {
        val menu = File("src/main/res/menu/dag_browser_menu.xml").readText()
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(menu, "android:id=\"@+id/menu_about\"")
        assertContains(menu, "android:title=\"@string/about_dag\"")
        assertContains(activity, "packageManager.getPackageInfo")
        assertContains(activity, "packageInfo.versionName")
        assertContains(activity, "packageInfo.longVersionCode")
        assertContains(activity, "DagVisualModelInfo.PublicName")
        assertContains(activity, "DagVisualModelInfo.FunctionalVersion")
        assertContains(activity, "DagVisualModelInfo.ShortSha256")
        assertContains(activity, "DagVisualModelInfo.Runtime")
        assertContains(activity, "DagVisualModelInfo.PolicyVersion")
    }
}
