package com.contentfilter.user.chromedataplane

import android.content.Context
import android.os.Process
import com.glosh.visual.AndroidGloshiaImagePreprocessor
import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaImagePreprocessResult
import com.glosh.visual.GloshiaImagePreprocessor
import com.glosh.visual.GloshiaPreparedRasterPolicy
import com.glosh.visual.GloshiaVisualAction
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualModelInfo
import com.glosh.visual.GloshiaVisualPolicyContract
import com.glosh.visual.LifecycleGloshiaVisualAnalyzer
import com.glosh.visual.OnDeviceGloshiaVisualAnalyzer
import com.glosh.visual.UnavailableGloshiaVisualAnalyzer
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface ChromePhotosGloshiaEngineCreation {
    data class Ready(
        val engine: ChromePhotosGloshiaDecisionEngine,
        val modelLoadMs: Double,
    ) : ChromePhotosGloshiaEngineCreation

    data class Unavailable(
        val reason: String,
        val observedModelSha256: String? = null,
    ) : ChromePhotosGloshiaEngineCreation
}

internal class ChromePhotosGloshiaDecisionEngine(
    analyzer: GloshiaVisualAnalyzer,
    private val preprocessor: GloshiaImagePreprocessor = AndroidGloshiaImagePreprocessor,
    private val nanoTime: () -> Long = System::nanoTime,
) : ChromePhotoDecisionEngine {
    override val identity = GloshiaIdentity
    private val lifecycleAnalyzer = LifecycleGloshiaVisualAnalyzer(analyzer)
    private val closed = AtomicBoolean(false)

    override fun decide(
        imageBytes: ByteArray,
        mimeType: String,
    ): ChromePhotoDecisionResult {
        val totalStarted = nanoTime()
        if (closed.get()) return unknown(AnalyzerClosedReason, ChromePhotoDecisionSource.Unavailable, totalStarted)
        val normalizedMime = mimeType.normalizedImageMimeType()
        if (normalizedMime !in GateMimeTypes) return unknown(UnsupportedMimeReason, totalStarted = totalStarted)
        if (imageBytes.isEmpty() || imageBytes.size > MaximumImageBytes) {
            return unknown(ImageByteLimitReason, totalStarted = totalStarted)
        }

        val preprocessStarted = nanoTime()
        val prepared =
            runCatching { preprocessor.prepare(imageBytes) }.getOrElse {
                return unknown(PreprocessExceptionReason, ChromePhotoDecisionSource.Error, totalStarted)
            }
        val preprocessFinished = nanoTime()
        if (prepared is GloshiaImagePreprocessResult.Rejected) {
            return unknown(
                reason = prepared.reason,
                totalStarted = totalStarted,
                preprocessNanos = preprocessFinished - preprocessStarted,
            )
        }
        prepared as GloshiaImagePreprocessResult.Ready
        val sourceBounds = prepared.sourceBounds
        if (
            sourceBounds == null ||
            !GloshiaImageContract.hasSafeDimensions(sourceBounds.width, sourceBounds.height) ||
            !mimeTypesMatch(normalizedMime, sourceBounds.mimeType)
        ) {
            prepared.images().wipe()
            return unknown(
                reason = InvalidDecodedImageReason,
                totalStarted = totalStarted,
                preprocessNanos = preprocessFinished - preprocessStarted,
            )
        }

        val images = prepared.images()
        var inferenceNanos = 0L
        return try {
            val visualDecision =
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = sha256(imageBytes).take(CandidateIdLength),
                    preparedImages = images,
                    analyzer = lifecycleAnalyzer,
                    canContinue = { !closed.get() && !Thread.currentThread().isInterrupted },
                    analyze = { currentAnalyzer, image ->
                        val started = nanoTime()
                        currentAnalyzer.analyze(image).also { inferenceNanos += nanoTime() - started }
                    },
                )
            val mapped =
                when {
                    visualDecision.action == GloshiaVisualAction.Allow &&
                        visualDecision.reason == GloshiaVisualPolicyContract.ModelAllowReason -> ChromePhotoDecision.Safe
                    visualDecision.action == GloshiaVisualAction.Block &&
                        visualDecision.reason == GloshiaVisualPolicyContract.ModelFilterReason -> ChromePhotoDecision.Block
                    else -> ChromePhotoDecision.Unknown
                }
            ChromePhotoDecisionResult(
                decision = mapped,
                reason = visualDecision.reason,
                source =
                    if (mapped == ChromePhotoDecision.Unknown) {
                        ChromePhotoDecisionSource.Unavailable
                    } else {
                        ChromePhotoDecisionSource.Engine
                    },
                filterProbability = visualDecision.filterProbability,
                basis = visualDecision.basis.name,
                preparedImageCount = visualDecision.preparedImageCount,
                timings =
                    ChromePhotoDecisionTimings(
                        decodeAndPreprocessMs = (preprocessFinished - preprocessStarted).toMillis(),
                        inferenceMs = inferenceNanos.toMillis(),
                        totalLocalMs = (nanoTime() - totalStarted).toMillis(),
                    ),
            )
        } catch (_: Exception) {
            unknown(
                reason = EngineExceptionReason,
                source = ChromePhotoDecisionSource.Error,
                totalStarted = totalStarted,
                preprocessNanos = preprocessFinished - preprocessStarted,
                inferenceNanos = inferenceNanos,
            )
        } finally {
            images.wipe()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) lifecycleAnalyzer.close()
    }

    private fun unknown(
        reason: String,
        source: ChromePhotoDecisionSource = ChromePhotoDecisionSource.Unavailable,
        totalStarted: Long,
        preprocessNanos: Long = 0L,
        inferenceNanos: Long = 0L,
    ) = ChromePhotoDecisionResult(
        decision = ChromePhotoDecision.Unknown,
        reason = reason,
        source = source,
        timings =
            ChromePhotoDecisionTimings(
                decodeAndPreprocessMs = preprocessNanos.toMillis(),
                inferenceMs = inferenceNanos.toMillis(),
                totalLocalMs = (nanoTime() - totalStarted).toMillis(),
            ),
    )

    internal companion object {
        val GloshiaIdentity =
            ChromePhotoDecisionIdentity(
                engine = GloshiaVisualModelInfo.PublicName,
                modelVersion = GloshiaVisualModelInfo.FunctionalVersion,
                modelSha256 = GloshiaVisualModelInfo.ModelSha256,
                policyVersion = GloshiaVisualModelInfo.PolicyVersion,
            )
        val GateMimeTypes = setOf("image/png", "image/jpeg", "image/webp", "image/avif")
        const val MaximumImageBytes = ChromePhotosRealUpstream.DefaultMaximumBodyBytes
        const val UnsupportedMimeReason = "unsupported_mime"
        const val ImageByteLimitReason = "image_byte_limit"
        const val PreprocessExceptionReason = "preprocess_exception"
        const val InvalidDecodedImageReason = "invalid_decoded_image"
        const val EngineExceptionReason = "engine_exception"
        const val AnalyzerClosedReason = "analyzer_closed"
        private const val CandidateIdLength = 32
    }
}

internal object ChromePhotosGloshiaEngineFactory {
    fun create(context: Context): ChromePhotosGloshiaEngineCreation {
        if (!Process.is64Bit()) {
            return ChromePhotosGloshiaEngineCreation.Unavailable("arm64_required")
        }
        val applicationContext = context.applicationContext
        val observedHash =
            runCatching {
                applicationContext.assets.open(GloshiaVisualModelInfo.ModelAssetPath).use { stream -> stream.sha256() }
            }.getOrElse {
                return ChromePhotosGloshiaEngineCreation.Unavailable("model_hash_unavailable")
            }
        if (observedHash != GloshiaVisualModelInfo.ModelSha256) {
            return ChromePhotosGloshiaEngineCreation.Unavailable("model_hash_mismatch", observedHash)
        }
        val started = System.nanoTime()
        val analyzer = OnDeviceGloshiaVisualAnalyzer.create(applicationContext)
        if (analyzer === UnavailableGloshiaVisualAnalyzer) {
            return ChromePhotosGloshiaEngineCreation.Unavailable("analyzer_unavailable", observedHash)
        }
        return ChromePhotosGloshiaEngineCreation.Ready(
            engine = ChromePhotosGloshiaDecisionEngine(analyzer),
            modelLoadMs = (System.nanoTime() - started).toMillis(),
        )
    }
}

private fun GloshiaImagePreprocessResult.Ready.images() = listOf(image) + regionalImages

private fun List<com.glosh.visual.GloshiaPreparedImage>.wipe() = forEach { it.rgb888.fill(0) }

private fun mimeTypesMatch(
    declared: String,
    decoded: String,
): Boolean = declared == decoded.normalizedImageMimeType()

private fun InputStream.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(HashBufferBytes)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    buffer.fill(0)
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun Long.toMillis(): Double = this / NanosPerMillis

private const val HashBufferBytes = 16 * 1024
private const val NanosPerMillis = 1_000_000.0
