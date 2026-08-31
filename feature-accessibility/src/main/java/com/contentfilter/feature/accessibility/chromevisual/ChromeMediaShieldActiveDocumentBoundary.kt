package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import java.util.concurrent.atomic.AtomicBoolean

internal data class ChromeMediaShieldActiveDocumentNativeBinding(
    val windowId: Int,
    val viewport: ChromeVisualViewport,
    val nativeRootDigest: String,
    val nativeRootBindingKind: ChromeMediaShieldNativeRootBindingKind,
)

internal enum class ChromeMediaShieldActiveDocumentAttemptStage {
    HelloAccepted,
    AwaitingOpaque,
    Challenged,
    Proved,
    Held,
    Committing,
    Released,
}

/** Prevents an already-cancelled bridge completion from creating native presentation state. */
internal class ChromeMediaShieldActiveDocumentDispatchGuard {
    private val cancelled = AtomicBoolean(false)

    fun cancel(): Boolean = cancelled.compareAndSet(false, true)

    fun isCurrent(): Boolean = !cancelled.get()

    fun mayDispatch(registration: ChromeMediaShieldActiveDocumentTransportCancellationRegistration): Boolean =
        registration == ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered &&
            isCurrent()
}

/**
 * Couples one bridge completion to the atomic lifetime observed by the native presentation path.
 *
 * Transport timeout/supersession marks [dispatchGuard] cancelled on the bridge thread before any
 * main-thread cleanup is queued. Every completion method therefore becomes inert immediately, and
 * the SurfaceControl commit boundary can reject a callback that races ahead of that cleanup.
 */
internal class ChromeMediaShieldActiveDocumentGuardedCompletion(
    private val delegate: ChromeMediaShieldActiveDocumentHandshakeCompletion,
    val dispatchGuard: ChromeMediaShieldActiveDocumentDispatchGuard =
        ChromeMediaShieldActiveDocumentDispatchGuard(),
) : ChromeMediaShieldActiveDocumentHandshakeCompletion {
    fun cancelTransport(): Boolean = dispatchGuard.cancel()

    fun isTransportCurrent(): Boolean = dispatchGuard.isCurrent()

    override fun onTransportCancelled(
        callback: () -> Unit,
    ): ChromeMediaShieldActiveDocumentTransportCancellationRegistration = delegate.onTransportCancelled(callback)

    override fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge): Boolean =
        isTransportCurrent() && delegate.issueChallenge(challenge)

    override fun acceptProof(): Boolean = isTransportCurrent() && delegate.acceptProof()

    override fun acceptPresentation(): Boolean = isTransportCurrent() && delegate.acceptPresentation()

    override fun acceptRevocation(): Boolean = isTransportCurrent() && delegate.acceptRevocation()

    override fun reject(): Boolean = delegate.reject()
}

internal object ChromeMediaShieldActiveDocumentTransportBoundaryPolicy {
    fun isCurrent(
        expected: ChromeMediaShieldActiveDocumentGuardedCompletion?,
        observed: ChromeMediaShieldActiveDocumentGuardedCompletion,
    ): Boolean = expected === observed && observed.isTransportCurrent()
}

internal object ChromeMediaShieldActiveDocumentBoundaryPolicy {
    fun acceptsRequest(
        expectedClaim: ChromeMediaShieldReadyClaim,
        expectedChallenge: ChromeMediaShieldActiveDocumentChallenge?,
        expectedStage: ChromeMediaShieldActiveDocumentAttemptStage,
        request: ChromeMediaShieldActiveDocumentRequest,
    ): Boolean {
        if (request.claim != expectedClaim) return false
        return when (request) {
            is ChromeMediaShieldActiveDocumentRequest.Hello -> false
            is ChromeMediaShieldActiveDocumentRequest.Prove ->
                expectedStage == ChromeMediaShieldActiveDocumentAttemptStage.Challenged &&
                    request.challenge == expectedChallenge
            is ChromeMediaShieldActiveDocumentRequest.Present ->
                expectedStage == ChromeMediaShieldActiveDocumentAttemptStage.Proved &&
                    request.challenge == expectedChallenge
            is ChromeMediaShieldActiveDocumentRequest.Revoke ->
                expectedChallenge != null &&
                    request.challenge == expectedChallenge
        }
    }

    fun isExactBoundary(
        expected: ChromeMediaShieldActiveDocumentNativeBinding,
        observed: ChromeMediaShieldActiveDocumentNativeBinding?,
        expectedSurface: ChromePhotosProtectedSurfaceSnapshot,
        currentSurface: ChromePhotosProtectedSurfaceSnapshot,
        claimCurrent: Boolean,
        attestationCurrent: Boolean,
    ): Boolean =
        claimCurrent &&
            attestationCurrent &&
            expected == observed &&
            expectedSurface == currentSurface &&
            expectedSurface.windowId == expected.windowId &&
            expectedSurface.viewport == expected.viewport

    fun proofRejectionReason(
        currentClaim: ChromeMediaShieldReadyClaim?,
        currentChallenge: ChromeMediaShieldActiveDocumentChallenge?,
        currentStage: ChromeMediaShieldActiveDocumentAttemptStage?,
        request: ChromeMediaShieldActiveDocumentRequest.Prove,
    ): String =
        when {
            currentClaim != request.claim -> "prove_replay"
            currentChallenge != request.challenge -> "prove_challenge_invalid"
            currentStage != ChromeMediaShieldActiveDocumentAttemptStage.Challenged -> "prove_replay"
            else -> "prove_context_changed"
        }

    fun presentRejectionReason(
        currentClaim: ChromeMediaShieldReadyClaim?,
        currentChallenge: ChromeMediaShieldActiveDocumentChallenge?,
        currentStage: ChromeMediaShieldActiveDocumentAttemptStage?,
        request: ChromeMediaShieldActiveDocumentRequest.Present,
    ): String =
        when {
            currentClaim != request.claim -> "present_replay"
            currentChallenge != request.challenge -> "present_replay"
            currentStage != ChromeMediaShieldActiveDocumentAttemptStage.Proved -> "present_not_proved"
            else -> "present_context_changed"
        }
}

/**
 * Reads only Chrome's unique foreground application window and native root.
 *
 * A parser-blocking document cannot wait for Chrome to publish an AX WebView: that publication is
 * renderer progress which the parser barrier itself prevents. The document half of authority is
 * therefore the proxy-issued capability + lifecycle; this reader supplies only the native
 * window/root/surface half. Page text, view IDs, WebView nodes and READY markers are never read.
 */
internal class ChromeMediaShieldActiveDocumentContextReader(
    private val windowInspector: ChromeVisualWindowInspector,
) : AutoCloseable {
    private val nativeRootAnchor =
        ChromeMediaShieldNativeRootAnchor<AccessibilityNodeInfo>(
            copy = ::copyNode,
            refresh = AccessibilityNodeInfo::refresh,
            sameNode = AccessibilityNodeInfo::equals,
            closeResource = ChromeMediaShieldAccessibilityNodeTraversal::recycle,
        )

    fun currentBinding(expectedWindowId: Int? = null): ChromeMediaShieldActiveDocumentNativeBinding? =
        (readCurrent(expectedWindowId) as? ChromeMediaShieldActiveDocumentContextReadResult.Found)?.binding

    fun readCurrent(expectedWindowId: Int? = null): ChromeMediaShieldActiveDocumentContextReadResult =
        runCatching { readResult(expectedWindowId) }
            .getOrElse { unavailable("context_read_exception") }

    private fun readResult(expectedWindowId: Int?): ChromeMediaShieldActiveDocumentContextReadResult {
        val window =
            windowInspector.findUniqueForegroundCandidate(expectedWindowId)
                ?: return unavailable("foreground_window_unavailable")
        val root = window.root ?: return unavailable("foreground_root_unavailable")
        try {
            if (root.packageName?.toString() != ChromePackageName) {
                return unavailable("foreground_root_package_mismatch")
            }
            if (root.windowId != window.id) return unavailable("foreground_root_window_mismatch")
            val nativeIdentity =
                nativeRootIdentity(window.id, root)
                    ?: return unavailable("foreground_root_identity_unavailable")
            val viewport = viewport(root) ?: return unavailable("foreground_viewport_invalid")
            return ChromeMediaShieldActiveDocumentContextReadResult.Found(
                binding(window.id, viewport, nativeIdentity),
            )
        } finally {
            ChromeMediaShieldAccessibilityNodeTraversal.recycle(root)
        }
    }

    private fun binding(
        windowId: Int,
        viewport: ChromeVisualViewport,
        nativeIdentity: ChromeMediaShieldNativeRootIdentity,
    ) = ChromeMediaShieldActiveDocumentNativeBinding(
        windowId = windowId,
        viewport = viewport,
        nativeRootDigest = digest(nativeIdentity.value),
        nativeRootBindingKind = nativeIdentity.kind,
    )

    private fun nativeRootIdentity(
        windowId: Int,
        root: AccessibilityNodeInfo,
    ): ChromeMediaShieldNativeRootIdentity? =
        nativeRootAnchor.identify(
            borrowedRoot = root,
            windowId = windowId,
            platformUniqueId = ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(root),
        )

    override fun close() = nativeRootAnchor.close()

    @Suppress("DEPRECATION")
    private fun copyNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain(node)

    private fun viewport(root: AccessibilityNodeInfo): ChromeVisualViewport? {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return ChromeVisualViewport(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .takeIf { it.width > 0 && it.height > 0 }
    }

    private fun unavailable(reason: String) = ChromeMediaShieldActiveDocumentContextReadResult.Unavailable(reason)

    private fun digest(value: String): String = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(value)

    private companion object {
        const val ChromePackageName = "com.android.chrome"
    }
}
