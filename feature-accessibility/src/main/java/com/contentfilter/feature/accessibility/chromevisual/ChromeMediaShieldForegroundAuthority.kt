package com.contentfilter.feature.accessibility.chromevisual

import android.os.Build
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
    val eventPackageName: String,
    val eventWindowId: Int,
    val sourcePackageName: String,
    val sourceWindowId: Int,
    val sourceViewIdResourceName: String,
    val sourceUniqueId: String,
    val sourceRootUniqueId: String,
    val foregroundRootPackageName: String,
    val foregroundRootUniqueId: String,
    val sourceAttachedToForegroundRoot: Boolean,
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
        val rootUniqueId: String,
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
        // TYPE_VIEW_FOCUSED is the platform-authenticated focus transition. Chromium queues that
        // event, so later focus and visibility reads may differ by delivery time. The ready beacon
        // is deliberately transparent under the opaque surface, therefore isFocused and
        // isVisibleToUser are diagnostic only. Exact current window/root ancestry and the claimed
        // source identity remain mandatory.
        if (
            evidence.sourceUniqueId.isBlank() ||
            evidence.sourceRootUniqueId.isBlank() ||
            !evidence.sourceAttachedToForegroundRoot
        ) {
            return rejected("ready_focus_root_mismatch")
        }
        val marker = evidence.markers.singleOrNull() ?: return rejected("ready_focus_marker_ambiguous")
        if (evidence.sourceViewIdResourceName != ReadyViewIdPrefix + marker.readyToken) {
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
            rootUniqueId = evidence.sourceRootUniqueId,
        )
    }

    private fun rejected(reason: String) = ChromeMediaShieldFocusEventResult.Rejected(reason)

    const val ReadyViewIdPrefix = "glosh-h19-ready-"
    private const val ChromePackageName = "com.android.chrome"
}

internal data class ChromeMediaShieldBoundAnchorEvidence(
    val windowId: Int,
    val rootPackageName: String,
    val rootUniqueId: String,
    val matchingNodeCount: Int,
    val sourcePackageName: String,
    val sourceWindowId: Int,
    val sourceViewIdResourceName: String,
    val sourceUniqueId: String,
    val sourceRootUniqueId: String,
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
            evidence.windowId != document.windowId ||
            document.accessibilityContext.windowId != document.windowId ||
            evidence.rootPackageName != ChromePackageName ||
            evidence.rootUniqueId.isBlank() ||
            ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken("root:${evidence.rootUniqueId}") !=
            document.accessibilityContext.rootIdentityDigest ||
            evidence.matchingNodeCount != 1
        ) {
            return false
        }
        if (
            evidence.sourcePackageName != ChromePackageName ||
            evidence.sourceWindowId != document.windowId ||
            evidence.sourceViewIdResourceName != document.focusAnchor.viewIdResourceName ||
            evidence.sourceUniqueId != document.focusAnchor.sourceUniqueId ||
            evidence.sourceRootUniqueId != evidence.rootUniqueId ||
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
    fun bindFocusedEvent(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
    ): ChromeMediaShieldTokenScanResult =
        try {
            bindFocusedEventOrThrow(event, window, expectedClaim)
        } catch (_: RuntimeException) {
            ChromeMediaShieldTokenScanResult.FailClosed("ready_focus_node_stale")
        }

    private fun bindFocusedEventOrThrow(
        event: AccessibilityEvent,
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
    ): ChromeMediaShieldTokenScanResult {
        val source = event.source ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_focus_source_missing")
        val root = window.root ?: return ChromeMediaShieldTokenScanResult.FailClosed("ax_root_missing")
        val result =
            ChromeMediaShieldFocusEventPolicy.verify(
                evidence =
                    ChromeMediaShieldFocusEventEvidence(
                        eventType = event.eventType,
                        eventPackageName = event.packageName?.toString().orEmpty(),
                        eventWindowId = event.windowId,
                        sourcePackageName = source.packageName?.toString().orEmpty(),
                        sourceWindowId = source.windowId,
                        sourceViewIdResourceName = source.viewIdResourceName.orEmpty(),
                        sourceUniqueId = source.uniqueIdOrNull().orEmpty(),
                        sourceRootUniqueId = source.webRootUniqueIdOrNull().orEmpty(),
                        foregroundRootPackageName = root.packageName?.toString().orEmpty(),
                        foregroundRootUniqueId = root.uniqueIdOrNull().orEmpty(),
                        sourceAttachedToForegroundRoot = source.belongsToWindowRoot(root),
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
        val accessibilityContext =
            ChromeMediaShieldAccessibilityContext(
                windowId = window.id,
                rootIdentityDigest =
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                        "root:${verified.rootUniqueId}",
                    ),
                markerIdentityDigest =
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                        "focus:${verified.sourceUniqueId}:${source.viewIdResourceName.orEmpty()}",
                    ),
            )
        val activation =
            ChromeMediaShieldDocumentAuthorityRegistry.activateClaimedForeground(
                claim = expectedClaim,
                accessibilityContext = accessibilityContext,
            ) ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_focus_claim_not_current")
        if (activation.claim.identity != expectedClaim.identity) {
            return ChromeMediaShieldTokenScanResult.FailClosed("ready_focus_identity_mismatch")
        }
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
                    ),
            ),
        )
    }

    fun verifiesBoundContext(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean =
        try {
            verifiesBoundContextOrThrow(window, claim, document)
        } catch (_: RuntimeException) {
            false
        }

    private fun verifiesBoundContextOrThrow(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean {
        if (window.id != document.windowId) return false
        val root = window.root ?: return false
        if (root.packageName?.toString() != ChromePackageName) return false
        return ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
            sessionId = claim.identity.protectionSessionId,
            epoch = claim.identity.policyEpoch,
            tokenDigest = claim.identity.tokenDigest,
            lifecycleSequence = claim.lifecycleSequence,
            accessibilityContext = document.accessibilityContext,
        ) == claim.identity
    }

    /** Event-driven secondary continuity check; never creates authority or retries. */
    fun verifiesBoundAnchor(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean = boundAnchorFailureReason(window, claim, document) == null

    /** Returns a bounded reason code without changing the bound-anchor authority decision. */
    fun boundAnchorFailureReason(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): String? =
        try {
            boundAnchorFailureReasonOrThrow(window, claim, document)
        } catch (_: RuntimeException) {
            "ready_boundary_anchor_stale"
        }

    private fun boundAnchorFailureReasonOrThrow(
        window: AccessibilityWindowInfo,
        claim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
    ): String? {
        if (!verifiesBoundContext(window, claim, document)) return "ready_boundary_context_mismatch"
        val root = window.root ?: return "ready_boundary_root_missing"
        val matches =
            root.findAccessibilityNodeInfosByViewId(document.focusAnchor.viewIdResourceName).orEmpty()
        if (matches.isEmpty()) return "ready_boundary_anchor_absent"
        if (matches.size != 1) return "ready_boundary_anchor_ambiguous"
        val node = matches.singleOrNull()
        val webRootUniqueId = node?.webRootUniqueIdOrNull().orEmpty()
        val verified =
            ChromeMediaShieldBoundAnchorPolicy.verifies(
                evidence =
                    ChromeMediaShieldBoundAnchorEvidence(
                        windowId = window.id,
                        rootPackageName = root.packageName?.toString().orEmpty(),
                        rootUniqueId = webRootUniqueId,
                        matchingNodeCount = matches.size,
                        sourcePackageName = node?.packageName?.toString().orEmpty(),
                        sourceWindowId = node?.windowId ?: -1,
                        sourceViewIdResourceName = node?.viewIdResourceName.orEmpty(),
                        sourceUniqueId = node?.uniqueIdOrNull().orEmpty(),
                        sourceRootUniqueId = webRootUniqueId,
                        sourceAttachedToForegroundRoot = node?.belongsToWindowRoot(root) == true,
                        sourceVisibleToUser = node?.isVisibleToUser == true,
                        markers =
                            markersForNodeFields(
                                contentDescription = node?.contentDescription?.toString(),
                                text = node?.text?.toString(),
                            ),
                    ),
                claim = claim,
                document = document,
            )
        return if (verified) null else "ready_boundary_anchor_mismatch"
    }

    fun scan(
        window: AccessibilityWindowInfo,
        expectedClaim: ChromeMediaShieldReadyClaim,
    ): ChromeMediaShieldTokenScanResult {
        val root = window.root ?: return ChromeMediaShieldTokenScanResult.FailClosed("ax_root_missing")
        if (root.packageName?.toString() != ChromePackageName) {
            return ChromeMediaShieldTokenScanResult.FailClosed("ax_root_not_chrome")
        }
        val rootUniqueId = root.uniqueIdOrNull() ?: return ChromeMediaShieldTokenScanResult.FailClosed("ax_root_identity_missing")
        val matches = root.findAccessibilityNodeInfosByText(ReadyPrefix).orEmpty()
        if (matches.size > MaximumReadyNodes) {
            return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_node_limit")
        }
        val markerNodes =
            matches.map { node ->
                if (!node.isVisibleToUser || node.windowId != window.id || !node.belongsToRoot(rootUniqueId)) {
                    return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_not_current_in_window")
                }
                val marker =
                    markersForNodeFields(
                        contentDescription = node.contentDescription?.toString(),
                        text = node.text?.toString(),
                    ).singleOrNull()
                        ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_node_ambiguous")
                val markerUniqueId =
                    node.uniqueIdOrNull()
                        ?: return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_ax_identity_missing")
                marker to markerUniqueId
            }
        val expected =
            markerNodes.singleOrNull { (marker, _) ->
                marker.lifecycleSequence == expectedClaim.lifecycleSequence &&
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(marker.readyToken) ==
                    expectedClaim.identity.tokenDigest
            } ?: return ChromeMediaShieldTokenScanResult.FailClosed(
                if (markerNodes.isEmpty()) "ready_token_absent" else "ready_token_not_unique_or_current",
            )
        if (markerNodes.size != 1) {
            return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_not_unique_or_current")
        }
        val accessibilityContext =
            ChromeMediaShieldAccessibilityContext(
                windowId = window.id,
                rootIdentityDigest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken("root:$rootUniqueId"),
                markerIdentityDigest =
                    ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(
                        "marker:${expected.second}",
                    ),
            )
        val identity =
            ChromeMediaShieldDocumentAuthorityRegistry.resolveClaimedForeground(
                sessionId = expectedClaim.identity.protectionSessionId,
                epoch = expectedClaim.identity.policyEpoch,
                tokenDigest = expectedClaim.identity.tokenDigest,
                lifecycleSequence = expectedClaim.lifecycleSequence,
                accessibilityContext = accessibilityContext,
            ) ?: return ChromeMediaShieldTokenScanResult.FailClosed(
                "ready_token_not_unique_or_current",
            )
        if (identity != expectedClaim.identity) {
            return ChromeMediaShieldTokenScanResult.FailClosed("ready_token_identity_mismatch")
        }
        return ChromeMediaShieldTokenScanResult.Current(
            ChromeMediaShieldForegroundDocument(
                identity = identity,
                lifecycleSequence = expectedClaim.lifecycleSequence,
                windowId = window.id,
                accessibilityContext = accessibilityContext,
                focusAnchor =
                    ChromeMediaShieldFocusAnchor(
                        viewIdResourceName = ChromeMediaShieldFocusEventPolicy.ReadyViewIdPrefix + expected.first.readyToken,
                        sourceUniqueId = expected.second,
                    ),
            ),
        )
    }

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

    private fun AccessibilityNodeInfo.belongsToRoot(rootUniqueId: String): Boolean {
        var current: AccessibilityNodeInfo? = this
        repeat(MaximumAncestorDepth) {
            val node = current ?: return false
            if (node.uniqueIdOrNull() == rootUniqueId) return true
            current = node.parent
        }
        return false
    }

    private fun AccessibilityNodeInfo.webRootUniqueIdOrNull(): String? {
        var current: AccessibilityNodeInfo? = this
        var lastUniqueId: String? = null
        repeat(MaximumAncestorDepth) {
            val node = current ?: return lastUniqueId
            val uniqueId = node.uniqueIdOrNull()
            if (uniqueId == null) return lastUniqueId
            lastUniqueId = uniqueId
            current = node.parent
        }
        return null
    }

    private fun AccessibilityNodeInfo.belongsToWindowRoot(root: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = this
        repeat(MaximumAncestorDepth) {
            val node = current ?: return false
            if (node == root) return true
            current = node.parent
        }
        return false
    }

    private fun AccessibilityNodeInfo.uniqueIdOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uniqueId?.takeIf(String::isNotBlank)
        } else {
            null
        }

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val ReadyPrefix = "glosh-shield-ready:"
        const val MaximumReadyNodes = 16
        const val MaximumAncestorDepth = 128
        const val MinimumTokenCharacters = 22
        const val MaximumTokenCharacters = 64
    }
}
