package com.contentfilter.feature.accessibility.chromevisual

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

internal enum class ChromeMediaShieldBoundContextBinding {
    ExactFocusSource,
    Invalid,
}

internal object ChromeMediaShieldBoundContextPolicy {
    fun select(exactFocusSourceCurrent: Boolean): ChromeMediaShieldBoundContextBinding =
        if (exactFocusSourceCurrent) {
            ChromeMediaShieldBoundContextBinding.ExactFocusSource
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

/** Bounded, ownership-safe traversal of Chrome's virtual Accessibility tree. */
internal object ChromeMediaShieldAccessibilityNodeTraversal {
    /**
     * Reacquires only the exact secret anchor first authenticated by TYPE_VIEW_FOCUSED.
     *
     * Chrome does not implement virtual-node lookup by view id consistently, so the bounded tree
     * walk compares both the browser-issued unique id and the unguessable document view id. The
     * browser-issued unique id is unique within the window, so the walk can stop at the first
     * node matching both identities instead of scanning a large real-web tree after success. The
     * returned node is owned by the caller and must be recycled or transferred.
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
