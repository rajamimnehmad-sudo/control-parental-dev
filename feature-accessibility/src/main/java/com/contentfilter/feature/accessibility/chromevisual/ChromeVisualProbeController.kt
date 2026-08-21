package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.feature.accessibility.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEV-only feasibility probe for CHROME-PHOTOS-PROTECTED-SURFACE-00.
 *
 * It deliberately never presents captured Chrome pixels. The single persistent host stays opaque,
 * captures the Chrome window underneath, derives only an in-memory change signature and atomically
 * stages a generated proof frame. This isolates the platform question before photo detection or
 * GloshIA decisions are connected.
 */
internal class ChromeVisualProbeController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val enabled =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            service.resources.getBoolean(R.bool.chrome_photos_protected_surface_probe_enabled)
    private val capture = ChromeWindowCapture(service)
    private val windowInspector = ChromeVisualWindowInspector(service)
    private val state = ChromePhotosProtectedSurfaceState()
    private val surface = ChromePhotosProtectedSurface(service, ::onHostPublicationChanged)
    private val attestationReader = ChromePhotosDataPlaneAttestationReader(service)
    private val leaseAuthority = ChromePhotosDataPlaneLeaseAuthority()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jobLock = Any()
    private var activeJob: Job? = null
    private var pendingTrigger: String? = null
    private var pendingMotion = false
    private var lastArmedEpoch = 0L
    private var activeLease: ChromePhotosDataPlaneLease? = null
    private val leaseWatchdog = Runnable(::verifyLeaseOnMain)

    @Volatile
    private var lastUnderlaySignature: Long? = null

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        val signal =
            ProbeSignal(
                eventType = event.eventType,
                contentChangeTypes = event.contentChangeTypes,
                packageName = event.packageName?.toString().orEmpty(),
                requestedWindowId = event.windowId,
            )
        scope.launch(Dispatchers.Main.immediate) { handleOnMain(signal) }
    }

    override fun close() {
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deactivateOnMain("service_closed")
        } else {
            service.mainExecutor.execute { deactivateOnMain("service_closed") }
        }
    }

    private fun handleOnMain(signal: ProbeSignal) {
        val chromeEvent = windowInspector.isChromePackage(signal.packageName)
        val current = state.snapshot()
        // Never arm Chrome from an unrelated event merely because a stale/background Chrome
        // window is still present behind another app or IME. Non-Chrome events may only preserve
        // an already-armed Chrome session while its exact application window remains inspectable.
        if (!chromeEvent && !current.isActive) return
        val inputMethodTop = windowInspector.inputMethodTop()
        val window =
            when {
                chromeEvent ->
                    windowInspector.find(signal.requestedWindowId.takeIf { it >= 0 } ?: AnyWindowId)
                else ->
                    windowInspector.find(
                        current.windowId.takeIf { it >= 0 } ?: AnyWindowId,
                        allowBehindInputMethod = inputMethodTop != null,
                    )
            }
        if (window == null) {
            deactivateOnMain("chrome_absent")
            return
        }
        val rawViewport =
            windowInspector.viewport(window) ?: run {
                revokeLeaseOnMain("geometry_unavailable")
                log("geometry", window.id, "unavailable")
                return
            }
        val viewport =
            rawViewport.clippedAt(inputMethodTop) ?: run {
                revokeLeaseOnMain("geometry_fully_clipped")
                log("geometry", window.id, "fully_clipped")
                return
            }
        val contextChanged =
            !current.isActive ||
                current.windowId != window.id ||
                current.viewport != viewport
        val eventInvalidates =
            chromeEvent &&
                ChromePhotosProtectedSurfaceEventPolicy.requiresInvalidation(
                    signal.eventType,
                    signal.contentChangeTypes,
                )
        if (!contextChanged && !eventInvalidates) return

        revokeLeaseOnMain(if (contextChanged) "context_changed" else "epoch_invalidated")

        val motion = chromeEvent && signal.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val snapshot =
            if (!current.isActive) {
                state.arm(window.id, viewport)
            } else {
                state.invalidate(window.id, viewport, motion)
            }
        val trigger = ChromePhotosProtectedSurfaceEventPolicy.label(signal.eventType)
        when (
            surface.cover(window.id, viewport, snapshot.epoch) {
                onOpaqueCommitted(snapshot)
            }
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> {
                failSurface(window.id)
                return
            }
            ChromePhotosProtectedSurfaceCoverResult.Pending -> {
                pendingTrigger = trigger
                pendingMotion = motion
                log("surface", window.id, "host_pending")
                return
            }
            ChromePhotosProtectedSurfaceCoverResult.Ready -> Unit
        }
        finishArming(snapshot, trigger, motion)
    }

    private fun onHostPublicationChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            service.mainExecutor.execute(::onHostPublicationChanged)
            return
        }
        val snapshot = state.snapshot()
        val viewport = snapshot.viewport ?: return
        if (!snapshot.isActive) return
        when (
            surface.cover(snapshot.windowId, viewport, snapshot.epoch) {
                onOpaqueCommitted(snapshot)
            }
        ) {
            ChromePhotosProtectedSurfaceCoverResult.Failed -> failSurface(snapshot.windowId)
            ChromePhotosProtectedSurfaceCoverResult.Pending -> Unit
            ChromePhotosProtectedSurfaceCoverResult.Ready ->
                finishArming(
                    snapshot,
                    pendingTrigger ?: HostReadyTrigger,
                    pendingMotion,
                )
        }
    }

    private fun finishArming(
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        trigger: String,
        motion: Boolean,
    ) {
        if (snapshot.epoch == lastArmedEpoch) return
        lastArmedEpoch = snapshot.epoch
        pendingTrigger = null
        pendingMotion = false
        val stats = surface.stats()
        Log.i(
            LogTag,
            "phase=armed windowId=${snapshot.windowId} epoch=${snapshot.epoch} " +
                "trigger=$trigger " +
                "attachmentCount=${stats.attachmentCount} layoutUpdates=${stats.layoutUpdateCount} " +
                "hostMode=window_surface_control hostExtent=${stats.hostExtent} " +
                "rawPresented=false result=success",
        )
        scheduleCapture(
            epoch = snapshot.epoch,
            trigger = trigger,
            motion = motion,
        )
    }

    private fun failSurface(windowId: Int) {
        log("surface", windowId, "attach_or_cover_failed")
        revokeLeaseOnMain("surface_failed")
        state.disarm()
        surface.close()
        pendingTrigger = null
        pendingMotion = false
    }

    private fun scheduleCapture(
        epoch: Long,
        trigger: String,
        motion: Boolean,
    ) {
        synchronized(jobLock) { activeJob?.cancel() }
        var scheduled: Job? = null
        scheduled =
            scope.launch {
                try {
                    delay(if (motion) MotionQuietMillis else ContentQuietMillis)
                    if (!state.markSettling(epoch)) return@launch
                    val token = state.beginCapture(epoch) ?: return@launch
                    captureAndStage(token, trigger)
                } finally {
                    synchronized(jobLock) {
                        if (activeJob === scheduled) activeJob = null
                    }
                }
            }
        synchronized(jobLock) { activeJob = scheduled }
    }

    private suspend fun captureAndStage(
        token: ChromePhotosProtectedSurfaceToken,
        trigger: String,
    ) {
        when (val result = capture.capture(token.windowId)) {
            is ChromeWindowCaptureResult.Failed -> {
                state.fail(token)
                withContext(Dispatchers.Main.immediate) {
                    revokeLeaseOnMain("capture_failed")
                }
                Log.i(
                    LogTag,
                    "phase=capture windowId=${token.windowId} epoch=${token.epoch} " +
                        "errorCode=${result.errorCode} rawPresented=false result=failed",
                )
            }
            is ChromeWindowCaptureResult.Captured ->
                result.frame.use { frame ->
                    val signature = ChromePhotosUnderlaySignature.compute(frame.bitmap)
                    val changed = lastUnderlaySignature?.let { it != signature } ?: true
                    val proofFrame =
                        ChromePhotosProofFrameFactory.create(
                            width = frame.width,
                            height = frame.height,
                            token = token,
                            captureLatencyMillis = frame.latencyMillis,
                            trigger = trigger,
                            underlayChanged = changed,
                        )
                    val commit =
                        withContext(Dispatchers.Main.immediate) {
                            when {
                                !state.markCommitReady(token) -> {
                                    proofFrame.recycle()
                                    SurfaceCommitResult.Stale
                                }
                                !surface.stage(token.viewport, proofFrame, token) -> {
                                    state.fail(token)
                                    SurfaceCommitResult.Failed
                                }
                                !state.markPresented(token) -> {
                                    surface.cover(
                                        token.windowId,
                                        token.viewport,
                                        state.snapshot().epoch,
                                    )
                                    SurfaceCommitResult.Stale
                                }
                                else -> SurfaceCommitResult.Staged
                            }
                        }
                    if (commit == SurfaceCommitResult.Staged) lastUnderlaySignature = signature
                    val stats = withContext(Dispatchers.Main.immediate) { surface.stats() }
                    Log.i(
                        LogTag,
                        "phase=commit windowId=${token.windowId} epoch=${token.epoch} " +
                            "sequence=${token.sequence} captureMs=${frame.latencyMillis} " +
                            "underlayChanged=$changed attachmentCount=${stats.attachmentCount} " +
                            "layoutUpdates=${stats.layoutUpdateCount} surfaceEpoch=${stats.authorityEpoch} " +
                            "pendingEpoch=${stats.pendingEpoch ?: 0L} discardedPending=${stats.discardedPendingFrameCount} " +
                            "rawPresented=false " +
                            "result=${commit.logValue}",
                    )
                }
        }
    }

    private fun deactivateOnMain(reason: String) {
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
        val wasActive = state.snapshot().isActive || surface.stats().attached
        revokeLeaseOnMain(reason)
        state.disarm()
        surface.close()
        lastUnderlaySignature = null
        pendingTrigger = null
        pendingMotion = false
        lastArmedEpoch = 0L
        if (wasActive) Log.i(LogTag, "phase=disarm reason=$reason rawPresented=false result=success")
    }

    private fun onOpaqueCommitted(snapshot: ChromePhotosProtectedSurfaceSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            service.mainExecutor.execute { onOpaqueCommitted(snapshot) }
            return
        }
        val current = state.snapshot()
        val viewport = snapshot.viewport ?: return
        if (
            !current.isActive ||
            current.epoch != snapshot.epoch ||
            current.windowId != snapshot.windowId ||
            current.viewport != viewport
        ) {
            return
        }
        val leaseContext = snapshot.toLeaseContext(viewport)
        val attestation = attestationReader.read()
        val lease = leaseAuthority.mint(attestation, leaseContext)
        if (lease == null || !leaseAuthority.isValid(lease, attestationReader.read(), leaseContext)) {
            surface.revokeTransparency()
            logLease("denied", snapshot, "attestation_invalid")
            return
        }
        if (!surface.presentTransparent(lease)) {
            leaseAuthority.revoke()
            surface.revokeTransparency()
            logLease("denied", snapshot, "surface_rejected")
            return
        }
        activeLease = lease
        scheduleLeaseWatchdog()
        logLease("granted", snapshot, "healthy")
    }

    private fun verifyLeaseOnMain() {
        val lease = activeLease ?: return
        val snapshot = state.snapshot()
        val viewport = snapshot.viewport
        if (viewport == null) {
            revokeLeaseOnMain("context_absent")
            return
        }
        val context = snapshot.toLeaseContext(viewport)
        val attestation = attestationReader.read()
        if (!leaseAuthority.isValid(lease, attestation, context)) {
            revokeLeaseOnMain("expired_or_unhealthy")
            return
        }
        if (lease.validUntilElapsed - android.os.SystemClock.elapsedRealtime() <= LeaseRenewalLeadMillis) {
            val renewed = leaseAuthority.mint(attestation, context)
            if (renewed == null) {
                revokeLeaseOnMain("renewal_denied")
                return
            }
            activeLease = renewed
        }
        scheduleLeaseWatchdog()
    }

    private fun scheduleLeaseWatchdog() {
        mainHandler.removeCallbacks(leaseWatchdog)
        mainHandler.postDelayed(leaseWatchdog, LeaseWatchdogMillis)
    }

    private fun revokeLeaseOnMain(reason: String) {
        mainHandler.removeCallbacks(leaseWatchdog)
        val lease = activeLease
        val wasTransparent = surface.stats().transparent
        activeLease = null
        leaseAuthority.revoke()
        surface.revokeTransparency()
        if (lease != null || wasTransparent) {
            val snapshot = state.snapshot()
            logLease("revoked", snapshot, reason)
        }
    }

    private fun ChromePhotosProtectedSurfaceSnapshot.toLeaseContext(currentViewport: ChromeVisualViewport) =
        ChromePhotosDataPlaneLeaseContext(
            packageName = ChromePackageName,
            windowId = windowId,
            epoch = epoch,
            viewport = currentViewport,
        )

    private fun logLease(
        action: String,
        snapshot: ChromePhotosProtectedSurfaceSnapshot,
        reason: String,
    ) {
        val stats = surface.stats()
        Log.i(
            LogTag,
            "phase=data_plane_lease action=$action reason=$reason windowId=${snapshot.windowId} " +
                "epoch=${snapshot.epoch} transparent=${stats.transparent} " +
                "attachmentCount=${stats.attachmentCount} rawPresented=false",
        )
    }

    private fun log(
        phase: String,
        windowId: Int,
        result: String,
    ) {
        Log.i(LogTag, "phase=$phase windowId=$windowId rawPresented=false result=$result")
    }

    private fun ChromeVisualViewport.clippedAt(maximumBottom: Int?): ChromeVisualViewport? {
        if (maximumBottom == null || maximumBottom >= bottom) return this
        return copy(bottom = maximumBottom.coerceAtLeast(top)).takeIf { it.height > 0 }
    }

    private enum class SurfaceCommitResult(
        val logValue: String,
    ) {
        Staged("staged"),
        Stale("stale"),
        Failed("stage_failed"),
    }

    private data class ProbeSignal(
        val eventType: Int,
        val contentChangeTypes: Int,
        val packageName: String,
        val requestedWindowId: Int,
    )

    private companion object {
        const val AnyWindowId = -1
        const val MotionQuietMillis = 180L
        const val ContentQuietMillis = 90L
        const val HostReadyTrigger = "HOST_READY"
        const val ChromePackageName = "com.android.chrome"
        const val LeaseWatchdogMillis = 50L
        const val LeaseRenewalLeadMillis = 150L
        const val LogTag = "ChromePhotosSurfaceProbe"
    }
}

internal object ChromePhotosProtectedSurfaceEventPolicy {
    fun requiresInvalidation(
        eventType: Int,
        contentChangeTypes: Int,
    ): Boolean =
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED ||
                    contentChangeTypes and VisualContentChangeMask != 0
            else -> false
        }

    fun label(eventType: Int): String =
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            else -> "TYPE_$eventType"
        }

    private val VisualContentChangeMask =
        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
}

private object ChromePhotosUnderlaySignature {
    fun compute(bitmap: Bitmap): Long {
        var hash = FnvOffsetBasis
        repeat(SampleRows) { row ->
            val y = (((row + 0.5) * bitmap.height) / SampleRows).toInt().coerceIn(0, bitmap.height - 1)
            repeat(SampleColumns) { column ->
                val x = (((column + 0.5) * bitmap.width) / SampleColumns).toInt().coerceIn(0, bitmap.width - 1)
                hash = (hash xor bitmap.getPixel(x, y).toLong()) * FnvPrime
            }
        }
        return hash
    }

    private const val SampleColumns = 24
    private const val SampleRows = 16
    private const val FnvOffsetBasis = -3750763034362895579L
    private const val FnvPrime = 1099511628211L
}

private object ChromePhotosProofFrameFactory {
    fun create(
        width: Int,
        height: Int,
        token: ChromePhotosProtectedSurfaceToken,
        captureLatencyMillis: Long,
        trigger: String,
        underlayChanged: Boolean,
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(BackgroundColor)
        drawPattern(canvas, width, height, token.sequence)

        val density = width.coerceAtLeast(1) / 1_080f
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 44f * density
                typeface = Typeface.DEFAULT_BOLD
            }
        val bodyPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BodyTextColor
                textSize = 28f * density
                typeface = Typeface.DEFAULT
            }
        val left = 48f * density
        var baseline = 120f * density
        canvas.drawText("Chrome Photos — superficie protegida", left, baseline, titlePaint)
        baseline += 72f * density
        listOf(
            "Captura bajo el overlay: OK",
            "Generación ${token.epoch} · cuadro ${token.sequence}",
            "Ventana $width×$height · captura $captureLatencyMillis ms",
            "Disparador: $trigger",
            "Contenido debajo cambió: ${if (underlayChanged) "sí" else "no"}",
            "Píxeles crudos de Chrome presentados: NO",
            "Deslizá la página: este host debe seguir siendo el mismo.",
        ).forEach { line ->
            canvas.drawText(line, left, baseline, bodyPaint)
            baseline += 50f * density
        }
        return output
    }

    private fun drawPattern(
        canvas: Canvas,
        width: Int,
        height: Int,
        sequence: Long,
    ) {
        val paint = Paint()
        val bandHeight = (height / PatternBands).coerceAtLeast(1)
        repeat(PatternBands) { index ->
            val value = ((sequence + index * 37L) and 0x3F).toInt()
            paint.color = Color.rgb(32 + value / 3, 48 + value / 2, 64 + value)
            val top = index * bandHeight
            val bottom = if (index == PatternBands - 1) height else top + bandHeight
            canvas.drawRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat(), paint)
        }
    }

    private const val PatternBands = 6
    private const val BackgroundColor = 0xFF202124.toInt()
    private const val BodyTextColor = 0xFFD7DEE8.toInt()
}
