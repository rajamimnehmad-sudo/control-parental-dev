package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagWebInteractionPolicyTest {
    @Test
    fun `sanitizer installation is coordinated by native document revision`() {
        val state = DagSanitizerDocumentState()
        val revision = state.onNewDocument()

        assertEquals(DagSanitizerRequestAction.Install, state.request(revision))
        assertEquals(DagSanitizerRequestAction.WaitForInstall, state.request(revision))
        assertTrue(state.completeInstall(revision, succeeded = true))
        assertEquals(DagSanitizerRequestAction.Extract, state.request(revision))

        val nextRevision = state.onNewDocument()
        assertFalse(state.completeInstall(revision, succeeded = true))
        assertEquals(DagSanitizerRequestAction.Install, state.request(nextRevision))
    }

    @Test
    fun `blank SPA shell retries are bounded before returning uncertainty`() {
        repeat(DagTextExtractionRetryPolicy.MaximumRetries) { attempt ->
            assertTrue(
                dagShouldRetryTextExtraction(
                    text = "",
                    attempt = attempt,
                    isAttachedToWindow = true,
                ),
            )
        }
        assertFalse(
            dagShouldRetryTextExtraction(
                text = "",
                attempt = DagTextExtractionRetryPolicy.MaximumRetries,
                isAttachedToWindow = true,
            ),
        )
        assertFalse(
            dagShouldRetryTextExtraction(
                text = "Contenido listo",
                attempt = 0,
                isAttachedToWindow = true,
            ),
        )
        assertFalse(
            dagShouldRetryTextExtraction(
                text = "",
                attempt = 0,
                isAttachedToWindow = false,
            ),
        )
    }

    @Test
    fun `content mutation policy throttles normal work without delaying urgent work`() {
        assertTrue(DagContentMutationPolicy.NormalThrottleMillis >= 5_000L)
        assertTrue(DagContentMutationPolicy.NormalDebounceMillis < DagContentMutationPolicy.NormalThrottleMillis)
        assertTrue(DagContentMutationPolicy.UrgentDelayMillis <= 50L)
    }

    @Test
    fun `urgent mutation pattern covers every unsafe classifier category`() {
        val pattern = DagDynamicRiskJavaScriptPattern.toRegex(RegexOption.IGNORE_CASE)

        listOf("pornografía", "dating app", "apuestas deportivas", "cannabis", "violencia", "הימורים")
            .forEach { text -> assertTrue(pattern.containsMatchIn(text), text) }
    }

    @Test
    fun `bridge payload cap counts UTF-8 bytes before JSON parsing`() {
        assertTrue(dagWebMessagePayloadAllowed("12345678", maximumBytes = 8))
        assertFalse(dagWebMessagePayloadAllowed("123456789", maximumBytes = 8))
        assertFalse(dagWebMessagePayloadAllowed("ééééé", maximumBytes = 8))
    }

    @Test
    fun `bridge rate limiter resets only after its window`() {
        var now = 0L
        val limiter = DagWebMessageRateLimiter(maximumMessages = 2, windowMillis = 100L) { now }

        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        now = 99L
        assertFalse(limiter.tryAcquire())
        now = 100L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun `visibility attributes use the lightweight text observer instead of the security observer`() {
        assertEquals(
            setOf("class", "style", "hidden", "open"),
            DagObservedVisibilityAttributes.toSet(),
        )
        assertTrue(DagObservedVisibilityAttributes.none(DagObservedSecurityAttributes::contains))
        assertTrue("src" in DagObservedSecurityAttributes)
        assertTrue("srcset" in DagObservedSecurityAttributes)
        assertTrue("data-src" in DagObservedSecurityAttributes)
        assertTrue("href" in DagObservedSecurityAttributes)
        assertTrue("rel" in DagObservedSecurityAttributes)
        assertTrue("media" in DagObservedSecurityAttributes)
        assertTrue("disabled" in DagObservedSecurityAttributes)
        assertTrue("xlink:href" in DagObservedSecurityAttributes)
        assertTrue("data" in DagObservedSecurityAttributes)
    }

    @Test
    fun `sanitizer observers survive documentElement replaceWith`() {
        // The Document is stable when a SPA replaces its <html> element (and
        // across document.open), whereas an observer attached to the old root
        // becomes detached.
        assertEquals("document", DagSanitizerObserverTargetJavaScript)
        assertEquals("node === document.documentElement", DagDocumentRootReplacementJavaScript)
    }

    @Test
    fun `image source invalidation clears orphaned priority pending state`() {
        assertTrue("data-dag-priority-source" in DagImageSourceMutationResetAttributes)
        assertTrue("data-dag-priority-queued" in DagImageSourceMutationResetAttributes)
        assertTrue("data-dag-image-priority-pending" in DagImageSourceMutationResetAttributes)
        assertTrue("data-dag-image-ready" in DagImageSourceMutationResetAttributes)
        assertTrue("data-dag-image-terminal" in DagImageSourceMutationResetAttributes)
    }

    @Test
    fun `dynamic DOM security work is split into bounded frame batches`() {
        assertEquals(0, dagDomSecurityBatchCount(0))
        assertEquals(1, dagDomSecurityBatchCount(1))
        assertEquals(1, dagDomSecurityBatchCount(DagDomSecurityBatchPolicy.MaximumNodesPerFrame))
        assertEquals(2, dagDomSecurityBatchCount(DagDomSecurityBatchPolicy.MaximumNodesPerFrame + 1))
        assertEquals(21, dagDomSecurityBatchCount(1_000))
    }

    @Test
    fun `initial and dynamically inserted style data backgrounds stay fail closed`() {
        val cssDataUri = """url("data:image/png;base64,iVBORw0KGgo=")"""
        val complexSvgDataUri =
            """linear-gradient(#fff,#000),url("data:image/svg+xml,<svg viewBox='0 0 2 2'></svg>")"""

        assertTrue(dagCssBackgroundRequiresFailClosed(cssDataUri))
        assertTrue(dagCssBackgroundRequiresFailClosed(complexSvgDataUri))
        assertFalse(dagCssBackgroundRequiresFailClosed("""url("https://cdn.example/safe.png")"""))
        assertTrue(DagBackgroundSecurityPolicy.InitialScanRunsSynchronously)
        mapOf(
            "dynamic <style>" to DagBackgroundSecurityPolicy.StylesheetScanRunsSynchronously,
            "added subtree" to DagBackgroundSecurityPolicy.AddedSubtreeScanRunsSynchronously,
            "visibility target" to DagBackgroundSecurityPolicy.VisibilityTargetScanRunsSynchronously,
        ).forEach { (scenario, runsSynchronously) ->
            assertFalse(runsSynchronously, scenario)
        }
        assertEquals(
            9,
            DagBackgroundSecurityPolicy.VisibleSampleColumns * DagBackgroundSecurityPolicy.VisibleSampleRows,
        )
        assertTrue(
            DagBackgroundSecurityPolicy.MaximumVisibleNodesPerSynchronousPass <=
                DagDomSecurityBatchPolicy.MaximumNodesPerFrame,
        )
        assertTrue(DagBackgroundSecurityPolicy.VisibleSampleMakesViewportReady)
        assertEquals(48, DagDomSecurityBatchPolicy.MaximumNodesPerFrame)
        assertTrue(DagBackgroundSecurityPolicy.PseudoElementUrlsRequireFailClosed)
        assertTrue(DagBlockedPseudoBackgroundCss.contains(DagBlockedBeforeBackgroundAttribute))
        assertTrue(DagBlockedPseudoBackgroundCss.contains(DagBlockedAfterBackgroundAttribute))
        assertTrue(
            DagBlockedPseudoBackgroundCss.contains(
                "html[$DagBlockedBeforeBackgroundAttribute=\"true\"]::before",
            ),
        )
        assertTrue(
            DagBlockedPseudoBackgroundCss.contains(
                "html body[$DagBlockedAfterBackgroundAttribute=\"true\"]::after",
            ),
        )
    }

    @Test
    fun `document start CSP blocks non HTTPS image schemes before page resources`() {
        assertEquals("img-src https:", DagImageSchemeCspContent)
        listOf("data:", "blob:", "file:", "content:", "javascript:").forEach { unsafeScheme ->
            assertFalse(DagImageSchemeCspContent.contains(unsafeScheme))
        }
        assertTrue(DagDocumentStartScript.contains("dagPolicyObserver.observe(document"))
        assertTrue(
            DagDocumentStartScript.indexOf("dagInstallImagePolicyAtDocumentStart") <
                DagDocumentStartScript.indexOf("window.URL.createObjectURL"),
        )
        val completePolicyIndex =
            DagDocumentStartScript.indexOf(
                "dagImagePolicy.setAttribute('content', '$DagImageSchemeCspContent')",
            )
        val insertPolicyIndex =
            DagDocumentStartScript.indexOf("dagPolicyParent.insertBefore(dagImagePolicy")
        assertTrue(completePolicyIndex < insertPolicyIndex)
        assertFalse(DagDocumentStartScript.contains("document.write"))
        assertFalse(DagDocumentStartScript.contains("document.createElement('head')"))
        assertFalse(DagDocumentStartScript.contains("__dagCssomBackgroundGuardsInstalled"))
        assertFalse(DagDocumentStartScript.contains("dagSetBackgroundGuard"))
    }

    @Test
    fun `navigation fails closed when document start security is unavailable`() {
        assertTrue(documentStartSecurityAvailable(featureSupported = true))
        assertFalse(documentStartSecurityAvailable(featureSupported = false))
        assertTrue(DagDocumentStartSecurityUnsupportedMessage.contains("WebView"))
    }

    @Test
    fun `stylesheet link changes request bounded background rescans without visual holds`() {
        assertEquals(
            setOf("href", "rel", "media", "disabled"),
            DagStylesheetLinkMutationAttributes.toSet(),
        )
        assertTrue(DagStylesheetLinkMutationAttributes.all(DagObservedSecurityAttributes::contains))
        assertTrue(DagBackgroundSecurityPolicy.StylesheetLoadRescanTimeoutMillis in 1_000L..15_000L)
    }

    @Test
    fun `each browser image channel stays within the native delivery bound`() {
        assertTrue(
            DagImageRequestPolicy.MaximumConcurrentSyntheticRequests <=
                DagImageDeliveryPolicy.MaximumConcurrentImages,
        )
        assertTrue(
            DagImageRequestPolicy.MaximumConcurrentBackgroundRequests <=
                DagImageDeliveryPolicy.MaximumConcurrentImages,
        )
    }

    @Test
    fun `image fast lane is bounded to six safe broker candidates per document`() {
        assertEquals(6, DagImageFastLanePolicy.MaximumImagesPerDocument)
        repeat(DagImageFastLanePolicy.MaximumImagesPerDocument) { selected ->
            assertTrue(
                dagImageFastLaneEligible(
                    sourceIsHttps = true,
                    loadingAttribute = if (selected % 2 == 0) "auto" else "eager",
                    hasRenderableGeometry = false,
                    fromDynamicSubtree = false,
                    styleAllowsLoading = true,
                    alreadySelected = selected,
                ),
            )
        }
        assertFalse(
            dagImageFastLaneEligible(
                sourceIsHttps = true,
                loadingAttribute = "auto",
                hasRenderableGeometry = false,
                fromDynamicSubtree = false,
                styleAllowsLoading = true,
                alreadySelected = DagImageFastLanePolicy.MaximumImagesPerDocument,
            ),
        )
    }

    @Test
    fun `image fast lane rejects lazy insecure and hidden resources`() {
        fun eligible(
            sourceIsHttps: Boolean = true,
            loading: String? = null,
            styleAllowsLoading: Boolean = true,
        ) = dagImageFastLaneEligible(
            sourceIsHttps = sourceIsHttps,
            loadingAttribute = loading,
            hasRenderableGeometry = false,
            fromDynamicSubtree = false,
            styleAllowsLoading = styleAllowsLoading,
            alreadySelected = 0,
        )

        assertFalse(eligible(sourceIsHttps = false))
        assertFalse(eligible(loading = "lazy"))
        assertFalse(eligible(styleAllowsLoading = false))
        assertTrue(eligible(loading = null))
    }

    @Test
    fun `dynamic subtree can fast lane its first renderable eager image`() {
        assertTrue(
            dagImageFastLaneEligible(
                sourceIsHttps = true,
                loadingAttribute = "eager",
                hasRenderableGeometry = true,
                fromDynamicSubtree = true,
                styleAllowsLoading = true,
                alreadySelected = 5,
            ),
        )
        assertFalse(
            dagImageFastLaneEligible(
                sourceIsHttps = true,
                loadingAttribute = "eager",
                hasRenderableGeometry = true,
                fromDynamicSubtree = false,
                styleAllowsLoading = true,
                alreadySelected = 5,
            ),
        )
    }

    @Test
    fun `removed fast lane image refunds its slot once and only while disconnected`() {
        var remaining = 0
        remaining =
            dagImageFastLaneRemainingAfterRelease(
                currentRemaining = remaining,
                wasTrackedFastLaneImage = true,
                isConnected = false,
            )
        assertEquals(1, remaining)

        remaining =
            dagImageFastLaneRemainingAfterRelease(
                currentRemaining = remaining,
                // WeakSet.delete returns false on a duplicate release.
                wasTrackedFastLaneImage = false,
                isConnected = false,
            )
        assertEquals(1, remaining)
        assertEquals(
            1,
            dagImageFastLaneRemainingAfterRelease(
                currentRemaining = remaining,
                wasTrackedFastLaneImage = true,
                isConnected = true,
            ),
        )
        assertEquals(
            DagImageFastLanePolicy.MaximumImagesPerDocument,
            dagImageFastLaneRemainingAfterRelease(
                currentRemaining = DagImageFastLanePolicy.MaximumImagesPerDocument,
                wasTrackedFastLaneImage = true,
                isConnected = false,
            ),
        )
    }
}
