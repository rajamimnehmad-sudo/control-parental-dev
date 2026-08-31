package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.view.View

internal data class ChromePhotosProtectedSurfaceStats(
    val attached: Boolean,
    val attachmentCount: Int,
    val layoutUpdateCount: Int,
    val attachedWindowId: Int?,
    val hostExtent: Int,
    val viewport: ChromeVisualViewport?,
    val authorityEpoch: Long,
    val pendingEpoch: Long?,
    val discardedPendingFrameCount: Int,
    val transparent: Boolean,
    val transparencyGrantCount: Int,
    val alphaTransitionsOutstanding: Int,
    val alphaSubmitFailures: Long,
)

internal enum class ChromePhotosProtectedSurfaceCoverResult {
    Ready,
    Pending,
    Failed,
}

internal enum class ChromePhotosProtectedSurfaceRevokeResult {
    Submitted,
    NoSurface,
    Failed,
}

/** Process-scoped DEV diagnostic switch. It is always false unless a gate explicitly enables it. */
object ChromePhotosProtectedSurfaceDiagnostics {
    @Volatile
    private var markerEnabled = false

    fun setMarkerEnabledForExplicitDevGate(enabled: Boolean) {
        markerEnabled = enabled
    }

    internal fun isMarkerEnabled(): Boolean = markerEnabled
}

/**
 * One opaque, non-touchable accessibility surface for the whole protected Chrome viewport.
 *
 * The SurfaceControl host is attached to Chrome's own window so it inherits that window's rotation
 * transition instead of receiving the independent fade applied to TYPE_ACCESSIBILITY_OVERLAY
 * windows. The host has an empty touchable region and a rotation-invariant square extent. It owns
 * every staged bitmap. It may become compositor-transparent only for a current, explicit DEV
 * data-plane lease; the opaque buffer remains resident underneath and is restored fail-closed.
 * A frame swap happens inside one onDraw, so a draw can only observe the old complete frame or the
 * new complete frame.
 */
internal class ChromePhotosProtectedSurface(
    private val service: AccessibilityService,
    private val onHostPublicationChanged: () -> Unit = {},
    private val diagnosticMarkerEnabled: () -> Boolean =
        ChromePhotosProtectedSurfaceDiagnostics::isMarkerEnabled,
) : AutoCloseable {
    private var view: ProtectedSurfaceView? = null
    private var host: ChromePhotosProtectedSurfaceHost? = null
    private var attached = false
    private var attachmentCount = 0
    private var layoutUpdateCount = 0
    private var attachedWindowId: Int? = null
    private var hostExtent = 0
    private var viewport: ChromeVisualViewport? = null
    private val presentationPolicy = ChromePhotosDataPlanePresentationPolicy()
    private val transparentCommitGate = ChromePhotosTransparentCommitGate()
    private val alphaTracker = ChromePhotosProtectedSurfaceAlphaTracker()
    private var transparencyGrantCount = 0

    fun cover(
        targetWindowId: Int,
        targetViewport: ChromeVisualViewport,
        authorityEpoch: Long,
        onOpaqueCommitted: (Long) -> Unit = {},
    ): ChromePhotosProtectedSurfaceCoverResult {
        if (!isMainThread()) return ChromePhotosProtectedSurfaceCoverResult.Failed
        transparentCommitGate.invalidate()
        val result = ensureAttached(targetWindowId, targetViewport)
        if (result == ChromePhotosProtectedSurfaceHostResult.Failed) {
            return ChromePhotosProtectedSurfaceCoverResult.Failed
        }
        view?.cover(authorityEpoch) ?: return ChromePhotosProtectedSurfaceCoverResult.Failed
        presentationPolicy.cover(authorityEpoch)
        if (result == ChromePhotosProtectedSurfaceHostResult.Ready) {
            val currentHost = host ?: return ChromePhotosProtectedSurfaceCoverResult.Failed
            val alphaToken = alphaTracker.begin(ChromePhotosProtectedSurfaceAlpha.Opaque)
            val accepted =
                currentHost.presentOpaque {
                    alphaTracker.commit(alphaToken)
                    if (view?.authorityEpoch() != authorityEpoch) return@presentOpaque
                    if (!presentationPolicy.markOpaqueCommitted(authorityEpoch)) return@presentOpaque
                    onOpaqueCommitted(authorityEpoch)
                }
            if (!accepted) {
                alphaTracker.submissionFailed(alphaToken)
                return ChromePhotosProtectedSurfaceCoverResult.Failed
            }
        }
        return result.toCoverResult()
    }

    fun presentTransparent(lease: ChromePhotosDataPlaneLease): Boolean {
        if (!isMainThread() || !attached) return false
        transparentCommitGate.invalidate()
        val hostedView = view ?: return false
        val currentHost = host ?: return false
        if (!isCurrentLeaseBoundary(lease, hostedView, currentHost)) {
            return false
        }
        val alphaToken = alphaTracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)
        if (!currentHost.presentTransparent()) {
            alphaTracker.submissionFailed(alphaToken)
            return false
        }
        alphaTracker.submittedWithoutCallback(alphaToken)
        if (!presentationPolicy.markTransparent(lease)) {
            restoreOpaque()
            return false
        }
        transparencyGrantCount += 1
        return true
    }

    /**
     * Submits a transparent transaction but grants presentation authority only from Android's
     * committed callback. [recheckCurrent] runs at that boundary so a replaced document/window
     * can fail closed without receiving a successful completion.
     *
     * Returning `false` means no platform transaction was submitted and no callback will arrive.
     * Once this method returns `true`, [onCommitted] receives exactly one terminal result unless
     * the process itself dies.
     */
    fun presentTransparent(
        lease: ChromePhotosDataPlaneLease,
        recheckCurrent: () -> Boolean,
        onCommitted: (Boolean) -> Unit,
        onRejectedPlatformCommit: (ChromePhotosTransparentCommitOutcome) -> Unit = {},
    ): Boolean {
        if (!isMainThread() || !attached) return false
        val hostedView = view ?: return false
        val currentHost = host ?: return false
        if (!isCurrentLeaseBoundary(lease, hostedView, currentHost)) return false
        val token = transparentCommitGate.begin(onCommitted)
        val alphaToken = alphaTracker.begin(ChromePhotosProtectedSurfaceAlpha.Transparent)
        val submitted =
            currentHost.presentTransparent {
                alphaTracker.commit(alphaToken)
                val outcome =
                    transparentCommitGate.onTransactionCommitted(
                        token = token,
                        boundaryCurrent = {
                            isMainThread() &&
                                attached &&
                                host === currentHost &&
                                view === hostedView &&
                                isCurrentLeaseBoundary(lease, hostedView, currentHost) &&
                                recheckCurrent()
                        },
                        commitCurrent = {
                            if (!presentationPolicy.markTransparent(lease)) {
                                false
                            } else {
                                transparencyGrantCount += 1
                                true
                            }
                        },
                    )
                if (outcome != ChromePhotosTransparentCommitOutcome.Committed) {
                    onRejectedPlatformCommit(outcome)
                    if (alphaTracker.snapshot().mayBeTransparent) restoreOpaque()
                }
            }
        if (!submitted) {
            alphaTracker.submissionFailed(alphaToken)
            transparentCommitGate.reject(token)
        }
        return submitted
    }

    fun revokeTransparency(onOpaqueCommitted: (Boolean) -> Unit = {}): ChromePhotosProtectedSurfaceRevokeResult {
        if (!isMainThread()) return ChromePhotosProtectedSurfaceRevokeResult.Failed
        transparentCommitGate.invalidate()
        presentationPolicy.revoke()
        if (!attached || host == null) {
            alphaTracker.reset()
            onOpaqueCommitted(true)
            return ChromePhotosProtectedSurfaceRevokeResult.NoSurface
        }
        return if (restoreOpaque(onOpaqueCommitted)) {
            ChromePhotosProtectedSurfaceRevokeResult.Submitted
        } else {
            ChromePhotosProtectedSurfaceRevokeResult.Failed
        }
    }

    /** Takes ownership of [frame], including when the operation is rejected. */
    fun stage(
        targetViewport: ChromeVisualViewport,
        frame: Bitmap,
        token: ChromePhotosProtectedSurfaceToken,
    ): Boolean {
        if (
            !isMainThread() ||
            ensureAttached(token.windowId, targetViewport) !=
            ChromePhotosProtectedSurfaceHostResult.Ready
        ) {
            frame.recycleSafely()
            return false
        }
        val hostedView = view
        if (hostedView == null) {
            frame.recycleSafely()
            return false
        }
        return hostedView.stage(frame, token.epoch, token.sequence)
    }

    fun stats(): ChromePhotosProtectedSurfaceStats {
        val alpha = alphaTracker.snapshot()
        return ChromePhotosProtectedSurfaceStats(
            attached = attached,
            attachmentCount = attachmentCount,
            layoutUpdateCount = layoutUpdateCount,
            attachedWindowId = attachedWindowId,
            hostExtent = hostExtent,
            viewport = viewport,
            authorityEpoch = view?.authorityEpoch() ?: 0L,
            pendingEpoch = view?.pendingEpoch(),
            discardedPendingFrameCount = view?.discardedPendingFrameCount() ?: 0,
            transparent = alpha.mayBeTransparent,
            transparencyGrantCount = transparencyGrantCount,
            alphaTransitionsOutstanding = alpha.pendingTransitions,
            alphaSubmitFailures = alpha.submitFailures,
        )
    }

    override fun close() {
        if (!isMainThread()) return
        transparentCommitGate.invalidate()
        detachAndReleaseHost()
        viewport = null
        presentationPolicy.reset()
        alphaTracker.reset()
    }

    private fun ensureAttached(
        targetWindowId: Int,
        targetViewport: ChromeVisualViewport,
    ): ChromePhotosProtectedSurfaceHostResult {
        if (targetWindowId < 0 || targetViewport.width <= 0 || targetViewport.height <= 0) {
            return ChromePhotosProtectedSurfaceHostResult.Failed
        }
        if (!attached) {
            return createAndAttachHost(targetWindowId, targetViewport)
        }
        val currentHost = host ?: return ChromePhotosProtectedSurfaceHostResult.Failed
        val previousExtent = currentHost.extent
        val result = currentHost.ensureWindowAndExtent(targetWindowId, targetViewport)
        if (result == ChromePhotosProtectedSurfaceHostResult.Failed) return result
        if (currentHost.extent > previousExtent) {
            layoutUpdateCount += 1
        }
        attachedWindowId = currentHost.windowId
        hostExtent = currentHost.extent
        viewport = targetViewport
        return result
    }

    private fun isCurrentLeaseBoundary(
        lease: ChromePhotosDataPlaneLease,
        hostedView: ProtectedSurfaceView,
        currentHost: ChromePhotosProtectedSurfaceHost,
    ): Boolean =
        hostedView.authorityEpoch() == lease.epoch &&
            currentHost.windowId == lease.windowId &&
            viewport == lease.viewport &&
            presentationPolicy.canPresent(lease)

    private fun createAndAttachHost(
        targetWindowId: Int,
        targetViewport: ChromeVisualViewport,
    ): ChromePhotosProtectedSurfaceHostResult {
        val visualContext =
            ChromePhotosProtectedSurfaceHostFactory.visualContext(service)
                ?: return ChromePhotosProtectedSurfaceHostResult.Failed
        val hostedView = ProtectedSurfaceView(visualContext, diagnosticMarkerEnabled)
        val createdHost =
            ChromePhotosProtectedSurfaceHostFactory.create(
                service = service,
                windowId = targetWindowId,
                viewport = targetViewport,
                view = hostedView,
                onPublicationChanged = onHostPublicationChanged,
            )
        if (createdHost == null) {
            hostedView.close()
            return ChromePhotosProtectedSurfaceHostResult.Failed
        }
        view = hostedView
        host = createdHost
        attached = true
        attachmentCount += 1
        attachedWindowId = createdHost.windowId
        hostExtent = createdHost.extent
        viewport = targetViewport
        return createdHost.ensureWindowAndExtent(targetWindowId, targetViewport)
    }

    private fun detachAndReleaseHost() {
        runCatching { host?.close() }
        view?.close()
        view = null
        host = null
        attached = false
        attachedWindowId = null
        hostExtent = 0
        presentationPolicy.reset()
        alphaTracker.reset()
    }

    private fun restoreOpaque(onOpaqueCommitted: (Boolean) -> Unit = {}): Boolean {
        val currentHost = host
        if (!attached || currentHost == null) {
            alphaTracker.reset()
            onOpaqueCommitted(true)
            return true
        }
        val token = alphaTracker.begin(ChromePhotosProtectedSurfaceAlpha.Opaque)
        val submitted =
            currentHost.presentOpaque {
                alphaTracker.commit(token)
                onOpaqueCommitted(true)
            }
        if (!submitted) {
            alphaTracker.submissionFailed(token)
            onOpaqueCommitted(false)
        }
        return submitted
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private class ProtectedSurfaceView(
        context: Context,
        private val diagnosticMarkerEnabled: () -> Boolean,
    ) : View(context), AutoCloseable {
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
            if (diagnosticMarkerEnabled()) drawSurfaceMarkerLattice(canvas)
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

        /**
         * DEV-only compositor marker. The square host is cropped and transformed by Android while
         * its target window rotates, so a marker anchored only at (0, 0) can leave the visible crop
         * even though the protected buffer remains composed. A sparse lattice makes every
         * sufficiently large visible crop carry evidence from this same buffer.
         */
        private fun drawSurfaceMarkerLattice(canvas: Canvas) {
            var x = MarkerInsetPx
            while (x < width) {
                canvas.drawRect(
                    x,
                    0f,
                    (x + ChromePhotosProtectedSurfaceHostPolicy.MarkerLineWidthPx)
                        .coerceAtMost(width.toFloat()),
                    height.toFloat(),
                    SurfaceMarkerPaint,
                )
                x += ChromePhotosProtectedSurfaceHostPolicy.MarkerPitchPx
            }
            var y = MarkerInsetPx
            while (y < height) {
                canvas.drawRect(
                    0f,
                    y,
                    width.toFloat(),
                    (y + ChromePhotosProtectedSurfaceHostPolicy.MarkerLineWidthPx)
                        .coerceAtMost(height.toFloat()),
                    SurfaceMarkerPaint,
                )
                y += ChromePhotosProtectedSurfaceHostPolicy.MarkerPitchPx
            }
        }

        private companion object {
            const val HorizontalPaddingPx = 32f
            const val VerticalPaddingPx = 40f
            const val PendingMessage = "Analizando contenido nuevo…"
            const val NeutralColor = 0xFF202124.toInt()
            const val MarkerInsetPx = 8f
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
}

private fun ChromePhotosProtectedSurfaceHostResult.toCoverResult() =
    when (this) {
        ChromePhotosProtectedSurfaceHostResult.Ready ->
            ChromePhotosProtectedSurfaceCoverResult.Ready
        ChromePhotosProtectedSurfaceHostResult.Pending ->
            ChromePhotosProtectedSurfaceCoverResult.Pending
        ChromePhotosProtectedSurfaceHostResult.Failed ->
            ChromePhotosProtectedSurfaceCoverResult.Failed
    }

internal object ChromePhotosProtectedSurfaceHostPolicy {
    const val MarkerLineWidthPx = 16f
    const val MarkerPitchPx = 128f

    fun requiredExtent(
        viewport: ChromeVisualViewport,
        displayWidth: Int,
        displayHeight: Int,
    ): Int = maxOf(viewport.width, viewport.height, displayWidth, displayHeight)
}

private fun Bitmap?.recycleSafely() {
    if (this != null && !isRecycled) recycle()
}
