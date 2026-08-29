package com.contentfilter.feature.accessibility.chromevisual

import android.os.Build
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
)

internal data class ChromeMediaShieldReadyMarker(
    val readyToken: String,
    val lifecycleSequence: Long,
)

internal sealed interface ChromeMediaShieldTokenScanResult {
    data class Current(
        val document: ChromeMediaShieldForegroundDocument,
    ) : ChromeMediaShieldTokenScanResult

    data class FailClosed(
        val reason: String,
    ) : ChromeMediaShieldTokenScanResult
}

/** Reads only the exact ready marker from the current Chrome Accessibility window. */
internal class ChromeMediaShieldAccessibilityTokenScanner {
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
