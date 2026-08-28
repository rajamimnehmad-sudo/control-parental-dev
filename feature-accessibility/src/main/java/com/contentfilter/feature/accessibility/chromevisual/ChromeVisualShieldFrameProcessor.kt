package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean

internal class ChromeVisualShieldCrop(
    val bitmap: Bitmap,
    private val onClosed: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        bitmap.recycle()
        onClosed()
    }
}

internal class ChromeVisualShieldFrameProcessor(
    private val metrics: ChromeVisualShieldMetrics,
) {
    /** The returned crop is RAM-only. The caller owns and must close it. */
    fun crop(
        frame: ChromeWindowFrame,
        identity: ChromeVisualShieldIdentity,
        region: ChromeVisualRegion = identity.region,
        navigationInsets: ChromeVisualShieldNavigationInsets = ChromeVisualShieldNavigationInsets.Zero,
    ): ChromeVisualShieldCrop? {
        val frameRegion =
            ChromeVisualShieldScreenshotGeometryMapper.toFrame(
                region,
                identity.viewport,
                frame.width,
                frame.height,
                navigationInsets,
            ) ?: return null
        val bitmap =
            Bitmap.createBitmap(
                frame.bitmap,
                frameRegion.left,
                frameRegion.top,
                frameRegion.width,
                frameRegion.height,
            )
        metrics.onCropCreated()
        return ChromeVisualShieldCrop(bitmap, metrics::onCropClosed)
    }
}

/** Pure ownership ordering used by unit tests and by the Android capture route. */
internal object ChromeVisualShieldOwnershipPipeline {
    inline fun <F : AutoCloseable, C : AutoCloseable, R> deriveAndProcess(
        fullFrame: F,
        derive: (F) -> C?,
        process: (C) -> R,
    ): R? {
        val crop =
            try {
                derive(fullFrame)
            } finally {
                fullFrame.close()
            } ?: return null
        return crop.use(process)
    }
}

/** Owns every capture resource across cancellation points; close is idempotent. */
internal class ChromeVisualShieldCaptureResources<F : AutoCloseable, C : AutoCloseable> :
    AutoCloseable {
    private var fullFrame: F? = null
    private var crop: C? = null

    fun attachFullFrame(value: F) {
        check(fullFrame == null && crop == null)
        fullFrame = value
    }

    fun deriveCrop(transform: (F) -> C?): C? {
        val ownedFullFrame = checkNotNull(fullFrame)
        val derived =
            try {
                transform(ownedFullFrame)
            } finally {
                ownedFullFrame.close()
                fullFrame = null
            }
        crop = derived
        return derived
    }

    suspend fun <R> processCrop(block: suspend (C) -> R): R {
        val ownedCrop = checkNotNull(crop)
        return try {
            block(ownedCrop)
        } finally {
            ownedCrop.close()
            crop = null
        }
    }

    override fun close() {
        crop?.close()
        crop = null
        fullFrame?.close()
        fullFrame = null
    }
}
