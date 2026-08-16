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
        if (targetKey != key) return false
        val surfaceRect = targetRect?.takeUnless(Rect::isEmpty) ?: return false
        val (originX, originY) = overlayOrigin()
        val displayRect = Rect(surfaceRect).apply { offset(originX, originY) }
        if (displayRect.width() <= 0 || displayRect.height() <= 0) return false
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener { _, event ->
            if (
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                !displayRect.contains(event.x.toInt(), event.y.toInt())
            ) {
                clear()
            }
            false
        }
        frame.setImageDrawable(null)
        frame.setBackgroundColor(blockedColor)
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
        placeholderKey = key
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        return true
    }

    fun clearForTab(tabId: Long) {
        if (placeholderKey?.tabId == tabId || targetKey?.tabId == tabId) clear()
    }

    fun prepareFullCover() {
        if (placeholderKey != null) clearFrame()
        placeholderKey = null
        overlay.setBackgroundColor(fullCoverColor)
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.setOnTouchListener { _, _ -> true }
        label.visibility = View.VISIBLE
    }

    fun clear() {
        clearFrame()
        targetKey = null
        targetRect = null
        placeholderKey = null
        overlay.visibility = View.GONE
        overlay.setBackgroundColor(fullCoverColor)
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.setOnTouchListener { _, _ -> true }
        label.visibility = View.VISIBLE
    }

    private fun clearFrame() {
        frame.setOnTouchListener(null)
        frame.isClickable = false
        frame.setImageDrawable(null)
        frame.setBackgroundColor(Color.TRANSPARENT)
        frame.visibility = View.GONE
    }
}
