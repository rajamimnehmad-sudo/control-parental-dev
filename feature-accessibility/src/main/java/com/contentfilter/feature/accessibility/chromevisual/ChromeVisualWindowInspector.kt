package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

internal class ChromeVisualWindowInspector(
    private val service: AccessibilityService,
) {
    fun isChromePackage(packageName: CharSequence?): Boolean = packageName?.toString() == ChromePackageName

    fun find(
        requestedWindowId: Int,
        allowBehindInputMethod: Boolean = false,
    ): AccessibilityWindowInfo? {
        val candidates =
            service.windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == ChromePackageName
            }
        return candidates
            .firstOrNull { requestedWindowId != AnyWindowId && it.id == requestedWindowId }
            ?.takeIf { candidate ->
                ChromeVisualWindowSelectionPolicy.canUseExactCandidate(
                    isActive = candidate.isActive,
                    isFocused = candidate.isFocused,
                    allowBehindInputMethod = allowBehindInputMethod,
                )
            }
            ?: candidates.firstOrNull { it.isActive }
            ?: candidates.firstOrNull { it.isFocused }
            ?: candidates.firstOrNull().takeIf { allowBehindInputMethod }
    }

    /** Fail-closed foreground selection for H19 READY authority; no arbitrary first-window fallback. */
    fun findUniqueForeground(): AccessibilityWindowInfo? {
        val candidates =
            service.windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == ChromePackageName &&
                    (window.isActive || window.isFocused)
            }
        return candidates.singleOrNull()
    }

    fun inputMethodTop(): Int? =
        service.windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                val bounds = Rect()
                window.root?.getBoundsInScreen(bounds)
                bounds.top.takeIf { !bounds.isEmpty }
            }
            .minOrNull()

    fun pageIdentity(window: AccessibilityWindowInfo): Long {
        var hash = FnvOffsetBasis
        val value = window.title?.toString().orEmpty().ifBlank { "window:${window.id}" }
        value.forEach { character -> hash = (hash xor character.code.toLong()) * FnvPrime }
        return hash
    }

    fun viewport(window: AccessibilityWindowInfo): ChromeVisualViewport? {
        val root = window.root ?: return null
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return ChromeVisualViewport(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .takeIf { it.width > 0 && it.height > 0 }
    }

    fun navigationInsets(): ChromeVisualShieldNavigationInsets {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ChromeVisualShieldNavigationInsets.Zero
        val windowManager = service.getSystemService(WindowManager::class.java)
        val insets =
            windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars(),
            )
        return ChromeVisualShieldNavigationInsets(insets.left, insets.right, insets.bottom)
    }

    fun collectCandidates(window: AccessibilityWindowInfo): List<ChromeVisualNodeCandidate> {
        val root = window.root ?: return emptyList()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val result = mutableListOf<ChromeVisualNodeCandidate>()
        queue += root
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MaxAccessibilityNodes) {
            val node = queue.removeFirst()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                result +=
                    ChromeVisualNodeCandidate(
                        className = node.className?.toString().orEmpty(),
                        hasDescription = !node.contentDescription.isNullOrBlank(),
                        childCount = node.childCount,
                        region =
                            ChromeVisualRegion(
                                id = "node_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}",
                                left = rect.left,
                                top = rect.top,
                                right = rect.right,
                                bottom = rect.bottom,
                            ),
                    )
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return result
    }

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val AnyWindowId = -1
        const val MaxAccessibilityNodes = 400
        const val FnvOffsetBasis = -3750763034362895579L
        const val FnvPrime = 1099511628211L
    }
}

internal object ChromeVisualWindowSelectionPolicy {
    fun canUseExactCandidate(
        isActive: Boolean,
        isFocused: Boolean,
        allowBehindInputMethod: Boolean,
    ): Boolean = isActive || isFocused || allowBehindInputMethod
}
