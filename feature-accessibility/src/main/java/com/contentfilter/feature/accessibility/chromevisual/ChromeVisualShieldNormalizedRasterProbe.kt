package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualDecision

/**
 * Diagnostic-only view of the exact captured crop. The sole geometric operation is one direct,
 * non-uniform resize to the R3.1 input size. prepareCapturedRaster then performs only canonical RGB
 * conversion, so the prepared raster cannot be letterboxed or cropped a second time.
 */
internal class ChromeVisualShieldNormalizedRaster private constructor(
    val preparedImage: GloshiaPreparedImage,
) : AutoCloseable {
    override fun close() {
        preparedImage.rgb888.fill(0)
    }

    companion object {
        fun prepare(bitmap: Bitmap): ChromeVisualShieldNormalizedRaster? {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            val resized =
                runCatching {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        GloshiaImageContract.TargetSize,
                        GloshiaImageContract.TargetSize,
                        true,
                    )
                }.getOrNull() ?: return null
            return try {
                AndroidGloshiaImagePreprocessor.prepareCapturedRaster(resized)?.let {
                    ChromeVisualShieldNormalizedRaster(it)
                }
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        }
    }
}

/** Applies the real R3.1 policy without allowing it to synthesize a second geometric view. */
internal object ChromeVisualShieldNormalizedRasterPolicyProbe {
    fun decide(
        candidateId: String,
        preparedImage: GloshiaPreparedImage,
        analyzer: GloshiaVisualAnalyzer,
        canContinue: () -> Boolean,
        onModelResult: (GloshiaVisualAnalysisResult) -> Unit = {},
    ): GloshiaVisualDecision {
        var cachedResult: GloshiaVisualAnalysisResult? = null
        return GloshiaPreparedRasterPolicy.decide(
            candidateId = candidateId,
            // Repeating the same immutable prepared raster prevents the policy's uncertain branch
            // from generating quadrant geometry. The analyzer result is cached, so R3.1 runs once.
            preparedImages = listOf(preparedImage, preparedImage),
            analyzer = analyzer,
            canContinue = canContinue,
            analyze = { currentAnalyzer, candidate ->
                check(candidate === preparedImage) { "normalized probe introduced a second raster" }
                cachedResult
                    ?: currentAnalyzer.analyze(candidate).also {
                        cachedResult = it
                        onModelResult(it)
                    }
            },
        )
    }
}
