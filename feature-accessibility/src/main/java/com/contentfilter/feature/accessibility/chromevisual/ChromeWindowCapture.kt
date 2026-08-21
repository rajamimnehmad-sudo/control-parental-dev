package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal data class ChromeWindowFrame(
    val bitmap: Bitmap,
    val latencyMillis: Long,
) : AutoCloseable {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
    val temporaryBytes: Long get() = width.toLong() * height * BytesPerPixel

    override fun close() = bitmap.recycle()

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
) {
    // Both Chrome Visual controllers reject events below API 34 before reaching capture().
    @SuppressLint("NewApi")
    suspend fun capture(windowId: Int): ChromeWindowCaptureResult =
        suspendCancellableCoroutine { continuation ->
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
                                        ChromeWindowFrame(
                                            bitmap = bitmap,
                                            latencyMillis = SystemClock.elapsedRealtime() - startedAt,
                                        )
                                    }
                                } finally {
                                    wrapped?.recycle()
                                }
                            } finally {
                                hardwareBuffer.close()
                            }
                        if (continuation.isActive) {
                            continuation.resume(
                                frame?.let(ChromeWindowCaptureResult::Captured)
                                    ?: ChromeWindowCaptureResult.Failed(InvalidBitmapErrorCode),
                            )
                        } else {
                            frame?.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resume(ChromeWindowCaptureResult.Failed(errorCode))
                        }
                    }
                },
            )
        }

    private companion object {
        const val InvalidBitmapErrorCode = -1
    }
}
