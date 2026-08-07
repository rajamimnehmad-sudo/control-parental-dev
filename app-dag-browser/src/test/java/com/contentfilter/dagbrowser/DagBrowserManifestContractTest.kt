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
        assertContains(activity, "keepCurrentPageVisible = tab.displayState == TabDisplayState.Visible")
        assertContains(activity, "tab.keepCurrentPageVisibleDuringReload &&")
    }

    @Test
    fun `web content consistently requests the light color scheme`() {
        val runtime = File("src/main/java/com/contentfilter/dagbrowser/DagGeckoRuntime.kt").readText()

        assertContains(runtime, "GeckoRuntimeSettings")
        assertContains(runtime, ".preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_LIGHT)")
        assertContains(runtime, ".setParallelMarkingEnabled(true)")
        assertFalse(runtime.contains("COLOR_SCHEME_DARK"))
        assertFalse(runtime.contains("COLOR_SCHEME_SYSTEM"))
    }

    @Test
    fun `thin page progress replaces the blue loading transition`() {
        val activity = File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
        val layout = File("src/main/res/layout/activity_dag_browser.xml").readText()
        val acceptedNavigation =
            activity
                .substringAfter("private fun maybeCoverAcceptedNavigation")
                .substringBefore("private fun showNavigationSnapshot")

        assertContains(layout, "android:id=\"@+id/page_load_progress\"")
        assertContains(layout, "android:id=\"@+id/navigation_snapshot\"")
        assertContains(layout, "android:layout_height=\"2dp\"")
        assertContains(layout, "android:background=\"@color/dag_surface\"")
        assertContains(activity, "override fun onProgressChange(")
        assertContains(activity, "finishPageLoadProgress(tab)")
        assertContains(activity, "geckoView.postOnAnimation(::clearNavigationSnapshot)")
        assertContains(activity, "showNavigationSnapshot(tab)\n        beginProtectedLoad(tab")
        assertContains(
            acceptedNavigation,
            "showNavigationSnapshot(tab)\n            beginProtectedLoad(",
        )
        assertFalse(
            activity.contains(
                "navigationFrameTabId == tab.id && navigationFrameRevision == tab.navigationRevision",
            ),
        )
        assertContains(activity, "tab.barrierReadyForNavigation = false")
        assertContains(activity, "tab.protectedContentReadyForNavigation = false")
        assertContains(activity, "!tab.barrierReadyForNavigation")
        assertContains(activity, "!tab.protectedContentReadyForNavigation")
        assertContains(activity, "!tab.documentSanitizedForNavigation")
        assertContains(activity, "ViewportImagesReadyMessage -> handleViewportImagesReady()")
        assertContains(activity, "recordPerformanceMetric(DagPerformanceMetric.ViewportImagesReady)")
        assertFalse(activity.contains("shimmer = true"))
    }
}
