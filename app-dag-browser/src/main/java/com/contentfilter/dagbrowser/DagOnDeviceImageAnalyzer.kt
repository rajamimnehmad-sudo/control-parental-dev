package com.contentfilter.dagbrowser

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.Closeable
import java.nio.FloatBuffer

internal fun interface DagImageAnalyzer {
    fun analyze(image: DagPreparedImage): DagImageAnalysisResult
}

internal sealed interface DagImageAnalysisResult {
    data class Classified(
        val filterProbability: Float,
    ) : DagImageAnalysisResult

    data class Unavailable(
        val reason: String,
    ) : DagImageAnalysisResult
}

internal object UnavailableDagImageAnalyzer : DagImageAnalyzer {
    override fun analyze(image: DagPreparedImage): DagImageAnalysisResult =
        DagImageAnalysisResult.Unavailable(DagMediaAnalysisPolicy.AnalyzerUnavailableReason)
}

/**
 * Prevents the ONNX session from closing while a worker is inside inference. Closing is
 * non-blocking for the Activity: the final in-flight analysis closes the delegate exactly once.
 */
internal class DagLifecycleImageAnalyzer(
    private val delegate: DagImageAnalyzer,
) : DagImageAnalyzer,
    Closeable {
    private val lock = Any()
    private var activeAnalyses = 0
    private var closeRequested = false
    private var delegateClosed = false

    override fun analyze(image: DagPreparedImage): DagImageAnalysisResult {
        val accepted =
            synchronized(lock) {
                if (closeRequested) {
                    false
                } else {
                    activeAnalyses += 1
                    true
                }
            }
        if (!accepted) return DagImageAnalysisResult.Unavailable(AnalyzerClosedReason)

        return try {
            delegate.analyze(image)
        } finally {
            val closeable =
                synchronized(lock) {
                    activeAnalyses -= 1
                    closeableIfReady()
                }
            closeDelegate(closeable)
        }
    }

    override fun close() {
        val closeable =
            synchronized(lock) {
                closeRequested = true
                closeableIfReady()
            }
        closeDelegate(closeable)
    }

    private fun closeableIfReady(): Closeable? {
        if (!closeRequested || activeAnalyses != 0 || delegateClosed) return null
        delegateClosed = true
        return delegate as? Closeable
    }

    private fun closeDelegate(closeable: Closeable?) {
        if (closeable != null) runCatching(closeable::close)
    }

    internal companion object {
        const val AnalyzerClosedReason = "analyzer_closed"
    }
}

/**
 * Runs the single TinyCLIP image encoder and binary policy head entirely on device.
 *
 * The model never receives URLs, text, or persisted media. Its input is the bounded 224x224 RGB
 * buffer produced by [AndroidDagImagePreprocessor].
 */
internal class DagOnDeviceImageAnalyzer private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : DagImageAnalyzer,
    Closeable {
    private val normalizedBuffers =
        ThreadLocal.withInitial {
            FloatArray(DagImageDecodeContract.PreparedByteCount)
        }

    override fun analyze(image: DagPreparedImage): DagImageAnalysisResult {
        if (!DagImageDecodeContract.isValid(image)) {
            return DagImageAnalysisResult.Unavailable(InvalidModelInputReason)
        }
        val normalized = requireNotNull(normalizedBuffers.get())
        normalizeNchw(image.rgb888, normalized)
        return try {
            OnnxTensor
                .createTensor(
                    environment,
                    FloatBuffer.wrap(normalized),
                    ModelInputShape,
                ).use { tensor ->
                    session.run(mapOf(ModelInputName to tensor)).use { output ->
                        val probability = output.filterProbability()
                        if (probability.isFinite() && probability in 0f..1f) {
                            DagImageAnalysisResult.Classified(probability)
                        } else {
                            DagImageAnalysisResult.Unavailable(InvalidModelOutputReason)
                        }
                    }
                }
        } catch (_: Exception) {
            DagImageAnalysisResult.Unavailable(ModelExecutionFailedReason)
        } finally {
            normalized.fill(0f)
        }
    }

    override fun close() {
        session.close()
    }

    private fun normalizeNchw(
        rgb: ByteArray,
        output: FloatArray,
    ) {
        val pixelCount = DagImageDecodeContract.TargetSize * DagImageDecodeContract.TargetSize
        for (pixelIndex in 0 until pixelCount) {
            val sourceIndex = pixelIndex * DagImageDecodeContract.RgbChannelCount
            for (channel in 0 until DagImageDecodeContract.RgbChannelCount) {
                val value = (rgb[sourceIndex + channel].toInt() and 0xFF) / 255f
                output[channel * pixelCount + pixelIndex] =
                    (value - ImageMean[channel]) / ImageStandardDeviation[channel]
            }
        }
    }

    private fun OrtSession.Result.filterProbability(): Float {
        val rows = this[0].value as? Array<*> ?: return Float.NaN
        val row = rows.firstOrNull() as? FloatArray ?: return Float.NaN
        return row.firstOrNull() ?: Float.NaN
    }

    internal companion object {
        fun create(context: Context): DagImageAnalyzer {
            var modelBytes: ByteArray? = null
            return try {
                val environment = OrtEnvironment.getEnvironment()
                modelBytes = context.assets.open(ModelAssetPath).use { it.readBytes() }
                val options =
                    OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                        setInterOpNumThreads(1)
                    }
                val session =
                    options.use {
                        environment.createSession(
                            requireNotNull(modelBytes),
                            it,
                        )
                    }
                DagOnDeviceImageAnalyzer(environment, session)
            } catch (_: Exception) {
                UnavailableDagImageAnalyzer
            } finally {
                modelBytes?.fill(0)
            }
        }

        const val ModelAssetPath = "dag-model/tinyclip-bounded-finetune-r1-int8.onnx"
        const val ModelInputName = "pixel_values"
        const val FilterThreshold = 0.4f
        const val UncertainRegionalReviewFloor = 0.3f
        const val UncertainRegionalFilterThreshold = 0.45f
        const val RegionalFilterThreshold = 0.5f
        const val RegionalStrongFilterThreshold = 0.7f
        const val RegionalConsensusMinimum = 2
        const val ModelAllowReason = "model_allow"
        const val ModelFilterReason = "model_filter"
        const val InvalidModelInputReason = "invalid_model_input"
        const val InvalidModelOutputReason = "invalid_model_output"
        const val ModelExecutionFailedReason = "model_execution_failed"
        private val ModelInputShape = longArrayOf(1, 3, 224, 224)
        private val ImageMean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val ImageStandardDeviation =
            floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
