package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualShieldMetricsSnapshot(
    val fullFrameAcquired: Long,
    val fullFrameClosed: Long,
    val fullFrameOutstanding: Long,
    val fullFramePeakBytes: Long,
    val cropCreated: Long,
    val cropClosed: Long,
    val cropOutstanding: Long,
    val staleDropped: Long,
    val captureCancelled: Long,
    val secureWindowFailures: Long,
)

internal class ChromeVisualShieldMetrics {
    private var fullFrameAcquired = 0L
    private var fullFrameClosed = 0L
    private var fullFrameOutstanding = 0L
    private var fullFrameBytesOutstanding = 0L
    private var fullFramePeakBytes = 0L
    private var cropCreated = 0L
    private var cropClosed = 0L
    private var cropOutstanding = 0L
    private var staleDropped = 0L
    private var captureCancelled = 0L
    private var secureWindowFailures = 0L

    @Synchronized
    fun onFullFrameAcquired(bytes: Long) {
        fullFrameAcquired += 1
        fullFrameOutstanding += 1
        fullFrameBytesOutstanding += bytes
        fullFramePeakBytes = maxOf(fullFramePeakBytes, fullFrameBytesOutstanding)
    }

    @Synchronized
    fun onFullFrameClosed(bytes: Long) {
        check(fullFrameOutstanding > 0) { "Visual Shield full frame closed without acquisition" }
        check(fullFrameBytesOutstanding >= bytes) { "Visual Shield full frame bytes underflow" }
        fullFrameClosed += 1
        fullFrameOutstanding -= 1
        fullFrameBytesOutstanding -= bytes
    }

    @Synchronized
    fun onCropCreated() {
        cropCreated += 1
        cropOutstanding += 1
    }

    @Synchronized
    fun onCropClosed() {
        check(cropOutstanding > 0) { "Visual Shield crop closed without creation" }
        cropClosed += 1
        cropOutstanding -= 1
    }

    @Synchronized
    fun onStaleDropped() {
        staleDropped += 1
    }

    @Synchronized
    fun onCaptureCancelled() {
        captureCancelled += 1
    }

    @Synchronized
    fun onSecureWindowFailure() {
        secureWindowFailures += 1
    }

    @Synchronized
    fun snapshot(): ChromeVisualShieldMetricsSnapshot =
        ChromeVisualShieldMetricsSnapshot(
            fullFrameAcquired = fullFrameAcquired,
            fullFrameClosed = fullFrameClosed,
            fullFrameOutstanding = fullFrameOutstanding,
            fullFramePeakBytes = fullFramePeakBytes,
            cropCreated = cropCreated,
            cropClosed = cropClosed,
            cropOutstanding = cropOutstanding,
            staleDropped = staleDropped,
            captureCancelled = captureCancelled,
            secureWindowFailures = secureWindowFailures,
        )
}

internal interface ChromeVisualShieldFullFrameObserver {
    fun onAcquired(bytes: Long)

    fun onClosed(bytes: Long)

    fun onFailure(errorCode: Int)
}

internal class ChromeVisualShieldCaptureObserver(
    private val metrics: ChromeVisualShieldMetrics,
) : ChromeVisualShieldFullFrameObserver {
    override fun onAcquired(bytes: Long) = metrics.onFullFrameAcquired(bytes)

    override fun onClosed(bytes: Long) = metrics.onFullFrameClosed(bytes)

    override fun onFailure(errorCode: Int) {
        if (errorCode == SecureWindowErrorCode) metrics.onSecureWindowFailure()
    }

    private companion object {
        const val SecureWindowErrorCode = 6
    }
}

internal object NoOpChromeVisualShieldFullFrameObserver : ChromeVisualShieldFullFrameObserver {
    override fun onAcquired(bytes: Long) = Unit

    override fun onClosed(bytes: Long) = Unit

    override fun onFailure(errorCode: Int) = Unit
}
