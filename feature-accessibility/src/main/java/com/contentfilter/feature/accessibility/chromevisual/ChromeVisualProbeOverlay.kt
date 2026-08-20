package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager

internal data class ProbeOverlayRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun asRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        fun centeredRight(
            windowWidth: Int,
            windowHeight: Int,
            density: Float,
        ): ProbeOverlayRegion {
            val desiredWidth = (120f * density).toInt().coerceAtLeast(1)
            val desiredHeight = (72f * density).toInt().coerceAtLeast(1)
            val margin = (24f * density).toInt().coerceAtLeast(0)
            val width = desiredWidth.coerceAtMost((windowWidth - margin * 2).coerceAtLeast(1))
            val height = desiredHeight.coerceAtMost((windowHeight - margin * 2).coerceAtLeast(1))
            val left = (windowWidth - margin - width).coerceAtLeast(0)
            val top = ((windowHeight - height) / 2).coerceAtLeast(0)
            return ProbeOverlayRegion(left, top, left + width, top + height)
        }
    }
}

internal class ChromeVisualProbeOverlay private constructor(
    private val windowManager: WindowManager,
    private val view: View,
) : AutoCloseable {
    override fun close() {
        runCatching { windowManager.removeViewImmediate(view) }
    }

    companion object {
        fun attach(
            service: AccessibilityService,
            windowId: Int,
            windowWidth: Int,
            windowHeight: Int,
            region: ProbeOverlayRegion,
        ): ChromeVisualProbeOverlay? {
            val windowManager = service.getSystemService(WindowManager::class.java) ?: return null
            val view = ProbeOverlayView(service)
            val layoutParams =
                WindowManager.LayoutParams(
                    region.width,
                    region.height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = region.left
                    y = region.top
                    title = "ChromeVisualProbe"
                }
            return runCatching {
                require(windowId >= 0 && windowWidth > 0 && windowHeight > 0)
                windowManager.addView(view, layoutParams)
                ChromeVisualProbeOverlay(windowManager, view)
            }.getOrNull()
        }
    }
}

private class ProbeOverlayView(
    service: AccessibilityService,
) : View(service) {
    private val paint = Paint().apply { color = Color.rgb(180, 0, 80) }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
