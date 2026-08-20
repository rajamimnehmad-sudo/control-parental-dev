package com.glosh.visual

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.Closeable
import java.nio.FloatBuffer

fun interface GloshiaVisualAnalyzer {
    fun analyze(image: GloshiaPreparedImage): GloshiaVisualAnalysisResult
}

sealed interface GloshiaVisualAnalysisResult {
    data class Classified(val filterProbability: Float) : GloshiaVisualAnalysisResult

    data class Unavailable(val reason: String) : GloshiaVisualAnalysisResult
}

object UnavailableGloshiaVisualAnalyzer : GloshiaVisualAnalyzer {
    override fun analyze(image: GloshiaPreparedImage) =
        GloshiaVisualAnalysisResult.Unavailable(
            GloshiaVisualPolicyContract.AnalyzerUnavailableReason,
        )
}

class LifecycleGloshiaVisualAnalyzer(
    private val delegate: GloshiaVisualAnalyzer,
) : GloshiaVisualAnalyzer, Closeable {
    private val lock = Any()
    private var activeAnalyses = 0
    private var closeRequested = false
    private var delegateClosed = false

    override fun analyze(image: GloshiaPreparedImage): GloshiaVisualAnalysisResult {
        val accepted =
            synchronized(lock) {
                if (closeRequested) false else true.also { activeAnalyses += 1 }
            }
        if (!accepted) {
            return GloshiaVisualAnalysisResult.Unavailable(
                GloshiaVisualPolicyContract.AnalyzerClosedReason,
            )
        }
        return try {
            delegate.analyze(image)
        } finally {
            closeDelegate(
                synchronized(lock) {
                    activeAnalyses -= 1
                    closeableIfReady()
                },
            )
        }
    }

    override fun close() {
        closeDelegate(
            synchronized(lock) {
                closeRequested = true
                closeableIfReady()
            },
        )
    }

    private fun closeableIfReady(): Closeable? {
        if (!closeRequested || activeAnalyses != 0 || delegateClosed) return null
        delegateClosed = true
        return delegate as? Closeable
    }

    private fun closeDelegate(closeable: Closeable?) {
        if (closeable != null) runCatching(closeable::close)
    }
}

class OnDeviceGloshiaVisualAnalyzer private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : GloshiaVisualAnalyzer, Closeable {
    private val normalizedBuffers =
        ThreadLocal.withInitial { FloatArray(GloshiaImageContract.PreparedByteCount) }

    override fun analyze(image: GloshiaPreparedImage): GloshiaVisualAnalysisResult {
        if (!GloshiaImageContract.isValid(image)) {
            return GloshiaVisualAnalysisResult.Unavailable(
                GloshiaVisualPolicyContract.InvalidModelInputReason,
            )
        }
        val normalized = requireNotNull(normalizedBuffers.get())
        GloshiaTensorPreprocessor.normalizeNchw(image.rgb888, normalized)
        return try {
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(normalized), ModelInputShape)
                .use { tensor ->
                    session.run(mapOf(ModelInputName to tensor)).use { output ->
                        val probability = output.filterProbability()
                        if (probability.isFinite() && probability in 0f..1f) {
                            GloshiaVisualAnalysisResult.Classified(probability)
                        } else {
                            GloshiaVisualAnalysisResult.Unavailable(
                                GloshiaVisualPolicyContract.InvalidModelOutputReason,
                            )
                        }
                    }
                }
        } catch (_: Exception) {
            GloshiaVisualAnalysisResult.Unavailable(
                GloshiaVisualPolicyContract.ModelExecutionFailedReason,
            )
        } finally {
            normalized.fill(0f)
        }
    }

    override fun close() = session.close()

    companion object {
        fun create(context: Context): GloshiaVisualAnalyzer {
            val environment = OrtEnvironment.getEnvironment()
            var modelBytes: ByteArray? = null
            return try {
                modelBytes = context.assets.open(GloshiaVisualModelInfo.ModelAssetPath).use { it.readBytes() }
                val session =
                    OrtSession.SessionOptions().use { options ->
                        options.setIntraOpNumThreads(2)
                        options.setInterOpNumThreads(1)
                        environment.createSession(requireNotNull(modelBytes), options)
                    }
                OnDeviceGloshiaVisualAnalyzer(environment, session)
            } catch (_: Exception) {
                UnavailableGloshiaVisualAnalyzer
            } finally {
                modelBytes?.fill(0)
            }
        }

        const val ModelInputName = "pixel_values"
        private val ModelInputShape = longArrayOf(1, 3, 224, 224)
    }
}

object GloshiaTensorPreprocessor {
    fun normalizeNchw(
        rgb: ByteArray,
        output: FloatArray,
    ) {
        require(rgb.size == GloshiaImageContract.PreparedByteCount)
        require(output.size == GloshiaImageContract.PreparedByteCount)
        val pixelCount = GloshiaImageContract.TargetSize * GloshiaImageContract.TargetSize
        for (pixelIndex in 0 until pixelCount) {
            val sourceIndex = pixelIndex * GloshiaImageContract.RgbChannelCount
            for (channel in 0 until GloshiaImageContract.RgbChannelCount) {
                val value = (rgb[sourceIndex + channel].toInt() and 0xFF) / 255f
                output[channel * pixelCount + pixelIndex] =
                    (value - ImageMean[channel]) / ImageStandardDeviation[channel]
            }
        }
    }

    private val ImageMean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    private val ImageStandardDeviation = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
}

private fun OrtSession.Result.filterProbability(): Float {
    val rows = this[0].value as? Array<*> ?: return Float.NaN
    val row = rows.firstOrNull() as? FloatArray ?: return Float.NaN
    return row.firstOrNull() ?: Float.NaN
}
