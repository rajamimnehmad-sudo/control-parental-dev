package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.contentfilter.feature.accessibility.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class ChromeVisualProbeController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val enabled = service.resources.getBoolean(R.bool.chrome_visual_probe_enabled)
    private val stateLock = Any()
    private var activeJob: Job? = null
    private var lastWindowId = InvalidWindowId
    private var lastCompletedAtMillis = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (event.packageName?.toString() != ChromePackageName) return
        val requestedWindowId = event.windowId.takeIf { it != InvalidWindowId } ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(stateLock) {
            if (activeJob?.isActive == true) return
            if (requestedWindowId == lastWindowId && now - lastCompletedAtMillis < ProbeCooldownMillis) return
            activeJob = scope.launch { runProbe(requestedWindowId) }
        }
    }

    override fun close() {
        synchronized(stateLock) {
            activeJob?.cancel()
            activeJob = null
        }
    }

    private suspend fun runProbe(requestedWindowId: Int) {
        var before: ProbePixelSample? = null
        var after: ProbePixelSample? = null
        var overlay: ChromeVisualProbeOverlay? = null
        var completedWindowId = requestedWindowId
        try {
            val window = withContext(Dispatchers.Main.immediate) { findChromeWindow(requestedWindowId) }
            if (window == null) {
                logResult(requestedWindowId, "window", "not_found")
                return
            }
            completedWindowId = window.id
            val baseline = capture(window.id, "baseline", null, shouldSample = true) ?: return
            before = baseline.sample
            val region =
                ProbeOverlayRegion.centeredRight(
                    windowWidth = baseline.width,
                    windowHeight = baseline.height,
                    density = service.resources.displayMetrics.density,
                )
            overlay =
                withContext(Dispatchers.Main.immediate) {
                    ChromeVisualProbeOverlay.attach(
                        service = service,
                        windowId = window.id,
                        windowWidth = baseline.width,
                        windowHeight = baseline.height,
                        region = region,
                    )
                }
            if (overlay == null) {
                logResult(window.id, "overlay", "attach_failed")
                return
            }
            delay(ScreenshotIntervalMillis)
            val covered = capture(window.id, "overlay", region.asRect(), shouldSample = true) ?: return
            after = covered.sample
            val beforeSample = before ?: return
            val afterSample = after ?: return
            val decision = ChromeVisualProbeGate.decide(beforeSample, afterSample)
            Log.i(
                LogTag,
                "phase=underlay windowId=${window.id} width=${covered.width} height=${covered.height} " +
                    "result=${if (decision.passed) "pass" else "fail"}",
            )
            withContext(Dispatchers.Main.immediate) {
                overlay.close()
                overlay = null
            }
            measureStableFrequency(window.id)
        } catch (error: Throwable) {
            if (error !is kotlinx.coroutines.CancellationException) {
                logResult(completedWindowId, "probe", "error_${error.javaClass.simpleName}")
            }
        } finally {
            ChromeVisualProbeGate.clear(before)
            ChromeVisualProbeGate.clear(after)
            withContext(NonCancellable + Dispatchers.Main.immediate) { overlay?.close() }
            synchronized(stateLock) {
                lastWindowId = completedWindowId
                lastCompletedAtMillis = SystemClock.elapsedRealtime()
                activeJob = null
            }
        }
    }

    private fun findChromeWindow(requestedWindowId: Int): AccessibilityWindowInfo? {
        val candidates =
            service.windows.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == ChromePackageName
            }
        return candidates.firstOrNull { it.id == requestedWindowId }
            ?: candidates.firstOrNull { it.isActive }
            ?: candidates.firstOrNull { it.isFocused }
    }

    private suspend fun capture(
        windowId: Int,
        phase: String,
        sampleRect: Rect?,
        shouldSample: Boolean,
    ): ProbeCapture? =
        suspendCancellableCoroutine { continuation ->
            val startedAt = SystemClock.elapsedRealtime()
            service.takeScreenshotOfWindow(
                windowId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val latencyMillis = SystemClock.elapsedRealtime() - startedAt
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val width = hardwareBuffer.width
                        val height = hardwareBuffer.height
                        var sample: ProbePixelSample? = null
                        runCatching {
                            if (shouldSample) {
                                val resolvedRect =
                                    sampleRect
                                        ?: ProbeOverlayRegion.centeredRight(
                                            windowWidth = width,
                                            windowHeight = height,
                                            density = service.resources.displayMetrics.density,
                                        ).asRect()
                                val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                try {
                                    val software = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                    try {
                                        if (software != null) sample = sample(software, resolvedRect)
                                    } finally {
                                        software?.recycle()
                                    }
                                } finally {
                                    wrapped?.recycle()
                                }
                            }
                        }.also {
                            hardwareBuffer.close()
                        }
                        val successful = !shouldSample || sample != null
                        Log.i(
                            LogTag,
                            "phase=$phase windowId=$windowId width=$width height=$height " +
                                "latencyMs=$latencyMillis " +
                                "temporaryBytes=${width.toLong() * height * BytesPerPixel * if (shouldSample) 2 else 1} " +
                                "result=${if (successful) "success" else "sample_failed"}",
                        )
                        if (continuation.isActive) {
                            continuation.resume(ProbeCapture(width, height, sample))
                        } else {
                            ChromeVisualProbeGate.clear(sample)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val latencyMillis = SystemClock.elapsedRealtime() - startedAt
                        Log.i(
                            LogTag,
                            "phase=$phase windowId=$windowId width=0 height=0 latencyMs=$latencyMillis " +
                                "temporaryBytes=0 result=failure_$errorCode",
                        )
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    private suspend fun measureStableFrequency(windowId: Int) {
        val startedAt = SystemClock.elapsedRealtime()
        var successes = 0
        repeat(FrequencySampleCount) { index ->
            delay(ScreenshotIntervalMillis)
            val capture = capture(windowId, "frequency_${index + 1}", null, shouldSample = false)
            if (capture != null) successes++
            ChromeVisualProbeGate.clear(capture?.sample)
        }
        val elapsedMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
        val frequencyHz = successes * 1_000.0 / elapsedMillis
        Log.i(
            LogTag,
            "phase=frequency windowId=$windowId width=0 height=0 latencyMs=$elapsedMillis " +
                "result=${if (successes == FrequencySampleCount) "success" else "partial"} " +
                "successfulCaptures=$successes requestedCaptures=$FrequencySampleCount frequencyHz=$frequencyHz",
        )
    }

    private fun sample(
        bitmap: Bitmap,
        requestedRect: Rect,
    ): ProbePixelSample? {
        val rect =
            Rect(
                requestedRect.left.coerceIn(0, bitmap.width),
                requestedRect.top.coerceIn(0, bitmap.height),
                requestedRect.right.coerceIn(0, bitmap.width),
                requestedRect.bottom.coerceIn(0, bitmap.height),
            )
        if (rect.width() <= 0 || rect.height() <= 0) return null
        val columns = SampleColumns.coerceAtMost(rect.width())
        val rows = SampleRows.coerceAtMost(rect.height())
        val colors = IntArray(columns * rows)
        var outputIndex = 0
        repeat(rows) { row ->
            val y = rect.top + ((row + 0.5) * rect.height() / rows).toInt().coerceAtMost(rect.height() - 1)
            repeat(columns) { column ->
                val x = rect.left + ((column + 0.5) * rect.width() / columns).toInt().coerceAtMost(rect.width() - 1)
                colors[outputIndex++] = bitmap.getPixel(x, y)
            }
        }
        return ProbePixelSample(columns, rows, colors)
    }

    private fun logResult(
        windowId: Int,
        phase: String,
        result: String,
    ) {
        Log.i(LogTag, "phase=$phase windowId=$windowId width=0 height=0 latencyMs=0 result=$result")
    }

    private data class ProbeCapture(
        val width: Int,
        val height: Int,
        val sample: ProbePixelSample?,
    )

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val InvalidWindowId = -1
        const val ProbeCooldownMillis = 10_000L
        const val ScreenshotIntervalMillis = 450L
        const val FrequencySampleCount = 3
        const val BytesPerPixel = 4L
        const val SampleColumns = 16
        const val SampleRows = 12
        const val LogTag = "ChromeVisualProbe"
    }
}
