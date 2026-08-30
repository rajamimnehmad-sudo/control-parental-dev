package com.contentfilter.feature.accessibility.chromevisual

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

internal enum class ChromeMediaShieldBoundContextBinding {
    ExactEventSource,
    Invalid,
}

internal data class ChromeMediaShieldRootEventFallbackEvidence(
    val eventType: Int,
    val contentChangeTypes: Int,
    val eventPackageName: String,
    val eventWindowId: Int,
    val sourcePackageName: String,
    val sourceWindowId: Int,
    val sourceClassName: String,
    val sourceIsWindowRoot: Boolean,
)

/** Allows one bounded lookup only when Chrome emits the current document change from its root. */
internal object ChromeMediaShieldRootEventFallbackPolicy {
    fun permits(
        evidence: ChromeMediaShieldRootEventFallbackEvidence,
        expectedWindowId: Int,
        alreadyAttempted: Boolean,
    ): Boolean =
        !alreadyAttempted &&
            evidence.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            evidence.contentChangeTypes != AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED &&
            evidence.contentChangeTypes and AllowedContentChangeTypes.inv() == 0 &&
            evidence.eventPackageName == ChromePackageName &&
            evidence.sourcePackageName == ChromePackageName &&
            evidence.eventWindowId == expectedWindowId &&
            evidence.sourceWindowId == expectedWindowId &&
            (
                evidence.sourceClassName == ChromeMediaShieldWebRootContract.ClassName ||
                    evidence.sourceIsWindowRoot
            )

    // Diagnostic lookup is reserved for an accessible-name mutation. A preceding host insertion
    // (SUBTREE/UNDEFINED) must not consume the one-shot budget.
    private const val AllowedContentChangeTypes =
        AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
    private const val ChromePackageName = "com.android.chrome"
}

internal object ChromeMediaShieldBoundContextPolicy {
    fun select(exactEventSourceCurrent: Boolean): ChromeMediaShieldBoundContextBinding =
        if (exactEventSourceCurrent) {
            ChromeMediaShieldBoundContextBinding.ExactEventSource
        } else {
            ChromeMediaShieldBoundContextBinding.Invalid
        }
}

internal object ChromeMediaShieldBoundAnchorRebindPolicy {
    fun permits(
        eventBoundClaim: ChromeMediaShieldReadyClaim?,
        expectedClaim: ChromeMediaShieldReadyClaim,
        document: ChromeMediaShieldForegroundDocument,
        currentWindowId: Int,
        hasBoundSource: Boolean,
        hasBoundWebRoot: Boolean,
        rebindAlreadyAttempted: Boolean = false,
    ): Boolean =
        eventBoundClaim == expectedClaim &&
            document.identity == expectedClaim.identity &&
            document.lifecycleSequence == expectedClaim.lifecycleSequence &&
            document.windowId == currentWindowId &&
            hasBoundSource &&
            hasBoundWebRoot &&
            !rebindAlreadyAttempted
}

internal sealed interface ChromeMediaShieldOwnedNodeSearchResult<out T> {
    data class Found<T>(
        val node: T,
    ) : ChromeMediaShieldOwnedNodeSearchResult<T>

    data object Absent : ChromeMediaShieldOwnedNodeSearchResult<Nothing>

    data object Ambiguous : ChromeMediaShieldOwnedNodeSearchResult<Nothing>

    data object Overflow : ChromeMediaShieldOwnedNodeSearchResult<Nothing>
}

/** Finds one exact browser-owned node while closing every candidate it does not return. */
internal class ChromeMediaShieldBoundedOwnedNodeSearch<T>(
    private val maximumNodeReads: Int,
) {
    init {
        require(maximumNodeReads > 0)
    }

    fun findDescendant(
        borrowedRoot: T,
        childCount: (T) -> Int,
        copyChild: (T, Int) -> T?,
        isExactMatch: (T) -> Boolean,
        close: (T) -> Unit,
    ): ChromeMediaShieldOwnedNodeSearchResult<T> {
        val queue = ArrayDeque<OwnedNode<T>>()
        var current: OwnedNode<T>? = OwnedNode(borrowedRoot, owned = false)
        var candidate: T? = null
        var nodeReads = 0
        try {
            while (current != null) {
                val parent = checkNotNull(current)
                val count = childCount(parent.node)
                for (index in 0 until count) {
                    if (nodeReads++ >= maximumNodeReads) {
                        closeOwned(parent, close)
                        current = null
                        closeQueue(queue, close)
                        return ChromeMediaShieldOwnedNodeSearchResult.Overflow
                    }
                    val child = copyChild(parent.node, index) ?: continue
                    candidate = child
                    if (isExactMatch(child)) {
                        closeOwned(parent, close)
                        current = null
                        closeQueue(queue, close)
                        candidate = null
                        return ChromeMediaShieldOwnedNodeSearchResult.Found(child)
                    }
                    queue.addLast(OwnedNode(child, owned = true))
                    candidate = null
                }
                closeOwned(parent, close)
                current = null
                current = queue.removeFirstOrNull()
            }
            return ChromeMediaShieldOwnedNodeSearchResult.Absent
        } catch (error: RuntimeException) {
            candidate?.let(close)
            current?.let { closeOwned(it, close) }
            closeQueue(queue, close)
            throw error
        }
    }

    /** Finds exactly one match; zero, multiple or an over-budget tree fail closed. */
    fun findUniqueDescendant(
        borrowedRoot: T,
        childCount: (T) -> Int,
        copyChild: (T, Int) -> T?,
        isExactMatch: (T) -> Boolean,
        close: (T) -> Unit,
    ): ChromeMediaShieldOwnedNodeSearchResult<T> {
        val queue = ArrayDeque<OwnedNode<T>>()
        var current: OwnedNode<T>? = OwnedNode(borrowedRoot, owned = false)
        var candidate: T? = null
        var match: T? = null
        var nodeReads = 0
        try {
            while (current != null) {
                val parent = checkNotNull(current)
                val count = childCount(parent.node)
                for (index in 0 until count) {
                    if (nodeReads++ >= maximumNodeReads) {
                        match?.let(close)
                        match = null
                        closeOwned(parent, close)
                        current = null
                        closeQueue(queue, close)
                        return ChromeMediaShieldOwnedNodeSearchResult.Overflow
                    }
                    val child = copyChild(parent.node, index) ?: continue
                    candidate = child
                    if (isExactMatch(child)) {
                        if (match != null) {
                            close(checkNotNull(match))
                            match = null
                            close(child)
                            candidate = null
                            closeOwned(parent, close)
                            current = null
                            closeQueue(queue, close)
                            return ChromeMediaShieldOwnedNodeSearchResult.Ambiguous
                        }
                        match = child
                    } else {
                        queue.addLast(OwnedNode(child, owned = true))
                    }
                    candidate = null
                }
                closeOwned(parent, close)
                current = null
                current = queue.removeFirstOrNull()
            }
            return match
                ?.let { ChromeMediaShieldOwnedNodeSearchResult.Found(it) }
                ?: ChromeMediaShieldOwnedNodeSearchResult.Absent
        } catch (error: RuntimeException) {
            candidate?.let(close)
            match?.let(close)
            current?.let { closeOwned(it, close) }
            closeQueue(queue, close)
            throw error
        }
    }

    private fun closeQueue(
        queue: ArrayDeque<OwnedNode<T>>,
        close: (T) -> Unit,
    ) {
        queue.forEach { closeOwned(it, close) }
        queue.clear()
    }

    private fun closeOwned(
        node: OwnedNode<T>,
        close: (T) -> Unit,
    ) {
        if (node.owned) close(node.node)
    }

    private data class OwnedNode<T>(
        val node: T,
        val owned: Boolean,
    )
}

/** WebView ancestry is context evidence only; it is never a release boundary. */
internal object ChromeMediaShieldWebRootContract {
    const val ClassName = "android.webkit.WebView"
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
            evidence.candidateClassName == ChromeMediaShieldWebRootContract.ClassName &&
            evidence.candidateWindowId == evidence.expectedWindowId &&
            (nativeRootUniqueId == null || candidateUniqueId != nativeRootUniqueId)
    }

    private const val ChromePackageName = "com.android.chrome"
}

internal data class ChromeMediaShieldCurrentWebRootEvidence(
    val expectedWindowId: Int,
    val windowRootPackageName: String,
    val nativeWindowRootUniqueId: String,
    val candidatePackageName: String,
    val candidateClassName: String,
    val candidateWindowId: Int,
    val candidateUniqueId: String?,
    val candidateVisibleToUser: Boolean,
    val candidateAttachedToWindowRoot: Boolean,
)

/**
 * Continues authority created by one exact focus event only while the same browser-issued WebView
 * root is the current visible document root of the exact Chrome window.
 *
 * This never searches for a token and cannot create authority. A page cannot nominate the
 * browser-issued [AccessibilityNodeInfo.getUniqueId] captured from the original event source.
 */
internal object ChromeMediaShieldCurrentWebRootPolicy {
    fun verifies(
        evidence: ChromeMediaShieldCurrentWebRootEvidence,
        expectedWebRootUniqueId: String,
        expectedRootIdentityDigest: String,
    ): Boolean =
        expectedWebRootUniqueId.isNotBlank() &&
            ChromeMediaShieldForegroundContextPolicy.bindingDigest(
                nativeRootUniqueId = evidence.nativeWindowRootUniqueId,
                webRootUniqueId = expectedWebRootUniqueId,
            ) == expectedRootIdentityDigest &&
            evidence.windowRootPackageName == ChromePackageName &&
            evidence.candidatePackageName == ChromePackageName &&
            evidence.candidateClassName == ChromeMediaShieldWebRootContract.ClassName &&
            evidence.candidateWindowId == evidence.expectedWindowId &&
            evidence.candidateUniqueId == expectedWebRootUniqueId &&
            evidence.candidateVisibleToUser &&
            evidence.candidateAttachedToWindowRoot

    private const val ChromePackageName = "com.android.chrome"
}

/** Bounded, ownership-safe traversal of Chrome's virtual Accessibility tree. */
internal object ChromeMediaShieldAccessibilityNodeTraversal {
    /** One-shot fallback awakened by a current browser content-change event; never passive scan. */
    @Suppress("DEPRECATION")
    fun copyUniqueReadyAnchorCandidate(
        windowRoot: AccessibilityNodeInfo,
        expectedViewIdResourceName: String,
        markerMatches: (contentDescription: String?, text: String?) -> Boolean,
    ): ChromeMediaShieldOwnedNodeSearchResult<AccessibilityNodeInfo> {
        if (expectedViewIdResourceName.isBlank()) return ChromeMediaShieldOwnedNodeSearchResult.Absent
        return ChromeMediaShieldBoundedOwnedNodeSearch<AccessibilityNodeInfo>(MaximumCurrentTreeNodes)
            .findUniqueDescendant(
                borrowedRoot = windowRoot,
                childCount = AccessibilityNodeInfo::getChildCount,
                copyChild = AccessibilityNodeInfo::getChild,
                isExactMatch = { node ->
                    node.viewIdResourceName == expectedViewIdResourceName &&
                        markerMatches(
                            node.contentDescription?.toString(),
                            node.text?.toString(),
                        )
                },
                close = ::recycle,
            )
    }

    /**
     * Reacquires only the exact document host first authenticated by the secret focus event.
     *
     * Chrome does not implement virtual-node lookup by view id consistently, so the bounded tree
     * walk compares the fixed protected host id and its browser-issued unique id. The secret READY
     * marker stays inside the host's closed shadow root and authenticates the initial focus event;
     * page-created hosts cannot reproduce the captured platform id. The walk can therefore stop at
     * the first exact match. The returned node is owned by the caller and must be recycled or
     * transferred.
     */
    @Suppress("DEPRECATION")
    fun copyExactBoundAnchorCandidate(
        windowRoot: AccessibilityNodeInfo,
        expectedViewIdResourceName: String,
        expectedUniqueId: String,
    ): ChromeMediaShieldOwnedNodeSearchResult<AccessibilityNodeInfo> {
        if (expectedViewIdResourceName.isBlank() || expectedUniqueId.isBlank()) {
            return ChromeMediaShieldOwnedNodeSearchResult.Absent
        }
        return ChromeMediaShieldBoundedOwnedNodeSearch<AccessibilityNodeInfo>(MaximumCurrentTreeNodes)
            .findDescendant(
                borrowedRoot = windowRoot,
                childCount = AccessibilityNodeInfo::getChildCount,
                copyChild = AccessibilityNodeInfo::getChild,
                isExactMatch = { node ->
                    node.viewIdResourceName == expectedViewIdResourceName &&
                        uniqueIdOrNull(node) == expectedUniqueId
                },
                close = ::recycle,
            )
    }

    /** Reacquires only the exact browser-issued WebView root authenticated by the focus event. */
    @Suppress("DEPRECATION")
    fun copyExactWebDocumentRootCandidate(
        windowRoot: AccessibilityNodeInfo,
        expectedWindowId: Int,
        expectedUniqueId: String,
        expectedRootIdentityDigest: String,
    ): ChromeMediaShieldOwnedNodeSearchResult<AccessibilityNodeInfo> {
        if (expectedUniqueId.isBlank()) return ChromeMediaShieldOwnedNodeSearchResult.Absent
        return ChromeMediaShieldBoundedOwnedNodeSearch<AccessibilityNodeInfo>(MaximumCurrentTreeNodes)
            .findUniqueDescendant(
                borrowedRoot = windowRoot,
                childCount = AccessibilityNodeInfo::getChildCount,
                copyChild = AccessibilityNodeInfo::getChild,
                isExactMatch = { node ->
                    ChromeMediaShieldCurrentWebRootPolicy.verifies(
                        evidence =
                            ChromeMediaShieldCurrentWebRootEvidence(
                                expectedWindowId = expectedWindowId,
                                windowRootPackageName = windowRoot.packageName?.toString().orEmpty(),
                                nativeWindowRootUniqueId = uniqueIdOrNull(windowRoot).orEmpty(),
                                candidatePackageName = node.packageName?.toString().orEmpty(),
                                candidateClassName = node.className?.toString().orEmpty(),
                                candidateWindowId = node.windowId,
                                candidateUniqueId = uniqueIdOrNull(node),
                                candidateVisibleToUser = node.isVisibleToUser,
                                candidateAttachedToWindowRoot = belongsToWindowRoot(node, windowRoot),
                            ),
                        expectedWebRootUniqueId = expectedUniqueId,
                        expectedRootIdentityDigest = expectedRootIdentityDigest,
                    )
                },
                close = ::recycle,
            )
    }

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
    private const val MaximumCurrentTreeNodes = 512
}
