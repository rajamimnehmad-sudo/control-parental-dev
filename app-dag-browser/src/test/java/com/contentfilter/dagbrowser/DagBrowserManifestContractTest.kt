package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertFalse(activity.contains("scrollDelegate"))
        assertFalse(activity.contains("contentScrollY"))
        assertFalse(activity.contains("SwipeRefreshLayout"))
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
        assertContains(activity, "if (tab === activeTab && !keepCurrentPageVisible)")
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
    fun `model initialization leaves the main thread before protected tabs restore`() {
        val activity = File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
        val initialization =
            activity
                .substringAfter("private fun installProtectionExtension")
                .substringBefore("private fun clearInterceptedMediaCacheAfterUpdate")

        assertContains(initialization, "analyzerInitializationExecutor.execute")
        assertContains(initialization, "imageAnalyzer = analyzer")
        assertContains(initialization, "clearInterceptedMediaCacheAfterUpdate()")
        assertContains(activity, "if (this::imageAnalyzer.isInitialized)")
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
        assertContains(activity, "navigationFrameRevision != tab.previewRevision")
        assertContains(activity, "navigationFrameRevision = request.revision")
        assertContains(activity, "if (navigationSnapshot.visibility == View.VISIBLE) return")
        assertContains(activity, "val bitmapOwnedByNavigationFrame = updateNavigationFrame(tab, request, bitmap)")
        assertContains(activity, "tab.barrierReadyForNavigation = false")
        assertContains(activity, "if (startsNewNavigation) {\n            recordFlight")
        assertContains(activity, "tab.protectedContentReadyForNavigation = false")
        assertContains(activity, "!tab.barrierReadyForNavigation")
        assertContains(activity, "!tab.protectedContentReadyForNavigation")
        assertContains(activity, "!tab.documentSanitizedForNavigation")
        assertContains(activity, "ViewportImagesReadyMessage -> handleViewportImagesReady(payload)")
        assertContains(activity, "mediaDocumentRegistry.isCurrent")
        assertContains(activity, "it.previewDocumentToken == documentToken")
        assertContains(activity, "documentToken != senderTab.previewDocumentToken")
        assertContains(activity, "recordPerformanceMetric(DagPerformanceMetric.ViewportImagesReady)")
        val viewportHandler =
            activity
                .substringAfter("private fun handleViewportImagesReady")
                .substringBefore("private fun mediaDocumentIdentity")
        assertTrue(
            viewportHandler.indexOf("DagFlightEventType.ViewportReady") <
                viewportHandler.indexOf("if (!tab.waitingForBarrier) return"),
        )
        assertFalse(activity.contains("shimmer = true"))
    }

    @Test
    fun `premium browser chrome keeps protection states visually distinct`() {
        val layout = File("src/main/res/layout/activity_dag_browser.xml").readText()
        val styles = File("src/main/res/values/styles.xml").readText()
        val tabLayout = File("src/main/res/layout/item_dag_tab.xml").readText()
        val pageEntryLayout = File("src/main/res/layout/item_dag_page_entry.xml").readText()

        assertContains(layout, "android:background=\"@color/dag_chrome\"")
        assertContains(layout, "android:background=\"@drawable/dag_toolbar_button_background\"")
        assertContains(layout, "android:src=\"@drawable/ic_dag_add\"")
        assertContains(layout, "android:id=\"@+id/video_lab_overlay\"")
        assertContains(layout, "android:background=\"@color/dag_navy\"")
        assertContains(layout, "android:id=\"@+id/safety_overlay\"")
        assertContains(layout, "android:background=\"@color/dag_background\"")
        assertContains(tabLayout, "@drawable/dag_tab_card_background")
        assertContains(pageEntryLayout, "@drawable/dag_tab_card_background")
        assertContains(styles, "<item name=\"android:windowLightStatusBar\">true</item>")
        assertContains(styles, "<item name=\"android:windowLightNavigationBar\">true</item>")
    }
}
