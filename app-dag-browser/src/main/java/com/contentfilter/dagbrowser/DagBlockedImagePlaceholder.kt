package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

internal data class DagPlaceholderSize(
    val width: Int,
    val height: Int,
)

internal object DagPlaceholderSizePlanner {
    fun plan(
        sourceWidth: Int?,
        sourceHeight: Int?,
        maxEdge: Int = MaxEdge,
    ): DagPlaceholderSize {
        if (sourceWidth == null || sourceHeight == null || sourceWidth <= 0 || sourceHeight <= 0) {
            return DagPlaceholderSize(FallbackEdge, FallbackEdge)
        }
        val scale = minOf(1.0, maxEdge.toDouble() / max(sourceWidth, sourceHeight).toDouble())
        return DagPlaceholderSize(
            width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
            height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private const val MaxEdge = 320
    private const val FallbackEdge = 64
}

internal object DagBlockedImagePlaceholder {
    fun renderBase64(
        sourceWidth: Int?,
        sourceHeight: Int?,
    ): String? =
        runCatching {
            val size = DagPlaceholderSizePlanner.plan(sourceWidth, sourceHeight)
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                val background =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader =
                            LinearGradient(
                                0f,
                                0f,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                intArrayOf(
                                    Color.rgb(225, 233, 237),
                                    Color.rgb(188, 203, 211),
                                    Color.rgb(235, 240, 242),
                                ),
                                null,
                                Shader.TileMode.CLAMP,
                            )
                    }
                canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), background)
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    Base64.getEncoder().encodeToString(output.toByteArray())
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
}
