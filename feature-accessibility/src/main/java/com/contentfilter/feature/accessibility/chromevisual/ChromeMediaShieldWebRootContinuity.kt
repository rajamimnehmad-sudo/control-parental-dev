package com.contentfilter.feature.accessibility.chromevisual

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

internal enum class ChromeMediaShieldBoundContextBinding {
    ExactFocusSource,
    ExactWebRoot,
    Invalid,
}

internal object ChromeMediaShieldBoundContextPolicy {
    fun select(
        exactFocusSourceCurrent: Boolean,
        exactWebRootCurrent: Boolean,
        requireExactFocusSource: Boolean,
    ): ChromeMediaShieldBoundContextBinding =
        when {
            exactFocusSourceCurrent -> ChromeMediaShieldBoundContextBinding.ExactFocusSource
            !requireExactFocusSource && exactWebRootCurrent -> ChromeMediaShieldBoundContextBinding.ExactWebRoot
            else -> ChromeMediaShieldBoundContextBinding.Invalid
        }
}

internal data class ChromeMediaShieldWebRootEvidence(
    val windowId: Int,
    val rootPackageName: String,
    val nativeRootUniqueId: String,
    val webRootPackageName: String,
    val webRootClassName: String,
    val webRootWindowId: Int,
    val webRootUniqueId: String,
    val webRootAttachedToForegroundRoot: Boolean,
    val webRootVisibleToUser: Boolean,
)

/** Maintains an already event-bound lease; it can never create foreground authority. */
internal object ChromeMediaShieldWebRootContinuityPolicy {
    fun verifies(
        evidence: ChromeMediaShieldWebRootEvidence,
        document: ChromeMediaShieldForegroundDocument,
    ): Boolean =
        ChromeMediaShieldForegroundContextPolicy.verifies(
            evidence =
                ChromeMediaShieldForegroundContextEvidence(
                    windowId = evidence.windowId,
                    rootPackageName = evidence.rootPackageName,
                    nativeRootUniqueId = evidence.nativeRootUniqueId,
                    exactAnchorCurrent =
                        evidence.webRootAttachedToForegroundRoot &&
                            evidence.webRootUniqueId == document.focusAnchor.webRootUniqueId,
                ),
            document = document,
        ) &&
            evidence.webRootPackageName == ChromePackageName &&
            evidence.webRootClassName == WebRootClassName &&
            evidence.webRootWindowId == document.windowId &&
            evidence.webRootUniqueId.isNotBlank() &&
            evidence.webRootUniqueId != evidence.nativeRootUniqueId &&
            evidence.webRootUniqueId == document.focusAnchor.webRootUniqueId &&
            evidence.webRootAttachedToForegroundRoot &&
            evidence.webRootVisibleToUser

    const val WebRootClassName = "android.webkit.WebView"
    private const val ChromePackageName = "com.android.chrome"
}

internal data class ChromeMediaShieldWebRootCandidateEvidence(
    val expectedWindowId: Int,
    val nativeRootUniqueId: String?,
    val candidatePackageName: String,
    val candidateClassName: String,
    val candidateWindowId: Int,
    val candidateUniqueId: String?,
)

/** Selects Chrome's browser-issued WebView root without requiring a native-root uniqueId. */
internal object ChromeMediaShieldWebRootCandidatePolicy {
    fun verifies(evidence: ChromeMediaShieldWebRootCandidateEvidence): Boolean {
        val candidateUniqueId = evidence.candidateUniqueId?.takeIf(String::isNotBlank) ?: return false
        val nativeRootUniqueId = evidence.nativeRootUniqueId?.takeIf(String::isNotBlank)
        return evidence.candidatePackageName == ChromePackageName &&
            evidence.candidateClassName == ChromeMediaShieldWebRootContinuityPolicy.WebRootClassName &&
            evidence.candidateWindowId == evidence.expectedWindowId &&
            (nativeRootUniqueId == null || candidateUniqueId != nativeRootUniqueId)
    }

    private const val ChromePackageName = "com.android.chrome"
}

/** Bounded, ownership-safe traversal of Chrome's virtual Accessibility tree. */
internal object ChromeMediaShieldAccessibilityNodeTraversal {
    @Suppress("DEPRECATION")
    fun copyWebDocumentRoot(
        source: AccessibilityNodeInfo,
        windowRoot: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
        val nativeRootUniqueId = uniqueIdOrNull(windowRoot)
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(source)
        try {
            repeat(MaximumAncestorDepth) {
                val node = current ?: return null
                if (node == windowRoot) return null
                if (
                    ChromeMediaShieldWebRootCandidatePolicy.verifies(
                        ChromeMediaShieldWebRootCandidateEvidence(
                            expectedWindowId = windowRoot.windowId,
                            nativeRootUniqueId = nativeRootUniqueId,
                            candidatePackageName = node.packageName?.toString().orEmpty(),
                            candidateClassName = node.className?.toString().orEmpty(),
                            candidateWindowId = node.windowId,
                            candidateUniqueId = uniqueIdOrNull(node),
                        ),
                    )
                ) {
                    return AccessibilityNodeInfo.obtain(node)
                }
                current = node.parent.also { recycle(node) }
            }
            return null
        } finally {
            current?.let(::recycle)
        }
    }

    fun webDocumentRootUniqueId(
        source: AccessibilityNodeInfo,
        windowRoot: AccessibilityNodeInfo,
    ): String? {
        val webRoot = copyWebDocumentRoot(source, windowRoot) ?: return null
        return try {
            uniqueIdOrNull(webRoot)
        } finally {
            recycle(webRoot)
        }
    }

    @Suppress("DEPRECATION")
    fun belongsToWindowRoot(
        source: AccessibilityNodeInfo,
        windowRoot: AccessibilityNodeInfo,
    ): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(source)
        try {
            repeat(MaximumAncestorDepth) {
                val node = current ?: return false
                if (node == windowRoot) return true
                current = node.parent.also { recycle(node) }
            }
            return false
        } finally {
            current?.let(::recycle)
        }
    }

    fun uniqueIdOrNull(node: AccessibilityNodeInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.uniqueId?.takeIf(String::isNotBlank)
        } else {
            null
        }

    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) {
        runCatching(node::recycle)
    }

    private const val MaximumAncestorDepth = 128
}
