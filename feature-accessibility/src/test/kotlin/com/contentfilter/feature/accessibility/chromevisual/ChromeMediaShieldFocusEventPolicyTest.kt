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
                rootUniqueId = RootUniqueId,
            ),
            result,
        )
        assertTrue(
            ChromeMediaShieldFocusEventPolicy.verify(
                evidence(foregroundRootUniqueId = "native-chrome-window-root"),
                Claim,
                WindowId,
            ) is ChromeMediaShieldFocusEventResult.Verified,
        )
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
    fun `same Chrome window cannot carry anchor across document root lifecycle or claim`() {
        assertBoundAnchorRejected(boundEvidence(rootUniqueId = "replacement-root"))
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
        sourceRootUniqueId: String = RootUniqueId,
        foregroundRootPackageName: String = ChromePackageName,
        foregroundRootUniqueId: String = RootUniqueId,
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

    private fun boundEvidence(
        windowId: Int = WindowId,
        rootPackageName: String = ChromePackageName,
        rootUniqueId: String = RootUniqueId,
        matchingNodeCount: Int = 1,
        sourcePackageName: String = ChromePackageName,
        sourceWindowId: Int = WindowId,
        sourceViewIdResourceName: String = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + Token,
        sourceUniqueId: String = SourceUniqueId,
        sourceRootUniqueId: String = RootUniqueId,
        sourceAttachedToForegroundRoot: Boolean = true,
        sourceVisibleToUser: Boolean = true,
        markers: List<ChromeMediaShieldReadyMarker> = listOf(Marker),
    ) = ChromeMediaShieldBoundAnchorEvidence(
        windowId = windowId,
        rootPackageName = rootPackageName,
        rootUniqueId = rootUniqueId,
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

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val WindowId = 17
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val OtherToken = "BBBBBBBBBBBBBBBBBBBBBB"
        const val SourceUniqueId = "web-node:ready"
        const val RootUniqueId = "chrome-root:17"
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
                            ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                                "root:$RootUniqueId",
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
                    ),
            )
    }
}
