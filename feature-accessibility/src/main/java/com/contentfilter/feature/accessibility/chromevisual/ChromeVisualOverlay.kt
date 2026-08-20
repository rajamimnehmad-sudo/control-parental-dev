package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

internal enum class ChromeVisualOverlayState {
    Pending,
    Blocked,
}

internal class ChromeVisualOverlay(
    private val service: AccessibilityService,
) : AutoCloseable {
    private val windowManager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private val entries = mutableMapOf<String, OverlayEntry>()

    fun show(
        region: ChromeVisualRegion,
        state: ChromeVisualOverlayState,
    ): Boolean {
        if (entries[region.id]?.let { it.region == region && it.state == state } == true) return true
        remove(region.id)
        return attach(region, state, View(service))
    }

    fun remove(regionId: String) {
        entries.remove(regionId)?.let { runCatching { windowManager.removeViewImmediate(it.view) } }
    }

    fun retain(regionIds: Set<String>) {
        entries.keys.filterNot(regionIds::contains).toList().forEach(::remove)
    }

    fun clipBottom(maximumBottom: Int) {
        entries.values.toList().forEach { entry ->
            when {
                entry.region.top >= maximumBottom -> remove(entry.region.id)
                entry.region.bottom > maximumBottom ->
                    show(entry.region.copy(bottom = maximumBottom), entry.state)
            }
        }
    }

    override fun close() {
        entries.keys.toList().forEach(::remove)
    }

    private fun attach(
        region: ChromeVisualRegion,
        state: ChromeVisualOverlayState,
        view: View,
    ): Boolean {
        view.setBackgroundColor(
            when (state) {
                ChromeVisualOverlayState.Pending -> PendingColor
                ChromeVisualOverlayState.Blocked -> BlockedColor
            },
        )
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.isClickable = false
        view.isFocusable = false
        val params =
            WindowManager.LayoutParams(
                region.width,
                region.height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = region.left
                y = region.top
                title = "ChromeVisual:${region.id.take(32)}"
            }
        return runCatching {
            windowManager.addView(view, params)
            entries[region.id] = OverlayEntry(region, state, view)
        }.isSuccess
    }

    private data class OverlayEntry(
        val region: ChromeVisualRegion,
        val state: ChromeVisualOverlayState,
        val view: View,
    )

    private companion object {
        const val PendingColor = Color.BLACK
        val BlockedColor = Color.rgb(70, 0, 20)
    }
}
