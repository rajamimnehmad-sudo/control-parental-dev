package com.contentfilter.dagbrowser

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
    private var noticeKey: DagVideoLabKey? = null
    private var noticeDismissal: Runnable? = null

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

    fun showFullscreenTransition(key: DagVideoLabKey): Boolean {
        cancelNotice()
        if (targetKey != key) return false
        clearFrame()
        placeholderKey = null
        overlay.setBackgroundColor(fullCoverColor)
        overlay.isClickable = true
        overlay.isFocusable = true
        overlay.setOnTouchListener { _, _ -> true }
        label.background = null
        label.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        return true
    }

    fun enableReplayInteraction(
        key: DagVideoLabKey,
        onTouch: (MotionEvent) -> Unit,
    ): Boolean {
        if (targetKey != key || frame.visibility != View.VISIBLE) return false
        val surfaceRect = targetRect?.takeUnless(Rect::isEmpty) ?: return false
        val (originX, originY) = overlayOrigin()
        val displayRect = Rect(surfaceRect).apply { offset(originX, originY) }
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener(null)
        frame.isClickable = true
        frame.setOnTouchListener { _, event ->
            onTouch(event)
            true
        }
        return true
    }

    fun showNativePlayback(key: DagVideoLabKey): Boolean {
        cancelNotice()
        if (targetKey != key) return false
        clearFrame()
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener(null)
        label.visibility = View.GONE
        overlay.visibility = View.GONE
        return true
    }

    fun targetSurfaceRect(key: DagVideoLabKey): Rect? =
        targetRect?.takeIf { targetKey == key && !it.isEmpty }?.let(::Rect)

    private fun showLocalized(
        key: DagVideoLabKey,
        color: Int,
        clearOutside: Boolean,
    ): Boolean {
        cancelNotice()
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

    fun showSafeSkipNotice(
        key: DagVideoLabKey,
        message: String,
        durationMillis: Long = NoticeDurationMillis,
    ): Boolean {
        cancelNotice()
        if (targetKey != key) return false
        val surfaceRect = targetRect?.takeUnless(Rect::isEmpty) ?: return false
        val (originX, originY) = overlayOrigin()
        val displayRect = Rect(surfaceRect).apply { offset(originX, originY) }
        val horizontalMargin = label.dp(12)
        val bottomMargin = label.dp(12)
        val availableWidth = displayRect.width() - horizontalMargin * 2
        if (availableWidth <= 0 || displayRect.height() <= 0) return false

        clearFrame()
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.setOnTouchListener(null)
        label.text = message
        label.setTextColor(Color.WHITE)
        label.setPadding(label.dp(14), label.dp(8), label.dp(14), label.dp(8))
        label.background =
            GradientDrawable().apply {
                cornerRadius = label.dp(18).toFloat()
                setColor(NoticeBackgroundColor)
            }
        label.layoutParams =
            FrameLayout.LayoutParams(availableWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = displayRect.left + horizontalMargin
                topMargin = (displayRect.bottom - bottomMargin - label.dp(48)).coerceAtLeast(displayRect.top)
            }
        label.visibility = View.VISIBLE
        label.elevation = label.dp(4).toFloat()
        noticeKey = key
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        val dismissal =
            Runnable {
                if (noticeKey != key) return@Runnable
                noticeKey = null
                noticeDismissal = null
                label.visibility = View.GONE
                label.background = null
                if (frame.visibility != View.VISIBLE) overlay.visibility = View.GONE
            }
        noticeDismissal = dismissal
        label.postDelayed(dismissal, durationMillis)
        return true
    }

    fun clearForTab(tabId: Long) {
        if (placeholderKey?.tabId == tabId || targetKey?.tabId == tabId) clear()
    }

    fun clear() {
        cancelNotice()
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

    private fun cancelNotice() {
        noticeDismissal?.let(label::removeCallbacks)
        noticeDismissal = null
        noticeKey = null
        label.visibility = View.GONE
        label.background = null
    }

    private fun clearFrame() {
        frame.setOnTouchListener(null)
        frame.isClickable = false
        frame.setImageDrawable(null)
        frame.setBackgroundColor(Color.TRANSPARENT)
        frame.visibility = View.GONE
    }

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val NoticeDurationMillis = 2_200L
        const val NoticeBackgroundColor = -0xCCB5A5
    }
}
