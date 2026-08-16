package com.contentfilter.dagbrowser

import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Keeps a terminal video block local to the last verified video rectangle. */
internal class DagVideoBlockedPlaceholderPresenter(
    private val overlay: FrameLayout,
    private val frame: ImageView,
    private val label: TextView,
    private val fullCoverColor: Int,
    private val blockedColor: Int,
    private val overlayOrigin: () -> Pair<Int, Int>,
) {
    private var targetKey: DagVideoLabKey? = null
    private var targetRect: Rect? = null
    private var placeholderKey: DagVideoLabKey? = null

    fun rememberTarget(
        key: DagVideoLabKey,
        surfaceRect: Rect,
    ) {
        if (surfaceRect.isEmpty) return
        targetKey = key
        targetRect = Rect(surfaceRect)
    }

    fun show(key: DagVideoLabKey): Boolean {
        return showLocalized(key, blockedColor, clearOutside = true)
    }

    fun showProtection(key: DagVideoLabKey): Boolean {
        return showLocalized(key, fullCoverColor, clearOutside = false)
    }

    private fun showLocalized(
        key: DagVideoLabKey,
        color: Int,
        clearOutside: Boolean,
    ): Boolean {
        if (targetKey != key) return false
        val surfaceRect = targetRect?.takeUnless(Rect::isEmpty) ?: return false
        val (originX, originY) = overlayOrigin()
        val displayRect = Rect(surfaceRect).apply { offset(originX, originY) }
        if (displayRect.width() <= 0 || displayRect.height() <= 0) return false
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener(
            if (clearOutside) {
                View.OnTouchListener { _, event ->
                    if (
                        event.actionMasked == MotionEvent.ACTION_DOWN &&
                        !displayRect.contains(event.x.toInt(), event.y.toInt())
                    ) {
                        clear()
                    }
                    false
                }
            } else {
                null
            },
        )
        frame.setImageDrawable(null)
        frame.setBackgroundColor(color)
        frame.layoutParams =
            FrameLayout.LayoutParams(displayRect.width(), displayRect.height()).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = displayRect.left
                topMargin = displayRect.top
            }
        frame.isClickable = true
        frame.setOnTouchListener { _, _ -> true }
        frame.visibility = View.VISIBLE
        label.visibility = View.GONE
        placeholderKey = key.takeIf { clearOutside }
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        return true
    }

    fun clearForTab(tabId: Long) {
        if (placeholderKey?.tabId == tabId || targetKey?.tabId == tabId) clear()
    }

    fun clear() {
        clearFrame()
        targetKey = null
        targetRect = null
        placeholderKey = null
        overlay.visibility = View.GONE
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener(null)
        label.visibility = View.GONE
    }

    private fun clearFrame() {
        frame.setOnTouchListener(null)
        frame.isClickable = false
        frame.setImageDrawable(null)
        frame.setBackgroundColor(Color.TRANSPARENT)
        frame.visibility = View.GONE
    }
}
