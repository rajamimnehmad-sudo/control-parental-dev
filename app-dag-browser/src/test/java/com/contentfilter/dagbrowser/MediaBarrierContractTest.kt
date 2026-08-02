package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

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
    fun `ordinary images remain owned by Gecko without an extension response gate`() {
        assertFalse(background.contains("filterResponseData"))
        assertFalse(background.contains("INTERCEPTED_RESOURCE_TYPES"))
        assertFalse(background.contains("connectNative"))
        assertFalse(background.contains("bytesBase64"))
        assertFalse(barrier.contains("data-glosh-dag-media"))
        assertFalse(barrier.contains("MutationObserver"))
        assertFalse(barrier.contains("srcset"))
        assertFalse(css.contains("img"))
        assertFalse(css.contains("image"))
        assertFalse(css.contains("svg"))
    }

    @Test
    fun `minimal bridge only reports readiness and sensitive preview state`() {
        assertContains(barrier, "barrier-ready")
        assertContains(barrier, "tab-preview-eligibility")
        assertContains(barrier, "connectNative")
        assertContains(barrier, "DOMContentLoaded")
        assertFalse(barrier.contains("querySelectorAll"))
        assertFalse(barrier.contains("requestAnimationFrame"))
        assertFalse(barrier.contains("IntersectionObserver"))
    }

    @Test
    fun `video and advertisement protection stay isolated`() {
        assertContains(background, "BLOCKED_RESOURCE_TYPES")
        assertContains(background, "BLOCKED_MEDIA_MIME_PATTERN")
        assertContains(css, "video")
        assertContains(css, "audio")
        assertContains(css, "canvas")
        assertContains(manifest, "\"ads.js\"")
        assertContains(ads, "EXPLICIT_AD_SELECTOR")
        assertContains(ads, "SPONSORED_LABEL")
        assertContains(css, "glosh-dag-page-ad-hidden")
    }

    @Test
    fun `active protection contains no store or device exceptions`() {
        val activeProtection = "$background\n$barrier\n$css"
        listOf("cheeky", "mimo", "fravega", "sm-a235", "sm-s908").forEach { forbidden ->
            assertFalse(activeProtection.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun `disabled visual classification does not initialize the onnx analyzer`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "if (BuildConfig.GLOSHIA_VISUAL_ENABLED)")
        assertContains(activity, "DagOnDeviceImageAnalyzer.create(applicationContext)")
        assertContains(activity, "UnavailableDagImageAnalyzer")
    }
}
