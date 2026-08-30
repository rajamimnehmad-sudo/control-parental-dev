package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.contentfilter.core.domain.chrome.ChromeMediaShieldAccessibilityContext
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

internal data class ChromeMediaShieldForegroundDocument(
    val identity: ChromeMediaShieldDocumentIdentity,
    val lifecycleSequence: Long,
    val windowId: Int,
    val accessibilityContext: ChromeMediaShieldAccessibilityContext,
    val focusAnchor: ChromeMediaShieldFocusAnchor,
)

internal data class ChromeMediaShieldFocusAnchor(
    val viewIdResourceName: String,
    val sourceUniqueId: String,
    val webRootUniqueId: String,
) {
    override fun toString(): String = "ChromeMediaShieldFocusAnchor(redacted)"
}

internal data class ChromeMediaShieldReadyMarker(
    val readyToken: String,
    val lifecycleSequence: Long,
) {
    override fun toString(): String = "ChromeMediaShieldReadyMarker(redacted,lifecycle=$lifecycleSequence)"
}

internal sealed interface ChromeMediaShieldTokenScanResult {
    data class Current(
        val document: ChromeMediaShieldForegroundDocument,
    ) : ChromeMediaShieldTokenScanResult

    data class FailClosed(
        val reason: String,
    ) : ChromeMediaShieldTokenScanResult
}

internal data class ChromeMediaShieldFocusEventEvidence(
    val eventType: Int,
    val contentChangeTypes: Int,
    val eventPackageName: String,
    val eventWindowId: Int,
    val sourcePackageName: String,
    val sourceWindowId: Int,
    val sourceViewIdResourceName: String,
    val sourceClassName: String,
    val sourceUniqueId: String,
    val sourceRootUniqueId: String,
    val sourceRootClassName: String,
    val sourceRootVisibleToUser: Boolean,
    val foregroundRootPackageName: String,
    val foregroundRootUniqueId: String,
    val sourceAttachedToForegroundRoot: Boolean,
    val sourceFocusable: Boolean,
    val sourceFocused: Boolean,
    val sourceVisibleToUser: Boolean,
    val markers: List<ChromeMediaShieldReadyMarker>,
) {
    override fun toString(): String = "ChromeMediaShieldFocusEventEvidence(redacted)"
}

internal sealed interface ChromeMediaShieldFocusEventResult {
    data class Verified(
        val marker: ChromeMediaShieldReadyMarker,
        val sourceUniqueId: String,
        val webRootUniqueId: String,
        val foregroundRootUniqueId: String,
    ) : ChromeMediaShieldFocusEventResult {
        override fun toString(): String = "ChromeMediaShieldFocusEventResult.Verified(redacted)"
    }

    data class Rejected(
        val reason: String,
    ) : ChromeMediaShieldFocusEventResult
}

internal object ChromeMediaShieldFocusEventPolicy {
    fun verify(
        evidence: ChromeMediaShieldFocusEventEvidence,
        expectedClaim: ChromeMediaShieldReadyClaim,
        expectedWindowId: Int,
    ): ChromeMediaShieldFocusEventResult {
        if (evidence.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return rejected("ready_focus_wrong_event")
        if (
            evidence.eventPackageName != ChromePackageName ||
            evidence.sourcePackageName != ChromePackageName ||
            evidence.foregroundRootPackageName != ChromePackageName
        ) {
            return rejected("ready_focus_not_chrome")
        }
        if (evidence.eventWindowId != expectedWindowId || evidence.sourceWindowId != expectedWindowId) {
            return rejected("ready_focus_wrong_window")
        }
        // The secret READY name is installed on a native button inside a closed shadow root only
        // after the native claim is accepted. TYPE_VIEW_FOCUSED authenticates that exact
        // browser-owned virtual node. Chromium may deliver the queued event after another element
        // takes focus, while the protected 1px beacon can be reported hidden. Those rereads are
        // diagnostic; native focusability, exact ancestry and the current secret claim remain
        // mandatory.
        if (
            evidence.sourceClassName != ReadySourceClassName ||
            evidence.sourceUniqueId.isBlank() ||
            evidence.sourceRootUniqueId.isBlank() ||
            evidence.sourceRootUniqueId == evidence.foregroundRootUniqueId ||
            evidence.sourceRootClassName != ChromeMediaShieldWebRootContract.ClassName ||
            !evidence.sourceRootVisibleToUser ||
            !evidence.sourceAttachedToForegroundRoot ||
            !evidence.sourceFocusable
        ) {
            return rejected("ready_focus_source_mismatch")
        }
        val marker = evidence.markers.singleOrNull() ?: return rejected("ready_focus_marker_ambiguous")
        if (evidence.sourceViewIdResourceName != ReadyViewId) {
            return rejected("ready_focus_view_id_mismatch")
        }
        if (
            marker.lifecycleSequence != expectedClaim.lifecycleSequence ||
            ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(marker.readyToken) !=
            expectedClaim.identity.tokenDigest
        ) {
            return rejected("ready_focus_claim_mismatch")
        }
        return ChromeMediaShieldFocusEventResult.Verified(
            marker = marker,
            sourceUniqueId = evidence.sourceUniqueId,
            webRootUniqueId = evidence.sourceRootUniqueId,
            foregroundRootUniqueId = evidence.foregroundRootUniqueId,
        )
    }

    private fun rejected(reason: String) = ChromeMediaShieldFocusEventResult.Rejected(reason)

    const val ReadyViewId = "glosh-h19-ready"
    private const val ReadySourceClassName = "android.widget.Button"
    private const val ChromePackageName = "com.android.chrome"
}

internal data class ChromeMediaShieldForegroundContextEvidence(
    val windowId: Int,
    val rootPackageName: String,
    val nativeRootUniqueId: String,
    val exactAnchorCurrent: Boolean,
)

internal object ChromeMediaShieldForegroundContextPolicy {
    fun bindingDigest(
        nativeRootUniqueId: String,
        webRootUniqueId: String,
    ): String? =
        when {
            nativeRootUniqueId.isNotBlank() -> digest("native-root:$nativeRootUniqueId")
            webRootUniqueId.isNotBlank() -> digest("web-root:$webRootUniqueId")
            else -> null
        }

    fun verifies(
        evidence: ChromeMediaShieldForegroundContextEvidence,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean {
        if (
            evidence.windowId != document.windowId ||
            document.accessibilityContext.windowId != document.windowId ||
            evidence.rootPackageName != ChromePackageName ||
            !evidence.exactAnchorCurrent
        ) {
            return false
        }
        val nativeDigest =
            evidence.nativeRootUniqueId
                .takeIf(String::isNotBlank)
                ?.let { digest("native-root:$it") }
        val webDigest =
            document.focusAnchor.webRootUniqueId
                .takeIf(String::isNotBlank)
                ?.let { digest("web-root:$it") }
        return document.accessibilityContext.rootIdentityDigest == nativeDigest ||
            document.accessibilityContext.rootIdentityDigest == webDigest
    }

    private fun digest(value: String): String = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(value)

    private const val ChromePackageName = "com.android.chrome"
}

internal data class ChromeMediaShieldBoundAnchorEvidence(
    val windowId: Int,
    val rootPackageName: String,
    val nativeRootUniqueId: String,
    val exactAnchorCurrent: Boolean,
    val matchingNodeCount: Int,
    val sourcePackageName: String,
    val sourceWindowId: Int,
    val sourceViewIdResourceName: String,
    val sourceUniqueId: String,
    val sourceRootUniqueId: String,
    val sourceRootVisibleToUser: Boolean,
    val sourceAttachedToForegroundRoot: Boolean,
    val sourceVisibleToUser: Boolean,
    val markers: List<ChromeMediaShieldReadyMarker>,
) {
    override fun toString(): String = "ChromeMediaShieldBoundAnchorEvidence(redacted)"
}

internal object ChromeMediaShieldBoundAnchorPolicy {
    fun verifies(
        evidence: ChromeMediaShieldBoundAnchorEvidence,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean {
        if (
            !ChromeMediaShieldForegroundContextPolicy.verifies(
                evidence =
                    ChromeMediaShieldForegroundContextEvidence(
                        windowId = evidence.windowId,
                        rootPackageName = evidence.rootPackageName,
                        nativeRootUniqueId = evidence.nativeRootUniqueId,
                        exactAnchorCurrent = evidence.exactAnchorCurrent,
                    ),
                document = document,
            ) ||
            evidence.matchingNodeCount != 1
        ) {
            return false
        }
        if (
            evidence.sourcePackageName != ChromePackageName ||
            evidence.sourceWindowId != document.windowId ||
            evidence.sourceViewIdResourceName != document.focusAnchor.viewIdResourceName ||
            evidence.sourceUniqueId != document.focusAnchor.sourceUniqueId ||
            evidence.sourceRootUniqueId != document.focusAnchor.webRootUniqueId ||
            !evidence.sourceRootVisibleToUser ||
            !evidence.sourceAttachedToForegroundRoot
        ) {
            return false
        }
        val marker = evidence.markers.singleOrNull() ?: return false
        return marker.lifecycleSequence == claim.lifecycleSequence &&
            claim.identity == document.identity &&
            ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(marker.readyToken) ==
            claim.identity.tokenDigest
    }

    private const val ChromePackageName = "com.android.chrome"
}

/** Reads only the exact ready marker from the current Chrome Accessibility window. */
internal class ChromeMediaShieldAccessibilityTokenScanner {
    private var boundFocusSource: AccessibilityNodeInfo? = null
    private var boundWebRoot: AccessibilityNodeInfo? = null
    private var boundClaim: ChromeMediaShieldReadyClaim? = null
    private var boundRebindAttempted = false
    private var exactAnchorRebindCount = 0L
    private var lastBoundAnchorFailureReason = ""
    private var fallbackAttemptedClaim: ChromeMediaShieldReadyClaim? = null

    fun exactAnchorRebindCount(): Long = exactAnchorRebindCount

    fun lastBoundAnchorFailureReason(): String = lastBoundAnchorFailureReason

    fun bindReadyEvent(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
    ): ChromeMediaShieldTokenScanResult =
        try {
            bindReadyEventOrThrow(event, window, expectedClaim)
        } catch (_: RuntimeException) {
            ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_node_stale")
        }

    private fun bindReadyEventOrThrow(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
    ): ChromeMediaShieldTokenScanResult {
        val source = event.source ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_source_missing")
        return try {
            val root = window.root ?: return ChromeMediaShieldTokenScanResult.FailClosed("ax_root_missing")
            try {
                bindReadyEventAgainstRoot(event, window, expectedClaim, source, root)
            } finally {
                recycleNode(root)
            }
        } finally {
            recycleNode(source)
        }
    }

    private fun bindReadyEventAgainstRoot(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
        source: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
    ): ChromeMediaShieldTokenScanResult {
        val direct =
            bindReadyCandidateAgainstRoot(
                event = event,
                window = window,
                expectedClaim = expectedClaim,
                source = source,
                root = root,
            )
        if (direct is ChromeMediaShieldTokenScanResult.Current) return direct
        val permitsFallback =
            ChromeMediaShieldRootEventFallbackPolicy.permits(
                evidence =
                    ChromeMediaShieldRootEventFallbackEvidence(
                        eventType = event.eventType,
                        contentChangeTypes = event.contentChangeTypes,
                        eventPackageName = event.packageName?.toString().orEmpty(),
                        eventWindowId = event.windowId,
                        sourcePackageName = source.packageName?.toString().orEmpty(),
                        sourceWindowId = source.windowId,
                        sourceClassName = source.className?.toString().orEmpty(),
                        sourceIsWindowRoot = source == root,
                    ),
                expectedWindowId = window.id,
                alreadyAttempted = fallbackAttemptedClaim == expectedClaim,
            )
        if (!permitsFallback) return direct
        fallbackAttemptedClaim = expectedClaim
        val search =
            ChromeMediaShieldAccessibilityNodeTraversal.copyUniqueReadyAnchorCandidate(
                windowRoot = root,
                expectedViewIdResourceName = ChromeMediaShieldFocusEventPolicy.ReadyViewId,
                markerMatches = { contentDescription, text ->
                    markersForNodeFields(contentDescription, text)
                        .singleOrNull()
                        ?.let { marker ->
                            marker.lifecycleSequence == expectedClaim.lifecycleSequence &&
                                ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(marker.readyToken) ==
                                expectedClaim.identity.tokenDigest
                        } == true
                },
            )
        val candidate =
            when (search) {
                ChromeMediaShieldOwnedNodeSearchResult.Absent ->
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_lookup_absent")
                ChromeMediaShieldOwnedNodeSearchResult.Ambiguous ->
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_lookup_ambiguous")
                ChromeMediaShieldOwnedNodeSearchResult.Overflow ->
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_lookup_overflow")
                is ChromeMediaShieldOwnedNodeSearchResult.Found -> search.node
            }
        return try {
            // A root-sourced event can prove that the claimed marker exists, but it cannot prove
            // that Chrome attributed this exact mutation to that marker. Keep the observation
            // diagnostic and fail closed; only the exact event.source can create authority.
            ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_lookup_diagnostic_only")
        } finally {
            recycleNode(candidate)
        }
    }

    private fun bindReadyCandidateAgainstRoot(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
        source: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo,
    ): ChromeMediaShieldTokenScanResult {
        val sourceWebRoot =
            ChromeMediaShieldAccessibilityNodeTraversal.copyWebDocumentRoot(source, root)
                ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_web_root_missing")
        val sourceWebRootUniqueId =
            ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(sourceWebRoot)
                ?: run {
                    recycleNode(sourceWebRoot)
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_web_root_identity_missing")
                }
        var sourceWebRootConsumed = false
        try {
            val result =
                ChromeMediaShieldFocusEventPolicy.verify(
                    evidence =
                        ChromeMediaShieldFocusEventEvidence(
                            eventType = event.eventType,
                            contentChangeTypes = event.contentChangeTypes,
                            eventPackageName = event.packageName?.toString().orEmpty(),
                            eventWindowId = event.windowId,
                            sourcePackageName = source.packageName?.toString().orEmpty(),
                            sourceWindowId = source.windowId,
                            sourceViewIdResourceName = source.viewIdResourceName.orEmpty(),
                            sourceClassName = source.className?.toString().orEmpty(),
                            sourceUniqueId =
                                ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(
                                    source,
                                ).orEmpty(),
                            sourceRootUniqueId = sourceWebRootUniqueId,
                            sourceRootClassName = sourceWebRoot.className?.toString().orEmpty(),
                            sourceRootVisibleToUser = sourceWebRoot.isVisibleToUser,
                            foregroundRootPackageName = root.packageName?.toString().orEmpty(),
                            foregroundRootUniqueId =
                                ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(root).orEmpty(),
                            sourceAttachedToForegroundRoot =
                                ChromeMediaShieldAccessibilityNodeTraversal.belongsToWindowRoot(source, root),
                            sourceFocusable = source.isFocusable,
                            sourceFocused = source.isFocused,
                            sourceVisibleToUser = source.isVisibleToUser,
                            markers =
                                markersForNodeFields(
                                    contentDescription = source.contentDescription?.toString(),
                                    text = source.text?.toString(),
                                ),
                        ),
                    expectedClaim = expectedClaim,
                    expectedWindowId = window.id,
                )
            val verified =
                result as? ChromeMediaShieldFocusEventResult.Verified
                    ?: return ChromeMediaShieldTokenScanResult.FailClosed(
                        (result as ChromeMediaShieldFocusEventResult.Rejected).reason,
                    )
            val rootIdentityDigest =
                ChromeMediaShieldForegroundContextPolicy.bindingDigest(
                    nativeRootUniqueId = verified.foregroundRootUniqueId,
                    webRootUniqueId = verified.webRootUniqueId,
                ) ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_root_identity_missing")
            val accessibilityContext =
                ChromeMediaShieldAccessibilityContext(
                    windowId = window.id,
                    rootIdentityDigest = rootIdentityDigest,
                    markerIdentityDigest =
                        ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                            "event:${verified.sourceUniqueId}:${source.viewIdResourceName.orEmpty()}",
                        ),
                )
            val (ownedSource, ownedWebRoot) = copyBoundNodes(source, sourceWebRoot)
            sourceWebRootConsumed = true
            var nodesTransferred = false
            try {
                val activation =
                    ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(
                        claim = expectedClaim,
                        accessibilityContext = accessibilityContext,
                    ) ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_claim_not_current")
                if (activation.claim.identity != expectedClaim.identity) {
                    ChromeMediaShieldDocumentAuthorityRegistry.deactivateClaimedForeground(expectedClaim)
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_ax_identity_mismatch")
                }
                replaceBoundNodes(ownedSource, ownedWebRoot, expectedClaim)
                nodesTransferred = true
                return ChromeMediaShieldTokenScanResult.Current(
                    ChromeMediaShieldForegroundDocument(
                        identity = activation.claim.identity,
                        lifecycleSequence = expectedClaim.lifecycleSequence,
                        windowId = window.id,
                        accessibilityContext = accessibilityContext,
                        focusAnchor =
                            ChromeMediaShieldFocusAnchor(
                                viewIdResourceName = source.viewIdResourceName.orEmpty(),
                                sourceUniqueId = verified.sourceUniqueId,
                                webRootUniqueId = verified.webRootUniqueId,
                            ),
                    ),
                )
            } finally {
                if (!nodesTransferred) {
                    recycleNode(ownedSource)
                    recycleNode(ownedWebRoot)
                }
            }
        } finally {
            if (!sourceWebRootConsumed) recycleNode(sourceWebRoot)
        }
    }

    fun verifiesBoundContext(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean = boundContextBinding(window, claim, document) != ChromeMediaShieldBoundContextBinding.Invalid

    fun verifiesReleaseBoundary(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean =
        boundContextBinding(window, claim, document) ==
            ChromeMediaShieldBoundContextBinding.ExactEventSource

    fun boundContextBinding(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): ChromeMediaShieldBoundContextBinding =
        try {
            boundContextBindingOrThrow(window, claim, document)
        } catch (_: RuntimeException) {
            lastBoundAnchorFailureReason = "ready_boundary_anchor_stale"
            ChromeMediaShieldBoundContextBinding.Invalid
        }

    private fun boundContextBindingOrThrow(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): ChromeMediaShieldBoundContextBinding {
        if (window.id != document.windowId) return ChromeMediaShieldBoundContextBinding.Invalid
        val root = window.root ?: return ChromeMediaShieldBoundContextBinding.Invalid
        try {
            val anchorFailure = boundAnchorFailureReasonOrThrow(window, root, claim, document)
            lastBoundAnchorFailureReason = anchorFailure.orEmpty()
            val exactEventSourceCurrent = anchorFailure == null
            val binding = ChromeMediaShieldBoundContextPolicy.select(exactEventSourceCurrent)
            if (binding == ChromeMediaShieldBoundContextBinding.Invalid) return binding
            val registryCurrent =
                ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                    sessionId = claim.identity.protectionSessionId,
                    epoch = claim.identity.policyEpoch,
                    tokenDigest = claim.identity.tokenDigest,
                    lifecycleSequence = claim.lifecycleSequence,
                    accessibilityContext = document.accessibilityContext,
                ) == claim.identity
            return binding.takeIf { registryCurrent } ?: ChromeMediaShieldBoundContextBinding.Invalid
        } finally {
            recycleNode(root)
        }
    }

    /** A lookup can only refresh authority first created by an exact event.source binding. */
    fun verifiesBoundAnchor(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean = boundAnchorFailureReason(window, claim, document) == null

    /** Returns a bounded reason code and retains a successfully reacquired exact anchor. */
    fun boundAnchorFailureReason(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): String? =
        try {
            boundAnchorFailureReasonOrThrow(window, claim, document).also {
                lastBoundAnchorFailureReason = it.orEmpty()
            }
        } catch (_: RuntimeException) {
            "ready_boundary_anchor_stale".also { lastBoundAnchorFailureReason = it }
        }

    private fun boundAnchorFailureReasonOrThrow(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): String? {
        val root = window.root ?: return "ready_boundary_root_missing"
        return try {
            boundAnchorFailureReasonOrThrow(window, root, claim, document)
        } finally {
            recycleNode(root)
        }
    }

    private fun boundAnchorFailureReasonOrThrow(
        window: AccessibilityWindowInfo,
        root: AccessibilityNodeInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): String? {
        if (
            boundClaim != claim ||
            document.identity != claim.identity ||
            document.lifecycleSequence != claim.lifecycleSequence ||
            document.windowId != window.id
        ) {
            return "ready_boundary_event_anchor_missing"
        }
        if (verifiesRetainedFocusSource(root, claim, document)) return null
        if (verifiesRetainedWebRoot(root, window.id, document)) return null
        val search =
            ChromeMediaShieldAccessibilityNodeTraversal.copyExactWebDocumentRootCandidate(
                windowRoot = root,
                expectedWindowId = window.id,
                expectedUniqueId = document.focusAnchor.webRootUniqueId,
                expectedRootIdentityDigest = document.accessibilityContext.rootIdentityDigest,
            )
        val webRoot =
            when (search) {
                ChromeMediaShieldOwnedNodeSearchResult.Absent -> return "ready_boundary_web_root_absent"
                ChromeMediaShieldOwnedNodeSearchResult.Ambiguous -> return "ready_boundary_web_root_ambiguous"
                ChromeMediaShieldOwnedNodeSearchResult.Overflow -> return "ready_boundary_web_root_scan_overflow"
                is ChromeMediaShieldOwnedNodeSearchResult.Found -> search.node
            }
        return try {
            if (!verifiesCurrentWebRoot(root, webRoot, window.id, document)) {
                return "ready_boundary_web_root_mismatch"
            }
            replaceBoundWebRoot(webRoot, claim)
            exactAnchorRebindCount += 1L
            null
        } finally {
            if (boundWebRoot !== webRoot) recycleNode(webRoot)
        }
    }

    fun clearBoundFocusSource() {
        val source = boundFocusSource
        val webRoot = boundWebRoot
        boundFocusSource = null
        boundWebRoot = null
        boundClaim = null
        boundRebindAttempted = false
        lastBoundAnchorFailureReason = ""
        source?.let(::recycleNode)
        webRoot?.let(::recycleNode)
    }

    @Suppress("DEPRECATION")
    private fun copyBoundNodes(
        source: AccessibilityNodeInfo,
        webRoot: AccessibilityNodeInfo,
    ): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo> {
        val ownedSource = AccessibilityNodeInfo.obtain(source)
        return ownedSource to webRoot
    }

    private fun replaceBoundNodes(
        ownedSource: AccessibilityNodeInfo,
        ownedWebRoot: AccessibilityNodeInfo,
        claim: ChromeMediaShieldReadyClaim,
        rebindAttempted: Boolean = false,
    ) {
        clearBoundFocusSource()
        boundFocusSource = ownedSource
        boundWebRoot = ownedWebRoot
        boundClaim = claim
        boundRebindAttempted = rebindAttempted
    }

    private fun replaceBoundWebRoot(
        ownedWebRoot: AccessibilityNodeInfo,
        claim: ChromeMediaShieldReadyClaim,
    ) {
        val previous = boundWebRoot
        boundWebRoot = ownedWebRoot
        boundClaim = claim
        boundRebindAttempted = true
        if (previous !== ownedWebRoot) previous?.let(::recycleNode)
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        runCatching(node::recycle)
    }

    private fun verifiesRetainedFocusSource(
        root: AccessibilityNodeInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean {
        val source = boundFocusSource ?: return false
        if (!source.refresh()) return false
        val currentWebRoot =
            ChromeMediaShieldAccessibilityNodeTraversal.copyWebDocumentRoot(source, root)
                ?: return false
        return try {
            val webRootUniqueId =
                ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(currentWebRoot).orEmpty()
            val sourceAttached = ChromeMediaShieldAccessibilityNodeTraversal.belongsToWindowRoot(source, root)
            ChromeMediaShieldBoundAnchorPolicy.verifies(
                evidence =
                    ChromeMediaShieldBoundAnchorEvidence(
                        windowId = root.windowId,
                        rootPackageName = root.packageName?.toString().orEmpty(),
                        nativeRootUniqueId =
                            ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(root).orEmpty(),
                        exactAnchorCurrent =
                            sourceAttached && webRootUniqueId == document.focusAnchor.webRootUniqueId,
                        matchingNodeCount = 1,
                        sourcePackageName = source.packageName?.toString().orEmpty(),
                        sourceWindowId = source.windowId,
                        sourceViewIdResourceName = source.viewIdResourceName.orEmpty(),
                        sourceUniqueId =
                            ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(source).orEmpty(),
                        sourceRootUniqueId = webRootUniqueId,
                        sourceRootVisibleToUser = currentWebRoot.isVisibleToUser,
                        sourceAttachedToForegroundRoot = sourceAttached,
                        sourceVisibleToUser = source.isVisibleToUser,
                        markers =
                            markersForNodeFields(
                                contentDescription = source.contentDescription?.toString(),
                                text = source.text?.toString(),
                            ),
                    ),
                claim = claim,
                document = document,
            )
        } finally {
            recycleNode(currentWebRoot)
        }
    }

    private fun verifiesRetainedWebRoot(
        root: AccessibilityNodeInfo,
        expectedWindowId: Int,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean {
        val webRoot = boundWebRoot ?: return false
        return webRoot.refresh() && verifiesCurrentWebRoot(root, webRoot, expectedWindowId, document)
    }

    private fun verifiesCurrentWebRoot(
        root: AccessibilityNodeInfo,
        webRoot: AccessibilityNodeInfo,
        expectedWindowId: Int,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean =
        ChromeMediaShieldCurrentWebRootPolicy.verifies(
            evidence =
                ChromeMediaShieldCurrentWebRootEvidence(
                    expectedWindowId = expectedWindowId,
                    windowRootPackageName = root.packageName?.toString().orEmpty(),
                    nativeWindowRootUniqueId =
                        ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(root).orEmpty(),
                    candidatePackageName = webRoot.packageName?.toString().orEmpty(),
                    candidateClassName = webRoot.className?.toString().orEmpty(),
                    candidateWindowId = webRoot.windowId,
                    candidateUniqueId =
                        ChromeMediaShieldAccessibilityNodeTraversal.uniqueIdOrNull(webRoot),
                    candidateVisibleToUser = webRoot.isVisibleToUser,
                    candidateAttachedToWindowRoot =
                        ChromeMediaShieldAccessibilityNodeTraversal.belongsToWindowRoot(webRoot, root),
                ),
            expectedWebRootUniqueId = document.focusAnchor.webRootUniqueId,
            expectedRootIdentityDigest = document.accessibilityContext.rootIdentityDigest,
        )

    internal fun readyMarkerOrNull(value: String): ChromeMediaShieldReadyMarker? {
        if (!value.startsWith(ReadyPrefix)) return null
        val fields = value.removePrefix(ReadyPrefix).split(':')
        if (fields.size != 2) return null
        val token = fields[0]
        val lifecycle = fields[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        if (
            token.length !in MinimumTokenCharacters..MaximumTokenCharacters ||
            token.any { character -> !character.isLetterOrDigit() && character != '-' && character != '_' }
        ) {
            return null
        }
        return ChromeMediaShieldReadyMarker(token, lifecycle)
    }

    internal fun markersForNodeFields(
        contentDescription: String?,
        text: String?,
    ): List<ChromeMediaShieldReadyMarker> =
        listOfNotNull(contentDescription, text)
            .mapNotNull(::readyMarkerOrNull)
            .distinct()

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val ReadyPrefix = "glosh-shield-ready:"
        const val MinimumTokenCharacters = 22
        const val MaximumTokenCharacters = 64
    }
}
