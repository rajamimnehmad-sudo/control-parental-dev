package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class WebImageOverlayController(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val overlayViews = mutableListOf<View>()
    private var currentPlan = WebImageOverlayPlan.None

    fun apply(plan: WebImageOverlayPlan) {
        if (plan == currentPlan) return
        clear()
        if (plan.coverage == WebImageOverlayCoverage.None) return
        val appliedBounds = mutableListOf<OverlayBounds>()
        plan.bounds.forEach { bounds ->
            val view =
                View(service).apply {
                    setBackgroundColor(Color.WHITE)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            val params =
                WindowManager.LayoutParams(
                    bounds.width,
                    bounds.height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = bounds.left
                    y = bounds.top
                }
            runCatching { windowManager.addView(view, params) }
                .onSuccess {
                    overlayViews += view
                    appliedBounds += bounds
                }
        }
        currentPlan =
            if (appliedBounds.size == plan.bounds.size) {
                plan
            } else {
                WebImageOverlayPlan(plan.coverage, appliedBounds)
            }
    }

    fun clear() {
        overlayViews.forEach { view -> runCatching { windowManager.removeViewImmediate(view) } }
        overlayViews.clear()
        currentPlan = WebImageOverlayPlan.None
    }
}
