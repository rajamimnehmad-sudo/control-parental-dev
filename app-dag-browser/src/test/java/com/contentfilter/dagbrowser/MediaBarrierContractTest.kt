package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MediaBarrierContractTest {
    private val extensionRoot = File("src/main/assets/dag-protection")

    @Test
    fun `extension runs before page content and in every frame`() {
        val manifest = extensionRoot.resolve("manifest.json").readText()

        assertContains(manifest, "\"run_at\": \"document_start\"")
        assertContains(manifest, "\"all_frames\": true")
        assertContains(manifest, "\"match_about_blank\": true")
        assertContains(manifest, "\"nativeMessagingFromContent\"")
    }

    @Test
    fun `network barrier cancels visual resources`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(background, "\"image\"")
        assertContains(background, "\"media\"")
        assertContains(background, "\"object\"")
        assertContains(background, "onHeadersReceived")
        assertContains(background, "BLOCKED_MEDIA_MIME_PATTERN")
        assertContains(background, "cancel: true")
        assertContains(script, "stopPlayableMedia")
        assertContains(script, "\"play\", \"playing\", \"volumechange\", \"loadedmetadata\"")
    }

    @Test
    fun `page ad filter blocks known ad networks without touching video sites`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()
        val css = extensionRoot.resolve("barrier.css").readText()

        assertContains(background, "PAGE_AD_HOSTS")
        assertContains(background, "isPageAdvertisementRequest")
        assertContains(background, "VIDEO_RESOURCE_TYPES")
        assertContains(background, "isVideoSiteUrl(details.documentUrl)")
        assertContains(background, "doubleclick.net")
        assertContains(background, "return { cancel: true }")
        assertContains(script, "PAGE_AD_SELECTOR")
        assertContains(script, "isVideoSiteDocument")
        assertContains(script, "isInsideVideoPlayer")
        assertContains(script, "scanPageAdvertisements")
        assertContains(css, "glosh-dag-page-ad-hidden")
    }

    @Test
    fun `image responses remain withheld during bounded native inspection`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(background, "filterResponseData")
        assertContains(background, "MAX_INTERCEPT_CAPTURE_BYTES = 512 * 1024")
        assertContains(background, "MAX_ANALYSIS_BYTES = 2 * 1024 * 1024")
        assertContains(background, "MAX_SOURCE_URL_LENGTH = 4_096")
        assertContains(background, "MAX_ACTIVE_IMAGE_FILTERS = 16")
        assertContains(background, "MAX_NATIVE_IN_FLIGHT = 10")
        assertContains(background, "RESPONSE_CAPTURE_TIMEOUT_MS = 5_000")
        assertContains(background, "NATIVE_DECISION_TIMEOUT_MS = 2_500")
        assertContains(background, "\"media-bytes\"")
        assertContains(background, "filter.write(event.data)")
        assertContains(background, "presentDecision")
        assertContains(background, "\"media-fallback-request\"")
        assertContains(background, "analyzeFallbackSource")
        assertContains(background, "\"media-inline-request\"")
        assertContains(background, "sourceUrl.startsWith(\"blob:\")")
        assertContains(background, "sender?.id === browser.runtime.id")
        assertContains(background, "isTrustedContentSender(sender)")
        assertContains(script, "MAX_INLINE_ANALYSIS_BYTES = 2 * 1024 * 1024")
        assertContains(script, "requestSourceDecision")
    }

    @Test
    fun `capacity pressure defers safely into a bounded visible first queue`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(background, "MAX_ACTIVE_FALLBACK_ANALYSES = 2")
        assertContains(background, "MAX_QUEUED_FALLBACK_ANALYSES = 256")
        assertContains(background, "MAX_CONTENT_DECISIONS = 512")
        assertContains(background, "MAX_FALLBACK_DECISIONS = 512")
        assertContains(background, "crypto.subtle.digest(\"SHA-256\"")
        assertContains(background, "contentDecisionPromises")
        assertContains(background, "requestContentDecision")
        assertContains(background, "fallbackAnalysisQueue")
        assertContains(background, "Promise.resolve(\"retry\")")
        assertContains(background, "promoteFallbackTask")
        assertContains(background, "filter.disconnect()")
        assertContains(script, "IntersectionObserver")
        assertContains(script, "FALLBACK_ROOT_MARGIN = \"1200px 0px\"")
        assertContains(script, "IMAGE_PREWARM_ROOT_MARGIN = \"1200px 0px\"")
        assertContains(script, "VISIBLE_FALLBACK_DELAY_MS = 0")
        assertContains(script, "NEARBY_FALLBACK_DELAY_MS = 120")
        assertContains(script, "prioritizedFallbackDelay")
        assertContains(script, "scheduled.dueAt <= dueAt")
        assertContains(script, "prepareImageForFastPresentation")
        assertContains(script, "observeImagePrewarm")
        assertContains(script, "fetchpriority\", \"high")
        assertContains(script, "loading\", \"eager")
        assertContains(script, "decoding\", \"async")
        assertContains(script, "isVisibleNow(element) ? \"visible\" : \"nearby\"")
        assertContains(script, "FALLBACK_RETRY_MAX_MS = 6_000")
        assertContains(script, "fallbackPendingSources.delete(sourceUrl)")
        assertFalse(script.contains("MAX_FALLBACK_REQUESTS"))
    }

    @Test
    fun `document barrier covers static dynamic and painted media`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(css, "img,")
        assertContains(css, "canvas,")
        assertContains(css, "svg,")
        assertContains(css, "background-image: none !important")
        assertContains(script, "MutationObserver")
        assertContains(script, "applyKnownDecision(root)")
        assertContains(script, "barrier-ready")
        assertContains(script, "window.top === window")
    }

    @Test
    fun `late source mutations return to fail closed before reusing a decision`() {
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "SOURCE_MUTATION_ATTRIBUTES")
        assertContains(script, "SOURCE_RECONCILE_DELAY_MS = 160")
        assertContains(script, "pendingSourceChanges")
        assertContains(script, "protectSourceMutation")
        assertContains(script, "analyzedSources.delete(element)")
        assertContains(script, "data-glosh-dag-media\", \"hidden\"")
        assertContains(script, "pendingSafetySource")
        assertContains(script, "protectSourceMutation(mutation.target)")
        assertContains(script, "pendingSourceChanges.delete(event.target)")
        assertContains(script, "clearSourceReconcileTimer(event.target)")
    }

    @Test
    fun `css background photos stay hidden until a native decision restores them`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(css, "html:not([data-glosh-dag-background-probe]) *")
        assertContains(css, "data-glosh-dag-css-media=\"allow\"")
        assertContains(css, "var(--glosh-dag-background-image)")
        assertContains(css, "html[data-glosh-dag-background-probe] body")
        assertContains(script, "BACKGROUND_PROBE_ATTRIBUTE")
        assertContains(script, "probeCssBackgrounds")
        assertContains(script, "backgroundSourcesFromValue")
        assertContains(script, "requestCssFallbackDecision")
        assertContains(script, "applyCssBackgroundDecision")
        assertContains(script, "recordPseudoCssVisual")
        assertContains(script, "applyPseudoCssDecision")
        assertContains(script, "scheduleCssBackgroundProbe")
        assertContains(script, "MIN_BACKGROUND_PROBE_INTERVAL_MS = 450")
        assertContains(script, "pendingBackgroundProbeRoots")
        assertContains(script, "scheduleCssBackgroundProbe(mutation.target)")
        assertContains(script, "scheduleScrollBackgroundProbe")
        assertContains(script, "BACKGROUND_SCROLL_SETTLE_MS = 160")
        assertContains(script, "ownStyleSnapshots")
        assertContains(script, "setStylePropertyIfChanged")
        assertContains(css, "data-glosh-dag-css-before=\"allow\"")
        assertContains(css, "data-glosh-dag-css-after=\"allow\"")
        assertContains(script, "\"blob:\"")
        assertContains(script, "supportedDataImage")
    }

    @Test
    fun `explicit sponsored results and advertisement overlays are hidden`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "isGoogleSearchDocument")
        assertContains(script, "\"[data-text-ad]\"")
        assertContains(script, "\"[data-pla-slot]\"")
        assertContains(script, "\"#tads\"")
        assertContains(script, "\"[aria-label='Productos patrocinados']\"")
        assertContains(script, "/^(patrocinado|sponsored)$/")
        assertContains(script, "ocultar resultados patrocinados")
        assertContains(script, "|| control).click()")
        assertContains(script, "markExplicitAdvertisementFrames")
        assertContains(script, "\"iframe[name^='google_ads_iframe']\"")
        assertContains(script, "isLargeOverlay")
        assertContains(script, "scheduleSponsoredScan")
        assertContains(css, "data-glosh-dag-sponsored-result=\"true\"")
    }

    @Test
    fun `small self contained interface vectors can render without opening raster bypasses`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "MAX_UI_VECTOR_RENDERED_HEIGHT = 160")
        assertContains(script, "MAX_UI_VECTOR_RENDERED_AREA = 96_000")
        assertContains(script, "MAX_INLINE_VECTOR_ELEMENTS = 256")
        assertContains(script, "\"foreignobject\"")
        assertContains(script, "\"feimage\"")
        assertContains(script, "hasExternalCssUrl")
        assertContains(script, "isSafeInlineUiVector")
        assertContains(script, "isSafeRemoteUiVector")
        assertContains(css, "data-glosh-dag-ui-vector=\"allow\"")
        assertContains(css, "*::before")
        assertContains(css, "content: none !important")
    }

    @Test
    fun `bounded semantic controls recover original icons without opening photo thumbnails`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "MAX_FUNCTIONAL_ICON_SIZE = 72")
        assertContains(script, "MAX_FUNCTIONAL_IMAGE_SOURCE_SIZE = 256")
        assertContains(script, "FUNCTIONAL_ICON_SEMANTIC_PATTERN")
        assertContains(script, "FUNCTIONAL_ICON_SOURCE_PATTERN")
        assertContains(script, "applyFunctionalImageIconDecision")
        assertContains(script, "functionalCssControlKind")
        assertContains(script, "clippedSprite")
        assertContains(script, "backgroundPosition")
        assertContains(script, "backgroundRepeat")
        assertContains(script, "MAX_VIEWPORT_INTERACTIVE_ELEMENTS = 96")
        assertContains(script, "MAX_CONTROL_VISUAL_DESCENDANTS = 8")
        assertContains(script, "document.querySelectorAll(INTERACTIVE_CONTROL_SELECTOR)")
        assertContains(script, "element.closest(\"a, button, [role='button']")
        assertContains(script, "removeAttributeIfPresent(element, FUNCTIONAL_ICON_ATTRIBUTE)")
        assertFalse(script.contains("iheart", ignoreCase = true))
        assertFalse(script.contains("fav-heart", ignoreCase = true))
        assertFalse(css.contains("content: \"\\2661\""))
    }

    @Test
    fun `media analysis protocol is native and fail closed`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()
        val css = extensionRoot.resolve("barrier.css").readText()

        assertContains(background, "\"media-bytes\"")
        assertContains(background, "\"media-decision\"")
        assertContains(background, "\"media-presentation-decision\"")
        assertContains(background, "pendingNativeDecisions")
        assertContains(background, "message.reason === \"model_filter\"")
        assertContains(background, "\"safe_ui_vector\"")
        assertContains(background, "TECHNICAL_ERROR_ACTION")
        assertContains(background, "connectNative(NATIVE_APP)")
        assertContains(background, "browser.tabs")
        assertContains(background, "notifyPresentationDecision")
        assertContains(background, "data:image/")
        assertContains(background, "inline-image.glosh.local")
        assertContains(background, "MAX_INLINE_IMAGE_URL_LENGTH")
        assertContains(script, "connectNative(\"glosh.dag.protection\")")
        assertContains(script, "applyKnownDecision")
        assertContains(script, "candidateSourcesFor")
        assertContains(script, "sourcesFromSrcset")
        assertContains(script, "mediaElementsBySource")
        assertContains(script, "updateElementSourceIndex")
        assertContains(script, "applyDecisionToMediaSource")
        assertContains(script, "stillReferencesDecisionSource")
        assertContains(script, "appliedCurrentDecision")
        assertContains(script, "removeElementFromSourceIndex")
        assertContains(script, "\"data-lazy-src\"")
        assertContains(script, "\"data-srcset\"")
        assertContains(script, "decisionsBySource")
        assertContains(script, "DECISION_ACTIONS = [\"allow\", \"block\", \"error\"]")
        assertContains(script, "[\"http:\", \"https:\"]")
        assertContains(css, "data-glosh-dag-media=\"allow\"")
        assertContains(css, "data-glosh-dag-media=\"block\"")
        assertContains(
            css,
            "filter: blur(28px) brightness(0.74) saturate(0.5) contrast(0.88)",
        )
        assertContains(css, "transition: opacity 160ms ease-out")
        assertFalse(css.contains("transition: filter"))
        assertContains(script, "Without the authenticated native channel, media remains hidden")
    }

    @Test
    fun `performance evidence is isolated to the initial window of each document`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "\"document-started\"")
        assertContains(script, "\"document-loaded\"")
        assertContains(script, "documentToken: performanceDocumentToken")
        assertContains(script, "window.addEventListener")
        assertContains(script, "reportDocumentLoaded()")
        assertContains(script, "documentLoadedReported")
        assertContains(background, "VIEWPORT_SETTLE_MS = 250")
        assertContains(background, "VIEWPORT_CAPTURE_WINDOW_MS = 750")
        assertContains(background, "documentStatesByTab")
        assertContains(background, "documentStatesByToken")
        assertContains(background, "pendingInitialWork(state) !== 0")
        assertContains(background, "currentDocumentStateForTab(details.tabId)")
        assertContains(background, "initialDocumentStateForToken(message.documentToken)")
        assertContains(background, "documentStatesByTab.get(state.tabId) === state")
        assertContains(background, "documentStateForDetails(details)")
        assertContains(background, "document-token-request")
        assertContains(script, "document-token-response")
        assertContains(background, "documentGeneration && !isCurrentDocumentState(documentGeneration)")
        assertContains(background, "\"viewport-images-ready\"")
        assertContains(
            background,
            "Performance evidence is DEV-only and never changes the fail-closed barrier",
        )
    }

    @Test
    fun `media states keep waiting and terminal error overlays but filtered media only blurred`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()
        val background = extensionRoot.resolve("background.js").readText()
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(script, "MAX_FALLBACK_ATTEMPTS = 4")
        assertContains(script, "failedSources")
        assertContains(script, "data-glosh-dag-media\", \"error\"")
        assertContains(script, "updateMediaHostState")
        assertContains(script, "mediaHostsByElement")
        assertContains(script, "releaseMediaHostsIn")
        assertContains(script, "mutation.removedNodes")
        assertContains(script, "APPROVED_PRESENTATION_SELECTOR")
        assertContains(script, "clearWaitingMediaHostsAround")
        assertContains(script, "if (action === \"allow\")")
        assertFalse(script.contains("siblingStates.includes(\"block\")"))
        assertContains(script, "siblingStates.includes(\"allow\")")
        assertContains(script, "candidateSourcesFor(element)[0]")
        assertContains(css, "data-glosh-dag-media-host=\"waiting\"")
        assertContains(css, "@keyframes glosh-dag-waiting")
        assertContains(css, "linear-gradient(100deg")
        assertFalse(css.contains("Analizando…"))
        assertFalse(css.contains("data-glosh-dag-media-host=\"filtered\""))
        assertFalse(css.contains("clip-path: polygon"))
        assertContains(script, "presentationState === \"filtered\"")
        assertContains(script, "releaseMediaHost(element)")
        assertContains(css, "isolation: isolate !important")
        assertFalse(css.contains("content: \"Protegida por Glosh\""))
        assertContains(script, "FILTERED_ACCESSIBLE_DESCRIPTION")
        assertContains(script, "aria-description")
        assertContains(css, "data-glosh-dag-media=\"error\"")
        assertContains(css, "visibility: hidden !important")
        assertFalse(script.contains("matchedStates"))
        assertFalse(background.contains("response?.matchedStates"))
        assertFalse(activity.contains("states=\${payload.optString"))
        assertFalse(activity.contains("source=\${payload.optString"))
    }
}
