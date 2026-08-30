package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent
import com.contentfilter.core.domain.chrome.ChromeMediaShieldAccessibilityContext
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldFocusEventPolicyTest {
    @Test
    fun `exact focused source in current Chrome root verifies the ready claim`() {
        val result = ChromeMediaShieldFocusEventPolicy.verify(evidence(), Claim, WindowId)

        assertEquals(
            ChromeMediaShieldFocusEventResult.Verified(
                marker = Marker,
                sourceUniqueId = SourceUniqueId,
                webRootUniqueId = WebRootUniqueId,
                foregroundRootUniqueId = ForegroundRootUniqueId,
            ),
            result,
        )
        assertTrue(WebRootUniqueId != ForegroundRootUniqueId)
    }

    @Test
    fun `event before or after the claimed lifecycle is rejected`() {
        assertRejected("ready_focus_claim_mismatch", evidence(markers = listOf(Marker.copy(lifecycleSequence = 2L))))
        assertRejected("ready_focus_claim_mismatch", evidence(markers = listOf(Marker.copy(lifecycleSequence = 4L))))
    }

    @Test
    fun `stale replay background window and replaced root are rejected`() {
        assertRejected(
            "ready_focus_view_id_mismatch",
            evidence(markers = listOf(ChromeMediaShieldReadyMarker(OtherToken, Marker.lifecycleSequence))),
        )
        assertRejected(
            "ready_focus_claim_mismatch",
            evidence(
                sourceViewIdResourceName = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + OtherToken,
                markers = listOf(ChromeMediaShieldReadyMarker(OtherToken, Marker.lifecycleSequence)),
            ),
        )
        assertRejected("ready_focus_wrong_window", evidence(eventWindowId = WindowId + 1))
        assertRejected("ready_focus_wrong_window", evidence(sourceWindowId = WindowId + 1))
        assertRejected("ready_focus_root_mismatch", evidence(sourceRootUniqueId = ""))
        assertRejected("ready_focus_root_mismatch", evidence(sourceRootUniqueId = ForegroundRootUniqueId))
        assertRejected("ready_focus_root_mismatch", evidence(sourceRootClassName = "android.widget.FrameLayout"))
        assertRejected("ready_focus_root_mismatch", evidence(sourceAttachedToForegroundRoot = false))
    }

    @Test
    fun `only an exact platform focus source can carry authority`() {
        assertRejected(
            "ready_focus_wrong_event",
            evidence(eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED),
        )
        assertRejected("ready_focus_not_chrome", evidence(eventPackageName = "example.hostile"))
        assertRejected("ready_focus_not_chrome", evidence(sourcePackageName = "example.hostile"))
        assertRejected("ready_focus_not_chrome", evidence(foregroundRootPackageName = "example.hostile"))
        assertTrue(
            ChromeMediaShieldFocusEventPolicy.verify(
                evidence(sourceFocused = false),
                Claim,
                WindowId,
            ) is ChromeMediaShieldFocusEventResult.Verified,
        )
        assertTrue(
            ChromeMediaShieldFocusEventPolicy.verify(
                evidence(sourceVisibleToUser = false),
                Claim,
                WindowId,
            ) is ChromeMediaShieldFocusEventResult.Verified,
        )
        assertRejected("ready_focus_root_mismatch", evidence(sourceUniqueId = ""))
        assertRejected("ready_focus_view_id_mismatch", evidence(sourceViewIdResourceName = "forged"))
        assertRejected("ready_focus_marker_ambiguous", evidence(markers = emptyList()))
        assertRejected(
            "ready_focus_marker_ambiguous",
            evidence(
                markers =
                    listOf(
                        Marker,
                        Marker.copy(lifecycleSequence = 4L),
                    ),
            ),
        )
        assertTrue(
            ChromeMediaShieldFocusEventPolicy.verify(
                evidence(foregroundRootUniqueId = ""),
                Claim,
                WindowId,
            ) is ChromeMediaShieldFocusEventResult.Verified,
        )
    }

    @Test
    fun `event-bound anchor remains current only while exact source is in current root`() {
        assertTrue(
            ChromeMediaShieldBoundAnchorPolicy.verifies(
                boundEvidence(),
                Claim,
                Document,
            ),
        )
        assertBoundAnchorRejected(boundEvidence(matchingNodeCount = 0))
        assertBoundAnchorRejected(boundEvidence(matchingNodeCount = 2))
        assertBoundAnchorRejected(boundEvidence(exactAnchorCurrent = false))
        assertBoundAnchorRejected(boundEvidence(sourceUniqueId = "replacement-node"))
        assertBoundAnchorRejected(boundEvidence(sourceViewIdResourceName = "cloned-view-id"))
        assertTrue(
            ChromeMediaShieldBoundAnchorPolicy.verifies(
                boundEvidence(sourceVisibleToUser = false),
                Claim,
                Document,
            ),
        )
    }

    @Test
    fun `release context binds the current native Chrome root independently of the web root`() {
        assertTrue(
            ChromeMediaShieldForegroundContextPolicy.verifies(
                foregroundContextEvidence(),
                Document,
            ),
        )
        assertTrue(WebRootUniqueId != ForegroundRootUniqueId)
        assertForegroundContextRejected(foregroundContextEvidence(nativeRootUniqueId = WebRootUniqueId))
        assertForegroundContextRejected(foregroundContextEvidence(nativeRootUniqueId = "replacement-native-root"))
        assertForegroundContextRejected(foregroundContextEvidence(nativeRootUniqueId = ""))
        assertForegroundContextRejected(foregroundContextEvidence(exactAnchorCurrent = false))
        assertForegroundContextRejected(foregroundContextEvidence(windowId = WindowId + 1))
        assertForegroundContextRejected(foregroundContextEvidence(rootPackageName = "example.hostile"))
    }

    @Test
    fun `missing native root identity falls back only to the exact current web root`() {
        assertTrue(
            ChromeMediaShieldForegroundContextPolicy.verifies(
                foregroundContextEvidence(
                    nativeRootUniqueId = "",
                    exactAnchorCurrent = true,
                ),
                WebRootDocument,
            ),
        )
        assertTrue(
            !ChromeMediaShieldForegroundContextPolicy.verifies(
                foregroundContextEvidence(
                    nativeRootUniqueId = "",
                    exactAnchorCurrent = false,
                ),
                WebRootDocument,
            ),
        )
        assertTrue(
            !ChromeMediaShieldForegroundContextPolicy.verifies(
                foregroundContextEvidence(
                    nativeRootUniqueId = "replacement-native-root",
                    exactAnchorCurrent = false,
                ),
                WebRootDocument,
            ),
        )
        assertTrue(
            ChromeMediaShieldBoundAnchorPolicy.verifies(
                boundEvidence(
                    nativeRootUniqueId = "",
                    exactAnchorCurrent = true,
                ),
                Claim,
                WebRootDocument,
            ),
        )
        assertTrue(
            !ChromeMediaShieldBoundAnchorPolicy.verifies(
                boundEvidence(
                    nativeRootUniqueId = "",
                    exactAnchorCurrent = false,
                ),
                Claim,
                WebRootDocument,
            ),
        )
    }

    @Test
    fun `same Chrome window cannot carry anchor across document root lifecycle or claim`() {
        assertBoundAnchorRejected(boundEvidence(nativeRootUniqueId = "replacement-root"))
        assertBoundAnchorRejected(boundEvidence(sourceRootUniqueId = "background-root"))
        assertBoundAnchorRejected(boundEvidence(windowId = WindowId + 1))
        assertBoundAnchorRejected(
            boundEvidence(markers = listOf(Marker.copy(lifecycleSequence = Marker.lifecycleSequence + 1L))),
        )
        assertBoundAnchorRejected(
            boundEvidence(
                markers =
                    listOf(
                        ChromeMediaShieldReadyMarker(
                            readyToken = OtherToken,
                            lifecycleSequence = Marker.lifecycleSequence,
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `browser issued web root maintains only an already event-bound current document`() {
        assertTrue(
            ChromeMediaShieldWebRootContinuityPolicy.verifies(
                webRootEvidence(),
                Document,
            ),
        )
        assertTrue(
            ChromeMediaShieldWebRootContinuityPolicy.verifies(
                webRootEvidence(nativeRootUniqueId = ""),
                WebRootDocument,
            ),
        )
        assertWebRootRejected(webRootEvidence(webRootUniqueId = "replacement-web-root"))
        assertWebRootRejected(webRootEvidence(webRootWindowId = WindowId + 1))
        assertWebRootRejected(webRootEvidence(webRootPackageName = "example.hostile"))
        assertWebRootRejected(webRootEvidence(webRootClassName = "android.widget.FrameLayout"))
        assertWebRootRejected(webRootEvidence(webRootUniqueId = ForegroundRootUniqueId))
        assertWebRootRejected(webRootEvidence(webRootAttachedToForegroundRoot = false))
        assertWebRootRejected(webRootEvidence(webRootVisibleToUser = false))
        assertWebRootRejected(webRootEvidence(nativeRootUniqueId = "replacement-native-root"))
    }

    @Test
    fun `web root traversal candidate accepts missing native root identity but remains window exact`() {
        assertTrue(
            ChromeMediaShieldWebRootCandidatePolicy.verifies(
                webRootCandidateEvidence(nativeRootUniqueId = null),
            ),
        )
        assertTrue(
            ChromeMediaShieldWebRootCandidatePolicy.verifies(
                webRootCandidateEvidence(nativeRootUniqueId = ""),
            ),
        )
        listOf(
            webRootCandidateEvidence(nativeRootUniqueId = WebRootUniqueId),
            webRootCandidateEvidence(candidateUniqueId = null),
            webRootCandidateEvidence(candidateUniqueId = ""),
            webRootCandidateEvidence(candidatePackageName = "example.hostile"),
            webRootCandidateEvidence(candidateClassName = "android.widget.FrameLayout"),
            webRootCandidateEvidence(candidateWindowId = WindowId + 1),
        ).forEach { evidence ->
            assertTrue(!ChromeMediaShieldWebRootCandidatePolicy.verifies(evidence))
        }
    }

    @Test
    fun `web root continuity can maintain but never create a release boundary`() {
        assertTrue(
            ChromeMediaShieldBoundContextPolicy.select(
                exactFocusSourceCurrent = false,
                exactWebRootCurrent = true,
                requireExactFocusSource = false,
            ) == ChromeMediaShieldBoundContextBinding.ExactWebRoot,
        )
        assertTrue(
            ChromeMediaShieldBoundContextPolicy.select(
                exactFocusSourceCurrent = false,
                exactWebRootCurrent = true,
                requireExactFocusSource = true,
            ) == ChromeMediaShieldBoundContextBinding.Invalid,
        )
        assertTrue(
            ChromeMediaShieldBoundContextPolicy.select(
                exactFocusSourceCurrent = true,
                exactWebRootCurrent = false,
                requireExactFocusSource = true,
            ) == ChromeMediaShieldBoundContextBinding.ExactFocusSource,
        )
    }

    private fun assertRejected(
        reason: String,
        evidence: ChromeMediaShieldFocusEventEvidence,
    ) {
        val result = ChromeMediaShieldFocusEventPolicy.verify(evidence, Claim, WindowId)
        assertTrue(result is ChromeMediaShieldFocusEventResult.Rejected)
        assertEquals(reason, (result as ChromeMediaShieldFocusEventResult.Rejected).reason)
    }

    private fun evidence(
        eventType: Int = AccessibilityEvent.TYPE_VIEW_FOCUSED,
        eventPackageName: String = ChromePackageName,
        eventWindowId: Int = WindowId,
        sourcePackageName: String = ChromePackageName,
        sourceWindowId: Int = WindowId,
        sourceViewIdResourceName: String = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + Token,
        sourceUniqueId: String = SourceUniqueId,
        sourceRootUniqueId: String = WebRootUniqueId,
        sourceRootClassName: String = ChromeMediaShieldWebRootContinuityPolicy.WebRootClassName,
        foregroundRootPackageName: String = ChromePackageName,
        foregroundRootUniqueId: String = ForegroundRootUniqueId,
        sourceAttachedToForegroundRoot: Boolean = true,
        sourceFocused: Boolean = true,
        sourceVisibleToUser: Boolean = true,
        markers: List<ChromeMediaShieldReadyMarker> = listOf(Marker),
    ) = ChromeMediaShieldFocusEventEvidence(
        eventType = eventType,
        eventPackageName = eventPackageName,
        eventWindowId = eventWindowId,
        sourcePackageName = sourcePackageName,
        sourceWindowId = sourceWindowId,
        sourceViewIdResourceName = sourceViewIdResourceName,
        sourceUniqueId = sourceUniqueId,
        sourceRootUniqueId = sourceRootUniqueId,
        sourceRootClassName = sourceRootClassName,
        foregroundRootPackageName = foregroundRootPackageName,
        foregroundRootUniqueId = foregroundRootUniqueId,
        sourceAttachedToForegroundRoot = sourceAttachedToForegroundRoot,
        sourceFocused = sourceFocused,
        sourceVisibleToUser = sourceVisibleToUser,
        markers = markers,
    )

    private fun assertBoundAnchorRejected(evidence: ChromeMediaShieldBoundAnchorEvidence) {
        assertTrue(!ChromeMediaShieldBoundAnchorPolicy.verifies(evidence, Claim, Document))
    }

    private fun assertForegroundContextRejected(evidence: ChromeMediaShieldForegroundContextEvidence) {
        assertTrue(!ChromeMediaShieldForegroundContextPolicy.verifies(evidence, Document))
    }

    private fun assertWebRootRejected(evidence: ChromeMediaShieldWebRootEvidence) {
        assertTrue(!ChromeMediaShieldWebRootContinuityPolicy.verifies(evidence, Document))
    }

    private fun foregroundContextEvidence(
        windowId: Int = WindowId,
        rootPackageName: String = ChromePackageName,
        nativeRootUniqueId: String = ForegroundRootUniqueId,
        exactAnchorCurrent: Boolean = true,
    ) = ChromeMediaShieldForegroundContextEvidence(
        windowId = windowId,
        rootPackageName = rootPackageName,
        nativeRootUniqueId = nativeRootUniqueId,
        exactAnchorCurrent = exactAnchorCurrent,
    )

    private fun boundEvidence(
        windowId: Int = WindowId,
        rootPackageName: String = ChromePackageName,
        nativeRootUniqueId: String = ForegroundRootUniqueId,
        exactAnchorCurrent: Boolean = true,
        matchingNodeCount: Int = 1,
        sourcePackageName: String = ChromePackageName,
        sourceWindowId: Int = WindowId,
        sourceViewIdResourceName: String = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + Token,
        sourceUniqueId: String = SourceUniqueId,
        sourceRootUniqueId: String = WebRootUniqueId,
        sourceAttachedToForegroundRoot: Boolean = true,
        sourceVisibleToUser: Boolean = true,
        markers: List<ChromeMediaShieldReadyMarker> = listOf(Marker),
    ) = ChromeMediaShieldBoundAnchorEvidence(
        windowId = windowId,
        rootPackageName = rootPackageName,
        nativeRootUniqueId = nativeRootUniqueId,
        exactAnchorCurrent = exactAnchorCurrent,
        matchingNodeCount = matchingNodeCount,
        sourcePackageName = sourcePackageName,
        sourceWindowId = sourceWindowId,
        sourceViewIdResourceName = sourceViewIdResourceName,
        sourceUniqueId = sourceUniqueId,
        sourceRootUniqueId = sourceRootUniqueId,
        sourceAttachedToForegroundRoot = sourceAttachedToForegroundRoot,
        sourceVisibleToUser = sourceVisibleToUser,
        markers = markers,
    )

    private fun webRootEvidence(
        windowId: Int = WindowId,
        rootPackageName: String = ChromePackageName,
        nativeRootUniqueId: String = ForegroundRootUniqueId,
        webRootPackageName: String = ChromePackageName,
        webRootClassName: String = ChromeMediaShieldWebRootContinuityPolicy.WebRootClassName,
        webRootWindowId: Int = WindowId,
        webRootUniqueId: String = WebRootUniqueId,
        webRootAttachedToForegroundRoot: Boolean = true,
        webRootVisibleToUser: Boolean = true,
    ) = ChromeMediaShieldWebRootEvidence(
        windowId = windowId,
        rootPackageName = rootPackageName,
        nativeRootUniqueId = nativeRootUniqueId,
        webRootPackageName = webRootPackageName,
        webRootClassName = webRootClassName,
        webRootWindowId = webRootWindowId,
        webRootUniqueId = webRootUniqueId,
        webRootAttachedToForegroundRoot = webRootAttachedToForegroundRoot,
        webRootVisibleToUser = webRootVisibleToUser,
    )

    private fun webRootCandidateEvidence(
        expectedWindowId: Int = WindowId,
        nativeRootUniqueId: String? = ForegroundRootUniqueId,
        candidatePackageName: String = ChromePackageName,
        candidateClassName: String = ChromeMediaShieldWebRootContinuityPolicy.WebRootClassName,
        candidateWindowId: Int = WindowId,
        candidateUniqueId: String? = WebRootUniqueId,
    ) = ChromeMediaShieldWebRootCandidateEvidence(
        expectedWindowId = expectedWindowId,
        nativeRootUniqueId = nativeRootUniqueId,
        candidatePackageName = candidatePackageName,
        candidateClassName = candidateClassName,
        candidateWindowId = candidateWindowId,
        candidateUniqueId = candidateUniqueId,
    )

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val WindowId = 17
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val OtherToken = "BBBBBBBBBBBBBBBBBBBBBB"
        const val SourceUniqueId = "web-node:ready"
        const val WebRootUniqueId = "chrome-web-root:17"
        const val ForegroundRootUniqueId = "chrome-native-root:17"
        val Marker = ChromeMediaShieldReadyMarker(Token, 3L)
        val Claim =
            ChromeMediaShieldReadyClaim(
                identity =
                    ChromeMediaShieldDocumentIdentity(
                        protectionSessionId = "h19-session",
                        policyEpoch = 19L,
                        navigationSequence = 1L,
                        documentSequence = 1L,
                        tokenDigest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(Token),
                        topLevel = true,
                    ),
                lifecycleSequence = Marker.lifecycleSequence,
            )
        val Document =
            ChromeMediaShieldForegroundDocument(
                identity = Claim.identity,
                lifecycleSequence = Claim.lifecycleSequence,
                windowId = WindowId,
                accessibilityContext =
                    ChromeMediaShieldAccessibilityContext(
                        windowId = WindowId,
                        rootIdentityDigest =
                            checkNotNull(
                                ChromeMediaShieldForegroundContextPolicy.bindingDigest(
                                    ForegroundRootUniqueId,
                                    WebRootUniqueId,
                                ),
                            ),
                        markerIdentityDigest =
                            ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                                "focus:$SourceUniqueId:${ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix}$Token",
                            ),
                    ),
                focusAnchor =
                    ChromeMediaShieldFocusAnchor(
                        viewIdResourceName = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + Token,
                        sourceUniqueId = SourceUniqueId,
                        webRootUniqueId = WebRootUniqueId,
                    ),
            )
        val WebRootDocument =
            Document.copy(
                accessibilityContext =
                    Document.accessibilityContext.copy(
                        rootIdentityDigest =
                            checkNotNull(
                                ChromeMediaShieldForegroundContextPolicy.bindingDigest(
                                    "",
                                    WebRootUniqueId,
                                ),
                            ),
                    ),
            )
    }
}
