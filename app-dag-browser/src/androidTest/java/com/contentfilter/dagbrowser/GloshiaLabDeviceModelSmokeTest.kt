package com.contentfilter.dagbrowser

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Isolated device-only model smoke test. It does not open the browser UI, persist media, or touch
 * the production DEV package. The tensor is the exact 224x224 RGB contract consumed by R3.1.
 */
@RunWith(AndroidJUnit4::class)
class GloshiaLabDeviceModelSmokeTest {
    @Test
    fun r3ModelExecutesRepeatedSyntheticTensors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val modelBytes = context.assets.open(DagVisualModelInfo.ModelAssetPath).use { it.readBytes() }
        val modelSha256 = modelBytes.sha256()
        assertEquals(ExpectedModelSha256, modelSha256)
        modelBytes.fill(0)

        val samples = syntheticSamples()
        val durationsMs = mutableListOf<Double>()
        val probabilities = mutableListOf<Float>()
        var failures = 0
        val analyzer = DagOnDeviceImageAnalyzer.create(context)
        try {
            samples.forEach { image ->
                val started = SystemClock.elapsedRealtimeNanos()
                when (val result = analyzer.analyze(image)) {
                    is DagImageAnalysisResult.Classified -> probabilities += result.filterProbability
                    is DagImageAnalysisResult.Unavailable -> failures += 1
                }
                durationsMs += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            }
            repeat(10) {
                val started = SystemClock.elapsedRealtimeNanos()
                when (val result = analyzer.analyze(samples[it % samples.size])) {
                    is DagImageAnalysisResult.Classified -> probabilities += result.filterProbability
                    is DagImageAnalysisResult.Unavailable -> failures += 1
                }
                durationsMs += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            }
        } finally {
            (analyzer as? AutoCloseable)?.close()
        }

        val memoryInfo = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val report =
            JSONObject()
                .put("schema_version", "gloshia-lab-device-model-smoke-v1")
                .put("model", DagVisualModelInfo.ModelAssetPath)
                .put("model_sha256", modelSha256)
                .put("device", Build.MODEL)
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("samples", samples.size)
                .put("inferences", durationsMs.size)
                .put("failures", failures)
                .put("probability_min", probabilities.minOrNull()?.toDouble())
                .put("probability_max", probabilities.maxOrNull()?.toDouble())
                .put("p50_ms", durationsMs.percentile(0.50))
                .put("p95_ms", durationsMs.percentile(0.95))
                .put("max_ms", durationsMs.maxOrNull())
                .put("total_pss_kb", memoryInfo.totalPss)
                .put("final_sealed_opened", false)

        Log.i(LogTag, report.toString())
        instrumentation.sendStatus(2, android.os.Bundle().apply { putString(StatusKey, report.toString()) })
        assertEquals("model_execution_failures", 0, failures)
        assertTrue("non_finite_probabilities", probabilities.all { it.isFinite() && it in 0f..1f })
    }

    private fun syntheticSamples(): List<DagPreparedImage> =
        List(12) { sampleIndex ->
            val rgb = ByteArray(DagImageDecodeContract.PreparedByteCount)
            for (pixel in 0 until DagImageDecodeContract.TargetSize * DagImageDecodeContract.TargetSize) {
                val x = pixel % DagImageDecodeContract.TargetSize
                val y = pixel / DagImageDecodeContract.TargetSize
                val base = (pixel * 3)
                rgb[base] = ((x * 255 / 223 + sampleIndex * 13) and 0xFF).toByte()
                rgb[base + 1] = ((y * 255 / 223 + sampleIndex * 7) and 0xFF).toByte()
                rgb[base + 2] = (((x + y + sampleIndex * 11) * 255 / 446) and 0xFF).toByte()
            }
            DagPreparedImage(
                width = DagImageDecodeContract.TargetSize,
                height = DagImageDecodeContract.TargetSize,
                rgb888 = rgb,
            )
        }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private fun List<Double>.percentile(fraction: Double): Double {
        val rank = ceil(size * fraction).toInt().coerceIn(1, size)
        return sorted()[rank - 1]
    }

    private companion object {
        const val LogTag = "GloshiaLabModel"
        const val StatusKey = "gloshia_lab_model_report"
        const val ExpectedModelSha256 = "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
    }
}
