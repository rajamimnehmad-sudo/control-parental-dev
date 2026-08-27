package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

internal data class ChromeWindowFrame(
    val bitmap: Bitmap,
    val latencyMillis: Long,
    private val onClosed: (Long) -> Unit = {},
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
    val temporaryBytes: Long get() = width.toLong() * height * BytesPerPixel

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val bytes = temporaryBytes
        bitmap.recycle()
        onClosed(bytes)
    }

    private companion object {
        const val BytesPerPixel = 4L
    }
}

internal sealed interface ChromeWindowCaptureResult {
    data class Captured(val frame: ChromeWindowFrame) : ChromeWindowCaptureResult

    data class Failed(val errorCode: Int) : ChromeWindowCaptureResult
}

internal class ChromeWindowCapture(
    private val service: AccessibilityService,
    private val observer: ChromeVisualShieldFullFrameObserver =
        NoOpChromeVisualShieldFullFrameObserver,
    private val admission: ChromeWindowCaptureAdmission = ChromeWindowCaptureAdmission.Shared,
) {
    // Both Chrome Visual controllers reject events below API 34 before reaching capture().
    @SuppressLint("NewApi")
    suspend fun capture(windowId: Int): ChromeWindowCaptureResult =
        coroutineScope {
            suspendCancellableCoroutine { continuation ->
                val admissionJob =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        val admitted =
                            admission.runWhenAdmitted(windowId) {
                                requestPlatformScreenshot(windowId, continuation)
                            }
                        if (!admitted) {
                            continuation.cancel(CancellationException("window capture superseded"))
                        }
                    }
                continuation.invokeOnCancellation { admissionJob.cancel() }
            }
        }

    @SuppressLint("NewApi")
    private fun requestPlatformScreenshot(
        windowId: Int,
        continuation: CancellableContinuation<ChromeWindowCaptureResult>,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        service.takeScreenshotOfWindow(
            windowId,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val frame =
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            try {
                                wrapped?.copy(Bitmap.Config.ARGB_8888, false)?.let { bitmap ->
                                    val bytes = bitmap.width.toLong() * bitmap.height * BytesPerPixel
                                    observer.onAcquired(bytes)
                                    ChromeWindowFrame(
                                        bitmap = bitmap,
                                        latencyMillis = SystemClock.elapsedRealtime() - startedAt,
                                        onClosed = observer::onClosed,
                                    )
                                }
                            } finally {
                                wrapped?.recycle()
                            }
                        } finally {
                            hardwareBuffer.close()
                        }
                    if (frame == null) observer.onFailure(InvalidBitmapErrorCode)
                    continuation.resumeWithOwnedResource(
                        value =
                            frame?.let(ChromeWindowCaptureResult::Captured)
                                ?: ChromeWindowCaptureResult.Failed(InvalidBitmapErrorCode),
                        resource = frame,
                    )
                }

                override fun onFailure(errorCode: Int) {
                    observer.onFailure(errorCode)
                    continuation.resumeWithOwnedResource(
                        value = ChromeWindowCaptureResult.Failed(errorCode),
                        resource = null,
                    )
                }
            },
        )
    }

    private companion object {
        const val BytesPerPixel = 4L
        const val InvalidBitmapErrorCode = -1
    }
}

/** Transfers [resource] with [value], closing it if cancellation wins before consumption. */
internal fun <T> CancellableContinuation<T>.resumeWithOwnedResource(
    value: T,
    resource: AutoCloseable?,
) {
    if (!isActive) {
        resource?.close()
        return
    }
    resume(
        value = value,
        onCancellation = { _, _, _ -> resource?.close() },
    )
}
