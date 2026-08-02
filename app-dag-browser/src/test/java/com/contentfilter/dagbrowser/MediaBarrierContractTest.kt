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
    fun `extension starts before content and updates in place`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(manifest, "\"run_at\": \"document_start\"")
        assertContains(manifest, "\"all_frames\": true")
        assertContains(manifest, "\"nativeMessaging\"")
        assertContains(activity, ".ensureBuiltIn(ExtensionLocation, ExtensionId)")
        assertFalse(activity.contains(".installBuiltIn(ExtensionLocation)"))
    }

    @Test
    fun `raster response has one bounded native authority`() {
        assertContains(background, "filterResponseData")
        assertContains(background, "RASTER_IMAGE_MIME_PATTERN.test(contentType)")
        assertContains(background, "alreadyIntercepted")
        assertContains(background, "MAX_IMAGE_BYTES = 2 * 1024 * 1024")
        assertContains(background, "MAX_CAPTURED_BYTES = 8 * 1024 * 1024")
        assertContains(background, "MAX_NATIVE_IN_FLIGHT = 2")
        assertContains(background, "MAX_QUEUED_ANALYSES = 24")
        assertContains(background, "media-bytes")
        assertContains(background, "model_allow")
        assertContains(background, "model_filter")
        assertContains(background, "safe_ui_sprite")
        assertContains(
            File("src/main/java/com/contentfilter/dagbrowser/DagSafeUiSpritePolicy.kt").readText(),
            "AndroidDagSafeUiSpriteInspector",
        )
    }

    @Test
    fun `filtered response never releases rejected pixels`() {
        assertContains(background, "BLOCKED_PLACEHOLDER_BASE64")
        assertContains(background, "blockedPlaceholder")
        assertContains(background, "action === \"allow\"")
        assertFalse(background.contains("filter.write(event.data)"))
        assertFalse(background.contains("blur("))
    }

    @Test
    fun `inline and changing image sources close before stable reveal`() {
        assertContains(barrier, "barrier-ready")
        assertContains(barrier, "IMAGE_STABILITY_MS = 0")
        assertContains(barrier, "MutationObserver")
        assertContains(barrier, "attributeFilter: [\"src\", \"srcset\", \"sizes\"]")
        assertContains(barrier, "imageSource(image) === source")
        assertContains(barrier, "image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)")
        assertContains(barrier, "hasInlineImageSource(record.target)")
        assertFalse(barrier.contains(".src ="))
        assertFalse(barrier.contains(".srcset ="))
        assertContains(css, "img[src^=\"data:\" i]")
        assertContains(css, "img[src^=\"blob:\" i]")
        assertContains(css, "img[srcset*=\"data:image\" i]")
        assertContains(css, "picture:has(source[srcset*=\"data:image\" i]) img")
        assertContains(css, "svg image[href^=\"data:\" i]")
        assertContains(css, "background-image: none !important")
        assertContains(css, "img:not([data-glosh-dag-stable=\"true\"])")
        assertFalse(css.contains("[src^=\"http"))
    }

    @Test
    fun `vector UI video and advertisements stay isolated`() {
        assertContains(background, "SAFE_UI_MIME_PATTERN")
        assertContains(background, "SAFE_UI_URL_PATTERN")
        assertContains(background, "BLOCKED_RESOURCE_TYPES")
        assertContains(background, "BLOCKED_MEDIA_MIME_PATTERN")
        assertContains(manifest, "\"ads.js\"")
        assertContains(ads, "EXPLICIT_AD_SELECTOR")
        assertContains(ads, "SEARCH_QUERY_KEYS")
        assertContains(ads, "isSearchResultsDocument")
        assertContains(ads, "NodeFilter.SHOW_TEXT")
        assertFalse(ads.contains("MutationObserver"))
        assertFalse(ads.contains("querySelectorAll?.(\"span,div\")"))
        assertFalse(css.contains("[data-ad-slot]"))
        assertContains(css, "glosh-dag-page-ad-hidden")
    }

    @Test
    fun `active protection has no store device or document remapping exceptions`() {
        val activeProtection = "$background\n$barrier\n$css"
        listOf("cheeky", "mimo", "fravega", "sm-a235", "sm-s908").forEach { forbidden ->
            assertFalse(activeProtection.contains(forbidden, ignoreCase = true))
        }
        assertFalse(background.contains("media-document-current"))
        assertFalse(background.contains("media-document-retired"))
        assertFalse(background.contains("media-presentation"))
    }
}
