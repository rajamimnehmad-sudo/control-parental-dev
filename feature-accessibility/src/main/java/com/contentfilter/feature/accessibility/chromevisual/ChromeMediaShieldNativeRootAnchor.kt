package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromeMediaShieldNativeRootBindingKind {
    PlatformUniqueId,
    RetainedNode,
}

internal data class ChromeMediaShieldNativeRootIdentity(
    val value: String,
    val kind: ChromeMediaShieldNativeRootBindingKind,
)

/**
 * Session-local identity for a native Chrome root when Android omits `AccessibilityNodeInfo.uniqueId`.
 *
 * The fallback is not `windowId`: it owns one copied root and reuses its generation only while the
 * copy refreshes and compares equal to the newly borrowed platform root. Replacement, detach, close
 * or window change closes the old copy and rotates the identity.
 */
internal class ChromeMediaShieldNativeRootAnchor<T>(
    private val copy: (T) -> T?,
    private val refresh: (T) -> Boolean,
    private val sameNode: (T, T) -> Boolean,
    private val closeResource: (T) -> Unit,
) : AutoCloseable {
    private var generation = 0L
    private var retained: Retained<T>? = null

    fun identify(
        borrowedRoot: T,
        windowId: Int,
        platformUniqueId: String?,
    ): ChromeMediaShieldNativeRootIdentity? {
        if (windowId < 0) return null
        platformUniqueId?.takeIf(String::isNotBlank)?.let { uniqueId ->
            invalidate()
            return ChromeMediaShieldNativeRootIdentity(
                value = "native-root:$uniqueId",
                kind = ChromeMediaShieldNativeRootBindingKind.PlatformUniqueId,
            )
        }
        retained?.let { current ->
            if (
                current.windowId == windowId &&
                runCatching { refresh(current.resource) }.getOrDefault(false) &&
                runCatching { sameNode(current.resource, borrowedRoot) }.getOrDefault(false)
            ) {
                return current.identity
            }
        }
        invalidate()
        val owned = runCatching { copy(borrowedRoot) }.getOrNull() ?: return null
        generation = if (generation == Long.MAX_VALUE) generation else generation + 1L
        val identity =
            ChromeMediaShieldNativeRootIdentity(
                value = "native-root-anchor:$windowId:$generation",
                kind = ChromeMediaShieldNativeRootBindingKind.RetainedNode,
            )
        retained = Retained(windowId, identity, owned)
        return identity
    }

    override fun close() = invalidate()

    private fun invalidate() {
        retained?.resource?.let { runCatching { closeResource(it) } }
        retained = null
    }

    private data class Retained<T>(
        val windowId: Int,
        val identity: ChromeMediaShieldNativeRootIdentity,
        val resource: T,
    )
}
