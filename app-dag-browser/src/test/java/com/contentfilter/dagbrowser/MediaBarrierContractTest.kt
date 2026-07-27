package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

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

        assertContains(background, "filterResponseData")
        assertContains(background, "MAX_CAPTURE_BYTES = 256 * 1024")
        assertContains(background, "NATIVE_DECISION_TIMEOUT_MS = 2_500")
        assertContains(background, "\"media-bytes\"")
        assertContains(background, "closeWithPlaceholder")
        assertContains(background, "response?.action === \"block\"")
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
        assertContains(script, "barrier-ready")
        assertContains(script, "window.top === window")
    }

    @Test
    fun `media analysis protocol is native and fail closed`() {
        val script = extensionRoot.resolve("barrier.js").readText()

        assertContains(script, "\"media-candidate\"")
        assertContains(script, "\"media-decision\"")
        assertContains(script, "SAFE_NATIVE_ACTIONS = new Set([\"block\"])")
        assertContains(script, "Any native or protocol failure leaves the media hidden")
        assertContains(script, "MAX_CANDIDATES_PER_DOCUMENT")
    }
}
