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

        assertContains(background, "\"image\"")
        assertContains(background, "\"media\"")
        assertContains(background, "\"object\"")
        assertContains(background, "cancel: true")
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
        assertContains(background, "MAX_CONTENT_DECISIONS = 256")
        assertContains(background, "crypto.subtle.digest(\"SHA-256\"")
        assertContains(background, "contentDecisionPromises")
        assertContains(background, "requestContentDecision")
        assertContains(background, "fallbackAnalysisQueue")
        assertContains(background, "Promise.resolve(\"retry\")")
        assertContains(background, "promoteFallbackTask")
        assertContains(background, "filter.disconnect()")
        assertContains(script, "IntersectionObserver")
        assertContains(script, "FALLBACK_ROOT_MARGIN = \"640px 0px\"")
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
        assertContains(css, "data-glosh-dag-css-before=\"allow\"")
        assertContains(css, "data-glosh-dag-css-after=\"allow\"")
        assertContains(script, "\"blob:\"")
        assertContains(script, "supportedDataImage")
    }

    @Test
    fun `google sponsored results are hidden only inside known ad containers`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "isGoogleSearchDocument")
        assertContains(script, "\"[data-text-ad]\"")
        assertContains(script, "\"[data-pla-slot]\"")
        assertContains(script, "/^(patrocinado|sponsored)$/")
        assertContains(script, "ocultar resultados patrocinados")
        assertContains(script, "|| control).click()")
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
    fun `media analysis protocol is native and fail closed`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()
        val css = extensionRoot.resolve("barrier.css").readText()

        assertContains(background, "\"media-bytes\"")
        assertContains(background, "\"media-decision\"")
        assertContains(background, "\"media-presentation-decision\"")
        assertContains(background, "pendingNativeDecisions")
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
        assertContains(script, "\"data-lazy-src\"")
        assertContains(script, "\"data-srcset\"")
        assertContains(script, "decisionsBySource")
        assertContains(script, "[\"http:\", \"https:\"]")
        assertContains(css, "data-glosh-dag-media=\"allow\"")
        assertContains(css, "data-glosh-dag-media=\"block\"")
        assertContains(css, "filter: blur(28px)")
        assertContains(script, "Without the authenticated native channel, media remains hidden")
    }

    @Test
    fun `performance evidence waits for document load and response quiescence`() {
        val background = extensionRoot.resolve("background.js").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "\"document-started\"")
        assertContains(script, "\"document-loaded\"")
        assertContains(script, "window.addEventListener")
        assertContains(background, "VIEWPORT_SETTLE_MS = 250")
        assertContains(background, "activeImageFilters !== 0")
        assertContains(background, "nativeRequestsInFlight !== 0")
        assertContains(background, "\"viewport-images-ready\"")
        assertContains(
            background,
            "Performance evidence is DEV-only and never changes the fail-closed barrier",
        )
    }

    @Test
    fun `media states distinguish waiting filtered and terminal error without exposing pixels`() {
        val css = extensionRoot.resolve("barrier.css").readText()
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "MAX_FALLBACK_ATTEMPTS = 4")
        assertContains(script, "failedSources")
        assertContains(script, "data-glosh-dag-media\", \"error\"")
        assertContains(script, "updateMediaHostState")
        assertContains(css, "data-glosh-dag-media-host=\"waiting\"")
        assertContains(css, "Analizando…")
        assertContains(css, "data-glosh-dag-media-host=\"filtered\"")
        assertContains(css, "Protegida por Glosh")
        assertContains(css, "data-glosh-dag-media=\"error\"")
        assertContains(css, "visibility: hidden !important")
    }
}
