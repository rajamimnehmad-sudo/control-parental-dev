package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal data class DagRegionalRisk(
    val cropPlan: DagImageCropPlan,
    val probability: Float,
)

/**
 * Experimental policy for the isolated lab APK only. It never changes the model score or the
 * normal fail-closed policy. A single moderate regional signal may be covered; ambiguity falls
 * back to a full placeholder.
 */
internal object DagPartialRedactionPolicy {
    const val StrongFrostedReason = "model_partial_redaction"

    fun select(
        fullProbability: Float,
        regionalRisks: List<DagRegionalRisk>,
    ): List<DagImageCropPlan>? {
        if (fullProbability >= FullImageBlockThreshold) return null
        val strong = regionalRisks.filter { it.probability >= RegionalRedactionThreshold }
        if (strong.size != 1) return null
        if (strong.any { it.probability >= FullImageBlockThreshold }) return null
        return strong.map(DagRegionalRisk::cropPlan)
    }

    private const val FullImageBlockThreshold = 0.72f
    private const val RegionalRedactionThreshold = 0.60f
}

/** Creates a deliberately strong, low-detail frosted-glass replacement for a lab-only preview. */
internal object DagStrongFrostedRedaction {
    fun renderBase64(
        bytes: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        cropPlans: List<DagImageCropPlan>,
    ): String? {
        if (bytes.isEmpty() || cropPlans.isEmpty()) return null
        val decodeSize =
            DagRegionalCropPlanner.decodeSize(
                sourceWidth,
                sourceHeight,
                allowStandardAspect = BuildConfig.GLOSHIA_LAB_FIXTURE,
            ) ?: return null
        var decoded: Bitmap? = null
        var output: Bitmap? = null
        return try {
            val options =
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sampleSize(sourceWidth, sourceHeight, decodeSize)
                }
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            decoded = source
            if (source.width != decodeSize.first || source.height != decodeSize.second) {
                val resized =
                    Bitmap.createScaledBitmap(source, decodeSize.first, decodeSize.second, true)
                source.recycle()
                decoded = resized
            }
            val decodedBitmap = checkNotNull(decoded)
            val outputBitmap = decodedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            output = outputBitmap
            val canvas = Canvas(outputBitmap)
            val frost =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(190, 214, 224, 232)
                }
            cropPlans.forEach { plan ->
                val rect =
                    Rect(
                        plan.left.coerceIn(0, outputBitmap.width - 1),
                        plan.top.coerceIn(0, outputBitmap.height - 1),
                        (plan.left + plan.width).coerceIn(1, outputBitmap.width),
                        (plan.top + plan.height).coerceIn(1, outputBitmap.height),
                    )
                if (rect.width() <= 0 || rect.height() <= 0) return@forEach
                val crop =
                    Bitmap.createBitmap(outputBitmap, rect.left, rect.top, rect.width(), rect.height())
                val miniature =
                    Bitmap.createScaledBitmap(
                        crop,
                        max(1, rect.width() / 18),
                        max(1, rect.height() / 18),
                        true,
                    )
                val blurred =
                    Bitmap.createScaledBitmap(miniature, rect.width(), rect.height(), false)
                canvas.drawBitmap(blurred, rect.left.toFloat(), rect.top.toFloat(), null)
                canvas.drawRect(rect, frost)
                blurred.recycle()
                miniature.recycle()
                crop.recycle()
            }
            ByteArrayOutputStream().use { stream ->
                if (!outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) return null
                val encoded = stream.toByteArray()
                if (encoded.size > MaxReplacementBytes) return null
                Base64.encodeToString(encoded, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        } finally {
            if (output !== decoded) output?.recycle()
            decoded?.recycle()
        }
    }

    private const val MaxReplacementBytes = 256 * 1024

    private fun sampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetSize: Pair<Int, Int>,
    ): Int {
        val sourceLongEdge = max(sourceWidth, sourceHeight)
        val targetLongEdge = max(targetSize.first, targetSize.second)
        var sample = 1
        while (sourceLongEdge / (sample * 2) >= targetLongEdge) sample *= 2
        return sample
    }
}
