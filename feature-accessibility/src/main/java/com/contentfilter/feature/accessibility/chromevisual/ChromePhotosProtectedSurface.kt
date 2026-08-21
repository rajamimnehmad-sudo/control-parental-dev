package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

internal data class ChromePhotosProtectedSurfaceStats(
    val attached: Boolean,
    val attachmentCount: Int,
    val layoutUpdateCount: Int,
    val viewport: ChromeVisualViewport?,
    val authorityEpoch: Long,
    val pendingEpoch: Long?,
    val discardedPendingFrameCount: Int,
)

/**
 * One opaque, non-touchable accessibility overlay for the whole protected Chrome viewport.
 *
 * The host is attached once and only updated in place until Chrome leaves. It owns every staged
 * bitmap and never makes itself transparent while armed. A frame swap happens inside one onDraw,
 * so a draw can only observe the old complete frame or the new complete frame.
 */
internal class ChromePhotosProtectedSurface(
    service: AccessibilityService,
) : AutoCloseable {
    private val windowManager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private val view = ProtectedSurfaceView(service)
    private var attached = false
    private var attachmentCount = 0
    private var layoutUpdateCount = 0
    private var viewport: ChromeVisualViewport? = null

    fun cover(
        targetViewport: ChromeVisualViewport,
        authorityEpoch: Long,
    ): Boolean {
        if (!isMainThread() || !ensureAttached(targetViewport)) return false
        view.cover(authorityEpoch)
        return true
    }

    /** Takes ownership of [frame], including when the operation is rejected. */
    fun stage(
        targetViewport: ChromeVisualViewport,
        frame: Bitmap,
        token: ChromePhotosProtectedSurfaceToken,
    ): Boolean {
        if (!isMainThread() || !ensureAttached(targetViewport)) {
            frame.recycleSafely()
            return false
        }
        return view.stage(frame, token.epoch, token.sequence)
    }

    fun stats(): ChromePhotosProtectedSurfaceStats =
        ChromePhotosProtectedSurfaceStats(
            attached = attached,
            attachmentCount = attachmentCount,
            layoutUpdateCount = layoutUpdateCount,
            viewport = viewport,
            authorityEpoch = view.authorityEpoch(),
            pendingEpoch = view.pendingEpoch(),
            discardedPendingFrameCount = view.discardedPendingFrameCount(),
        )

    override fun close() {
        if (!isMainThread()) return
        if (attached) {
            runCatching { windowManager.removeViewImmediate(view) }
            attached = false
        }
        viewport = null
        view.close()
    }

    private fun ensureAttached(targetViewport: ChromeVisualViewport): Boolean {
        if (targetViewport.width <= 0 || targetViewport.height <= 0) return false
        if (!attached) {
            val added =
                runCatching {
                    windowManager.addView(view, layoutParams(targetViewport))
                    true
                }.getOrDefault(false)
            if (!added) return false
            attached = true
            attachmentCount += 1
            viewport = targetViewport
            return true
        }
        if (viewport == targetViewport) return true
        val updated =
            runCatching {
                windowManager.updateViewLayout(view, layoutParams(targetViewport))
                true
            }.getOrDefault(false)
        if (!updated) return false
        viewport = targetViewport
        layoutUpdateCount += 1
        return true
    }

    private fun layoutParams(targetViewport: ChromeVisualViewport) =
        WindowManager.LayoutParams(
            targetViewport.width,
            targetViewport.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetViewport.left
            y = targetViewport.top
            title = SurfaceTitle
        }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private class ProtectedSurfaceView(
        service: AccessibilityService,
    ) : View(service), AutoCloseable {
        private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val frameDestination = Rect()
        private val statusPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16f * resources.displayMetrics.scaledDensity
            }
        private var frontFrame: Bitmap? = null
        private var pendingFrame: Bitmap? = null
        private var covered = true
        private var minimumEpoch = 0L
        private var presentedEpoch = 0L
        private var presentedSequence = 0L
        private var pendingEpoch = 0L
        private var pendingSequence = 0L
        private var discardedPendingFrameCount = 0

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            isFocusable = false
            setBackgroundColor(NeutralColor)
        }

        fun cover(authorityEpoch: Long) {
            if (authorityEpoch > minimumEpoch) minimumEpoch = authorityEpoch
            discardPendingFrameBelowMinimumEpoch()
            covered = true
            postInvalidateOnAnimation()
        }

        fun stage(
            frame: Bitmap,
            epoch: Long,
            sequence: Long,
        ): Boolean {
            if (
                frame.isRecycled ||
                epoch < minimumEpoch ||
                epoch < presentedEpoch ||
                (epoch == presentedEpoch && sequence <= presentedSequence) ||
                epoch < pendingEpoch ||
                (epoch == pendingEpoch && sequence <= pendingSequence)
            ) {
                frame.recycleSafely()
                return false
            }
            pendingFrame?.recycleSafely()
            pendingFrame = frame
            pendingEpoch = epoch
            pendingSequence = sequence
            covered = false
            postInvalidateOnAnimation()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            discardPendingFrameBelowMinimumEpoch()
            val staged = pendingFrame
            if (staged != null) {
                val retired = frontFrame
                frontFrame = staged
                pendingFrame = null
                presentedEpoch = pendingEpoch
                presentedSequence = pendingSequence
                pendingEpoch = 0L
                pendingSequence = 0L
                retired?.recycleSafely()
            }

            canvas.drawColor(NeutralColor)
            frontFrame?.takeUnless { it.isRecycled }?.let { frame ->
                frameDestination.set(0, 0, width, height)
                canvas.drawBitmap(frame, null, frameDestination, framePaint)
            }
            if (covered) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), CoverPaint)
                canvas.drawText(
                    PendingMessage,
                    HorizontalPaddingPx,
                    height - VerticalPaddingPx,
                    statusPaint,
                )
            }
            // DEV-only compositor marker. A physical recording can verify that the protected host
            // itself remained in the composed output for every sampled frame.
            canvas.drawRect(
                MarkerInsetPx,
                MarkerInsetPx,
                MarkerInsetPx + MarkerSizePx,
                MarkerInsetPx + MarkerSizePx,
                SurfaceMarkerPaint,
            )
        }

        fun authorityEpoch(): Long = minimumEpoch

        fun pendingEpoch(): Long? = pendingFrame?.let { pendingEpoch }

        fun discardedPendingFrameCount(): Int = discardedPendingFrameCount

        override fun close() {
            pendingFrame?.recycleSafely()
            pendingFrame = null
            frontFrame?.recycleSafely()
            frontFrame = null
            covered = true
            minimumEpoch = 0L
            presentedEpoch = 0L
            presentedSequence = 0L
            pendingEpoch = 0L
            pendingSequence = 0L
            discardedPendingFrameCount = 0
        }

        private fun discardPendingFrameBelowMinimumEpoch() {
            val stale = pendingFrame ?: return
            if (pendingEpoch >= minimumEpoch) return
            stale.recycleSafely()
            pendingFrame = null
            pendingEpoch = 0L
            pendingSequence = 0L
            discardedPendingFrameCount += 1
        }

        private companion object {
            const val HorizontalPaddingPx = 32f
            const val VerticalPaddingPx = 40f
            const val PendingMessage = "Analizando contenido nuevo…"
            const val NeutralColor = 0xFF202124.toInt()
            const val MarkerInsetPx = 8f
            const val MarkerSizePx = 32f
            val SurfaceMarkerPaint =
                Paint().apply {
                    color = 0xFF00C8FF.toInt()
                }
            val CoverPaint =
                Paint().apply {
                    color = 0xB31F2328.toInt()
                }
        }
    }

    private companion object {
        const val SurfaceTitle = "ChromePhotosProtectedSurface"
    }
}

private fun Bitmap?.recycleSafely() {
    if (this != null && !isRecycled) recycle()
}
