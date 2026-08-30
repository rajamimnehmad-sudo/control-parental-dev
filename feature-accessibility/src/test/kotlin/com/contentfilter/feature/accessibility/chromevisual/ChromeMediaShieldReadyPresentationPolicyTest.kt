package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldAccessibilityContext
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldReadyPresentationPolicyTest {
    @Test
    fun `only current H19 attestation is eligible for a ready claim`() {
        val claim = claim()
        val valid = attestation()

        assertTrue(ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(claim, valid, Now))
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                claim.copy(identity = claim.identity.copy(topLevel = false)),
                valid,
                Now,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                claim,
                valid.copy(sessionId = "other", vpnSessionId = "other"),
                Now,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                claim,
                valid.copy(mediaPolicyEpoch = PolicyEpoch + 1L),
                Now,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.acceptsAttestation(
                claim,
                valid.copy(validUntilElapsed = Now),
                Now,
            ),
        )
    }

    @Test
    fun `release boundary requires exact surface window viewport document and lifecycle`() {
        val claim = claim()
        val viewport = ChromeVisualViewport(0, 40, 1080, 2200)
        val snapshot = snapshot(viewport)
        val target = ChromeMediaShieldReadyPresentationTarget(claim, snapshot, opaqueCommitted = true)
        val document = document(claim)

        assertTrue(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot,
                WindowId,
                viewport,
                document,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target.copy(opaqueCommitted = false),
                snapshot,
                WindowId,
                viewport,
                document,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot.copy(epoch = snapshot.epoch + 1L),
                WindowId,
                viewport,
                document,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot,
                WindowId + 1,
                viewport,
                document,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot,
                WindowId,
                viewport.copy(bottom = viewport.bottom - 1),
                document,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot,
                WindowId,
                viewport,
                document.copy(lifecycleSequence = claim.lifecycleSequence + 1L),
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isExactBoundary(
                target,
                snapshot,
                WindowId,
                viewport,
                document.copy(identity = document.identity.copy(documentSequence = 2L)),
            ),
        )
    }

    @Test
    fun `current covered claim awaits its marker without invalidating the synchronous bootstrap`() {
        val claim = claim()
        val viewport = ChromeVisualViewport(0, 40, 1080, 2200)
        val snapshot = snapshot(viewport)
        val target = ChromeMediaShieldReadyPresentationTarget(claim, snapshot)

        assertTrue(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target,
                currentSurface = snapshot,
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = true,
                released = false,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target,
                currentSurface = snapshot.copy(epoch = snapshot.epoch + 1L),
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = true,
                released = false,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target,
                currentSurface = snapshot,
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = true,
                released = false,
                hasLease = true,
                surfaceTransparent = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target,
                currentSurface = snapshot,
                windowId = WindowId + 1,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = true,
                released = false,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target,
                currentSurface = snapshot,
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = false,
                completionPending = true,
                released = false,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target.copy(opaqueCommitted = true),
                currentSurface = snapshot,
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = false,
                released = true,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
        assertTrue(
            ChromeMediaShieldReadyPresentationPolicy.isAwaitingCurrentMarker(
                target = target.copy(opaqueCommitted = true),
                currentSurface = snapshot,
                windowId = WindowId,
                viewport = viewport,
                claimCurrent = true,
                attestationAccepted = true,
                completionPending = false,
                released = false,
                hasLease = false,
                surfaceTransparent = false,
            ),
        )
    }

    @Test
    fun `released presentation is revoked immediately when an accessibility observation no longer verifies it`() {
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.requiresImmediateRevocation(
                released = false,
                presentationStillVerified = false,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.requiresImmediateRevocation(
                released = true,
                presentationStillVerified = true,
            ),
        )
        assertTrue(
            ChromeMediaShieldReadyPresentationPolicy.requiresImmediateRevocation(
                released = true,
                presentationStillVerified = false,
            ),
        )
    }

    @Test
    fun `post commit release requires the same unique foreground window`() {
        assertTrue(
            ChromeMediaShieldReadyPresentationPolicy.isCurrentPostCommitWindow(
                expectedWindowId = WindowId,
                currentWindowId = WindowId,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isCurrentPostCommitWindow(
                expectedWindowId = WindowId,
                currentWindowId = WindowId + 1,
            ),
        )
        assertFalse(
            ChromeMediaShieldReadyPresentationPolicy.isCurrentPostCommitWindow(
                expectedWindowId = WindowId,
                currentWindowId = null,
            ),
        )
    }

    private fun claim() =
        ChromeMediaShieldReadyClaim(
            identity =
                ChromeMediaShieldDocumentIdentity(
                    protectionSessionId = Session,
                    policyEpoch = PolicyEpoch,
                    navigationSequence = 1L,
                    documentSequence = 1L,
                    tokenDigest = "a".repeat(64),
                    topLevel = true,
                ),
            lifecycleSequence = 3L,
        )

    private fun document(claim: ChromeMediaShieldReadyClaim) =
        ChromeMediaShieldForegroundDocument(
            identity = claim.identity,
            lifecycleSequence = claim.lifecycleSequence,
            windowId = WindowId,
            accessibilityContext =
                ChromeMediaShieldAccessibilityContext(
                    windowId = WindowId,
                    rootIdentityDigest = "b".repeat(64),
                    markerIdentityDigest = "c".repeat(64),
                ),
            focusAnchor =
                ChromeMediaShieldFocusAnchor(
                    viewIdResourceName = "glosh-h19-ready-redacted",
                    sourceUniqueId = "web-node:ready",
                    webRootUniqueId = "web-root:ready",
                ),
        )

    private fun snapshot(viewport: ChromeVisualViewport) =
        ChromePhotosProtectedSurfaceSnapshot(
            phase = ChromePhotosProtectedSurfacePhase.Covered,
            epoch = 7L,
            windowId = WindowId,
            viewport = viewport,
            activeSequence = 0L,
            presentedSequence = 0L,
        )

    private fun attestation() =
        ChromePhotosDataPlaneAttestation(
            devBuild = true,
            sessionId = Session,
            active = true,
            proxyHealthy = true,
            policyConfirmed = true,
            vpnConfirmed = true,
            vpnSessionId = Session,
            fixtureConfirmed = true,
            realWebScopeConfirmed = true,
            heartbeatElapsed = Now - 1L,
            validUntilElapsed = Now + 1_000L,
            accessibilityBound = true,
            mediaAuthorityEnabled = true,
            mediaPolicyEpoch = PolicyEpoch,
        )

    private companion object {
        const val Session = "h19-session"
        const val PolicyEpoch = 19L
        const val WindowId = 17
        const val Now = 1_000L
    }
}
