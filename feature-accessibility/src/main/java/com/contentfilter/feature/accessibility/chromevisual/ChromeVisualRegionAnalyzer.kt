package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import com.contentfilter.feature.accessibility.ChromeVisualGloshiaEngineProvider
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import com.glosh.visual.LifecycleGloshiaVisualAnalyzer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

internal class ChromeVisualRegionAnalyzer(
    private val service: AccessibilityService,
) : AutoCloseable {
    private val lock = Any()
    private val inferenceMutex = Mutex()
    private var analyzer: GloshiaVisualAnalyzer? = null

    suspend fun analyze(
        source: Bitmap,
        region: ChromeVisualRegion,
    ): GloshiaVisualDecision =
        inferenceMutex.withLock {
            val crop =
                runCatching {
                    Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
                }.getOrNull() ?: return@withLock unavailable(region.id, GloshiaVisualPolicyContract.DecodeFailedReason)
            val prepared =
                try {
                    AndroidGloshiaImagePreprocessor.prepareVideoCapturedRaster(
                        crop,
                        maxOf(crop.width, crop.height),
                    )
                } finally {
                    crop.recycle()
                } ?: return@withLock unavailable(region.id, GloshiaVisualPolicyContract.DecodeFailedReason)
            return try {
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = region.id,
                    preparedImages = listOf(prepared),
                    analyzer =
                        engine() ?: return@withLock unavailable(
                            region.id,
                            GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
                        ),
                )
            } finally {
                prepared.rgb888.fill(0)
            }
        }

    override fun close() {
        synchronized(lock) {
            (analyzer as? Closeable)?.close()
            analyzer = null
        }
    }

    private fun engine(): GloshiaVisualAnalyzer? =
        synchronized(lock) {
            analyzer
                ?: ChromeVisualGloshiaEngineProvider.create(service)?.let {
                    LifecycleGloshiaVisualAnalyzer(it).also { created -> analyzer = created }
                }
        }

    private fun unavailable(
        candidateId: String,
        reason: String,
    ) = GloshiaVisualDecision(
        candidateId = candidateId,
        action = GloshiaVisualAction.Block,
        reason = reason,
    )
}
