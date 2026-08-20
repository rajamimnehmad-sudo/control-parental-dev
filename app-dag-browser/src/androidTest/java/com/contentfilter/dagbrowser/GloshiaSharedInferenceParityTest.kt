package com.contentfilter.dagbrowser

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import androidx.test.core.app.ApplicationProvider
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualModelInfo
import com.glosh.visual.OnDeviceGloshiaVisualAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.abs

class GloshiaSharedInferenceParityTest {
    @Test
    fun sharedEngineMatchesFrozenDagInferenceOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image =
            GloshiaPreparedImage(
                width = 224,
                height = 224,
                rgb888 = ByteArray(224 * 224 * 3) { index -> (index * 37 % 256).toByte() },
            )
        val shared = OnDeviceGloshiaVisualAnalyzer.create(context)
        try {
            val sharedResult = shared.analyze(image)
            assertTrue(sharedResult is GloshiaVisualAnalysisResult.Classified)
            val sharedProbability =
                (sharedResult as GloshiaVisualAnalysisResult.Classified).filterProbability
            val legacyProbability = legacyDagProbability(context, image.rgb888)

            assertTrue(abs(sharedProbability - legacyProbability) <= 0.000001f)
            assertEquals(legacyProbability >= 0.4f, sharedProbability >= 0.4f)
        } finally {
            (shared as? Closeable)?.close()
            image.rgb888.fill(0)
        }
    }

    private fun legacyDagProbability(
        context: android.content.Context,
        rgb: ByteArray,
    ): Float {
        val normalized = legacyNormalizeNchw(rgb)
        val environment = OrtEnvironment.getEnvironment()
        val model = context.assets.open(GloshiaVisualModelInfo.ModelAssetPath).use { it.readBytes() }
        return try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                environment.createSession(model, options).use { session ->
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(normalized),
                        longArrayOf(1, 3, 224, 224),
                    ).use { tensor ->
                        session.run(mapOf("pixel_values" to tensor)).use { output ->
                            val rows = output[0].value as Array<*>
                            (rows.first() as FloatArray).first()
                        }
                    }
                }
            }
        } finally {
            normalized.fill(0f)
            model.fill(0)
        }
    }

    private fun legacyNormalizeNchw(rgb: ByteArray): FloatArray {
        val output = FloatArray(224 * 224 * 3)
        val means = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val deviations = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        val pixelCount = 224 * 224
        for (pixelIndex in 0 until pixelCount) {
            val sourceIndex = pixelIndex * 3
            for (channel in 0 until 3) {
                val value = (rgb[sourceIndex + channel].toInt() and 0xFF) / 255f
                output[channel * pixelCount + pixelIndex] =
                    (value - means[channel]) / deviations[channel]
            }
        }
        return output
    }
}
