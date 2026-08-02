package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaBarrierContractTest {
    private val extensionRoot = File("src/main/assets/dag-protection")
    private val manifest by lazy { extensionRoot.resolve("manifest.json").readText() }
    private val background by lazy { extensionRoot.resolve("background.js").readText() }
    private val barrier by lazy { extensionRoot.resolve("barrier.js").readText() }
    private val ads by lazy { extensionRoot.resolve("ads.js").readText() }
    private val css by lazy { extensionRoot.resolve("barrier.css").readText() }

    @Test
    fun `extension starts before content in every frame and updates in place`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(manifest, "\"run_at\": \"document_start\"")
        assertContains(manifest, "\"all_frames\": true")
        assertContains(manifest, "\"match_about_blank\": true")
        assertContains(manifest, "\"nativeMessagingFromContent\"")
        assertContains(activity, ".ensureBuiltIn(ExtensionLocation, ExtensionId)")
        assertFalse(activity.contains(".installBuiltIn(ExtensionLocation)"))
    }

    @Test
    fun `network gate is the sole authority for ordinary http raster bytes`() {
        assertContains(background, "filterResponseData")
        assertContains(background, "INTERCEPTED_RESOURCE_TYPES")
        assertContains(background, "settleStream")
        assertContains(background, "requestContentDecision")
        assertContains(background, "message.reason === \"model_filter\"")
        assertContains(background, "filter.write(bytes)")
        assertFalse(background.contains("filter.write(event.data)"))
        assertContains(background, "cancel: true")
        assertContains(background, "DOCUMENT_CURRENT_MESSAGE")
        assertContains(background, "DOCUMENT_RETIRED_MESSAGE")
    }

    @Test
    fun `transport remains bounded prioritized and fail closed`() {
        assertContains(background, "MAX_ACTIVE_IMAGE_STREAMS = 128")
        assertContains(background, "MAX_INTERCEPT_CAPTURE_BUDGET_BYTES = 8 * 1024 * 1024")
        assertContains(background, "MAX_INTERCEPT_CAPTURE_BYTES = MAX_ANALYSIS_BYTES")
        assertContains(background, "MAX_NATIVE_IN_FLIGHT = 4")
        assertContains(background, "visibleInterceptAnalysisQueue")
        assertContains(background, "nearbyInterceptAnalysisQueue")
        assertContains(background, "crypto.subtle.digest(\"SHA-256\"")
        assertContains(background, "TECHNICAL_ERROR_ACTION")
        assertContains(background, "isCurrentDocumentState")
    }

    @Test
    fun `presentation never destroys the page wide css system`() {
        assertFalse(css.contains("list-style-image: none"))
        assertFalse(css.contains("border-image-source: none"))
        assertFalse(css.contains("content: none !important"))
        assertFalse(css.contains("data-glosh-dag-background-probe"))
        assertFalse(barrier.contains("scheduleScrollBackgroundProbe"))
        assertFalse(barrier.contains("MAX_BACKGROUND_PROBE_ELEMENTS"))
        assertFalse(barrier.contains("document.querySelectorAll(INTERACTIVE_CONTROL_SELECTOR)"))
        assertFalse(barrier.contains("BACKGROUND_PROBE_ATTRIBUTE"))
        assertContains(css, "html:not([data-glosh-dag-initialized])")
        assertContains(barrier, "INITIALIZED_ATTRIBUTE")
        assertContains(barrier, "initialBarrierComplete")
    }

    @Test
    fun `advertisement presentation is isolated from media presentation`() {
        assertContains(manifest, "\"ads.js\"")
        assertContains(ads, "EXPLICIT_AD_SELECTOR")
        assertContains(ads, "SPONSORED_LABEL")
        assertContains(ads, "isVideoSite")
        assertContains(css, "glosh-dag-page-ad-hidden")
        assertFalse(barrier.contains("scanPageAdvertisements"))
        assertFalse(barrier.contains("scheduleSponsoredScan"))
    }

    @Test
    fun `temporary initial css gate opens before waiting for dom content loaded`() {
        val initialize = barrier.indexOf("data-glosh-dag-initialized")
        val ready = barrier.lastIndexOf("reportBarrierReady()")

        assertTrue(initialize >= 0)
        assertTrue(ready > initialize)
        assertContains(barrier, "DOMContentLoaded")
        assertContains(barrier, "openInitialBarrier();")
        assertContains(barrier, "const completeDocument")
        assertContains(barrier, "barrier-ready")
    }

    @Test
    fun `ordinary media uses an indexed state machine without global rescans`() {
        assertContains(barrier, "elementsBySource")
        assertContains(barrier, "sourcesByElement")
        assertContains(barrier, "indexElement")
        assertContains(barrier, "applyDecisionToSource")
        assertContains(barrier, "MutationObserver")
        assertContains(barrier, "requestAnimationFrame")
        assertContains(barrier, "IntersectionObserver")
        assertContains(barrier, "rootMargin: \"640px 0px\"")
        assertContains(barrier, "mediaPriorityObserver?.observe")
        assertContains(barrier, "flushLayoutWork")
        assertContains(barrier, "mediaElements.forEach(boundsFor)")
        assertContains(barrier, "setTimeout(flushLayoutWork, 48)")
        assertContains(barrier, "SOURCE_ATTRIBUTES")
        assertContains(barrier, "setMediaState(element, action || \"hidden\")")
        assertContains(barrier, "data-glosh-dag-media")
        assertContains(barrier, "unregisterTree")
        assertContains(barrier, "mutation.removedNodes")
        assertContains(css, "img:not([data-glosh-dag-media=\"allow\"])")
        assertFalse(barrier.contains("__gloshDagGeneratedId"))
        assertFalse(barrier.contains("mediaElementsBySource.get(sourceUrl)?.size > 0\n        ?"))
    }

    @Test
    fun `generated media uses indexed css targets instead of tree wide computed style scans`() {
        assertContains(barrier, "GENERATED_PROTOCOLS = new Set([\"data:\", \"blob:\"])")
        assertContains(barrier, "generatedSourcesFromStyle")
        assertContains(barrier, "refreshGeneratedRuleIndex")
        assertContains(barrier, "generatedRuleTargets")
        assertContains(barrier, "inspectGeneratedTargets")
        assertFalse(barrier.contains("inspectGeneratedVisuals"))
        assertFalse(barrier.contains("createTreeWalker"))
        assertFalse(barrier.contains("NodeFilter"))
        assertContains(barrier, "requestSourceDecision")
        assertContains(background, "INLINE_ANALYSIS_ORIGIN")
        assertContains(background, "`${'$'}{INLINE_ANALYSIS_ORIGIN}/blob`")
        assertContains(background, "`${'$'}{INLINE_ANALYSIS_ORIGIN}/data`")
        assertContains(css, "data-glosh-dag-generated-background")
        assertContains(css, "data-glosh-dag-generated-before")
        assertContains(css, "data-glosh-dag-generated-after")
    }

    @Test
    fun `video canvas object and unsafe inline vectors stay closed`() {
        assertContains(background, "BLOCKED_RESOURCE_TYPES")
        assertContains(background, "BLOCKED_MEDIA_MIME_PATTERN")
        assertContains(barrier, "BLOCKED_MEDIA_SELECTOR")
        assertContains(barrier, "stopBlockedMedia")
        assertContains(barrier, "safeInlineSvg")
        assertContains(barrier, "foreignobject")
        assertContains(barrier, "feimage")
        assertContains(barrier, "xlink:href")
        assertContains(barrier, "!value.startsWith(\"#\")")
        assertContains(css, "svg:not([data-glosh-dag-ui-vector=\"allow\"])")
    }

    @Test
    fun `waiting filter error and allow remain visibly distinct without rejected pixels`() {
        assertContains(css, "data-glosh-dag-media-host=\"waiting\"")
        assertContains(css, "data-glosh-dag-media-host=\"filtered\"")
        assertContains(css, "data-glosh-dag-media-host=\"error\"")
        assertContains(css, "@keyframes glosh-dag-waiting")
        assertContains(css, "prefers-reduced-motion: reduce")
        assertContains(barrier, "FILTERED_ACCESSIBLE_DESCRIPTION")
        assertContains(barrier, "ERROR_ACCESSIBLE_DESCRIPTION")
        assertFalse(css.contains("blur(28px)"))
        assertFalse(css.contains("Protegida por Glosh"))
    }

    @Test
    fun `site lazy loading remains owned by the site`() {
        assertFalse(barrier.contains("fetchpriority\", \"high"))
        assertFalse(barrier.contains("loading\", \"eager"))
        assertContains(barrier, "decoding\", \"async")
        assertContains(barrier, "media-priority-hint")
    }

    @Test
    fun `diagnostics do not expose urls text or image pixels`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertFalse(activity.contains("source=\${payload.optString"))
        assertFalse(activity.contains("bytesBase64=\${payload.optString"))
        assertContains(activity, "safePipelineValue")
        assertContains(background, "diagnosticsEnabled")
    }

    @Test
    fun `performance lifecycle stays bound to an exact document`() {
        assertContains(barrier, "document-started")
        assertContains(barrier, "document-loaded")
        assertContains(barrier, "documentToken: performanceDocumentToken")
        assertContains(background, "documentStatesByTab")
        assertContains(background, "documentStatesByToken")
        assertContains(background, "viewport-images-ready")
        assertContains(background, "abortQueuedFallbackTasksForDocument")
    }
}
