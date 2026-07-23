package com.contentfilter.user.dag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

internal enum class DagImageDecision {
    Allowed,
    Blocked,
    Uncertain,
}

internal data class DagImageClassification(
    val decision: DagImageDecision,
    val unsafeScore: Float? = null,
    val mimeType: String? = null,
    val scores: Map<String, Float> = emptyMap(),
    val signals: Set<String> = emptySet(),
    val calibrationVersion: Long = 0,
)

internal fun dagImageDecision(
    unsafeScore: Float,
    calibration: DagImageCalibration = DagImageCalibration(),
): DagImageDecision =
    when {
        !unsafeScore.isFinite() || unsafeScore < 0f || unsafeScore > 1f -> DagImageDecision.Uncertain
        unsafeScore <= calibration.professionalSafe -> DagImageDecision.Allowed
        unsafeScore >= calibration.professionalBlock -> DagImageDecision.Blocked
        else -> DagImageDecision.Uncertain
    }

internal fun dagProfessionalImageDecision(
    unsafeProbability: Float,
    calibration: DagImageCalibration = DagImageCalibration(),
): DagImageDecision =
    when {
        !unsafeProbability.isFinite() || unsafeProbability !in 0f..1f -> DagImageDecision.Uncertain
        unsafeProbability <= calibration.professionalSafe -> DagImageDecision.Allowed
        unsafeProbability >= calibration.professionalBlock -> DagImageDecision.Blocked
        else -> DagImageDecision.Uncertain
    }

internal fun dagEnsembleImageDecision(
    professional: DagImageDecision,
    legacy: DagImageDecision,
): DagImageDecision = if (professional != DagImageDecision.Uncertain) professional else legacy

internal class DagImageClassifier(
    context: Context,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var interpreter: Interpreter? = null
    private val professionalClassifier = DagProfessionalImageClassifier(applicationContext)
    private val modestyClassifier = DagModestyImageClassifier(applicationContext)
    private val tzniutPoseClassifier = DagTzniutPoseClassifier(applicationContext)
    private val calibrationStore = DagImageCalibrationStore(applicationContext)

    fun exactDecision(imageHash: String): DagImageDecision? = calibrationStore.exactDecision(imageHash)

    fun prepare() {
        synchronized(lock) {
            runCatching {
                professionalClassifier.prepare()
                modestyClassifier.prepare()
            }
        }
    }

    fun classify(
        bytes: ByteArray,
        mimeType: String?,
        audienceContext: DagImageAudienceContext = DagImageAudienceContext.Unknown,
    ): DagImageClassification =
        synchronized(lock) {
            val detectedMime = supportedStaticImageMime(bytes, mimeType)
            if (detectedMime == null) {
                return@synchronized DagImageClassification(DagImageDecision.Uncertain)
            }
            val bitmap =
                decodeForClassification(bytes)
                    ?: return@synchronized DagImageClassification(DagImageDecision.Uncertain)
            try {
                val classificationStartedAt = SystemClock.elapsedRealtime()
                val calibration = calibrationStore.current()
                val professionalAvailable = professionalClassifier.isAvailable()
                val professional =
                    if (professionalAvailable) {
                        professionalClassifier.classify(bitmap, calibration)
                    } else {
                        DagImageClassification(DagImageDecision.Uncertain)
                    }
                var legacyScore: Float? = null
                val ensembleDecision =
                    if (professionalAvailable && professional.decision != DagImageDecision.Uncertain) {
                        professional.decision
                    } else {
                        val output = Array(1) { FloatArray(OutputClasses) }
                        model().run(bitmapToInput(bitmap), output)
                        legacyScore = output[0][UnsafeOutputIndex]
                        dagEnsembleImageDecision(professional.decision, dagImageDecision(legacyScore, calibration))
                    }
                var modestyScores =
                    if (ensembleDecision != DagImageDecision.Blocked) {
                        modestyClassifier.classify(
                            bitmap,
                        )
                    } else {
                        null
                    }
                if (
                    modestyScores != null &&
                    modestyScores.femaleFace >= calibration.femaleFace * PoseFemaleContextRatio
                ) {
                    decodeForPose(bytes)?.let { poseBitmap ->
                        try {
                            modestyScores = modestyScores.withTzniutPose(tzniutPoseClassifier.classify(poseBitmap))
                        } finally {
                            poseBitmap.recycle()
                        }
                    }
                }
                val modestyDecision =
                    modestyScores?.let { dagModestyImageDecision(it, calibration, audienceContext) }
                        ?: DagImageDecision.Allowed
                val decision =
                    dagAudienceAwareImageDecision(
                        ensembleDecision,
                        modestyDecision,
                        modestyScores,
                        calibration,
                        audienceContext,
                    )
                Log.d(
                    ImageMetricsTag,
                    "decision=${decision.name.lowercase()} elapsed_ms=" +
                        (SystemClock.elapsedRealtime() - classificationStartedAt) +
                        " fallback=${legacyScore != null} modesty=${modestyDecision.name.lowercase()} " +
                        "audience=${audienceContext.name.lowercase()}",
                )
                val scores =
                    buildMap {
                        professional.unsafeScore?.let { put("professional", it) }
                        legacyScore?.let { put("legacy", it) }
                        modestyScores?.toCalibrationScores()?.let(::putAll)
                    }
                DagImageClassification(
                    decision = decision,
                    unsafeScore = maxOf(professional.unsafeScore ?: 0f, legacyScore ?: 0f),
                    mimeType = detectedMime,
                    scores = scores,
                    signals = dagImageDecisionSignals(decision, scores, calibration),
                    calibrationVersion = calibration.version,
                )
            } catch (_: RuntimeException) {
                DagImageClassification(DagImageDecision.Uncertain)
            } finally {
                bitmap.recycle()
            }
        }

    override fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            professionalClassifier.close()
            modestyClassifier.close()
            tzniutPoseClassifier.close()
        }
    }

    private fun model(): Interpreter =
        interpreter ?: loadModel().also { loaded ->
            check(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, InputSize, InputSize, 3)))
            check(loaded.getOutputTensor(0).shape().contentEquals(intArrayOf(1, OutputClasses)))
            interpreter = loaded
        }

    private fun loadModel(): Interpreter {
        val descriptor = applicationContext.assets.openFd(ModelAsset)
        descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                return Interpreter(mapped, Interpreter.Options().setNumThreads(ModelThreads))
            }
        }
    }

    private fun decodeForClassification(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in MinimumDimension..MaximumSourceDimension) return null
        if (bounds.outHeight !in MinimumDimension..MaximumSourceDimension) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MaximumDecodeDimension) sampleSize *= 2
        val decoded =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        return Bitmap.createScaledBitmap(decoded, ResizeSize, ResizeSize, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun decodeForPose(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MaximumPoseSourceDimension) sampleSize *= 2
        val decoded =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        val scale = minOf(1f, MaximumPoseDimension.toFloat() / max(decoded.width, decoded.height))
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { decoded.recycle() }
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteBuffer {
        val input =
            ByteBuffer.allocateDirect(
                InputSize * InputSize * 3 * Float.SIZE_BYTES,
            ).order(ByteOrder.nativeOrder())
        val pixels = IntArray(InputSize * InputSize)
        val offset = (ResizeSize - InputSize) / 2
        bitmap.getPixels(pixels, 0, InputSize, offset, offset, InputSize, InputSize)
        pixels.forEach { pixel ->
            input.putFloat(Color.blue(pixel) - VggMeanBlue)
            input.putFloat(Color.green(pixel) - VggMeanGreen)
            input.putFloat(Color.red(pixel) - VggMeanRed)
        }
        input.rewind()
        return input
    }

    private fun supportedStaticImageMime(
        bytes: ByteArray,
        mimeType: String?,
    ): String? {
        if (bytes.size !in MinimumBytes..MaximumImageBytes) return null
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        if (mime == "image/svg+xml" || mime == "image/gif") return null
        if (
            bytes.startsWithAscii("GIF8") ||
            bytes.containsAscii("acTL") ||
            bytes.containsAscii("ANIM") ||
            bytes.containsAscii("ANMF")
        ) {
            return null
        }
        return when {
            bytes.isJpeg() -> "image/jpeg"
            bytes.isPng() -> "image/png"
            bytes.isWebP() -> "image/webp"
            bytes.isStaticAvif() -> "image/avif"
            else -> bitmapMime(bytes)
        }
    }

    private fun bitmapMime(bytes: ByteArray): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outMimeType
            ?.lowercase()
            ?.takeIf(SupportedBitmapMimes::contains)
    }

    private fun ByteArray.isJpeg(): Boolean =
        size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte()

    private fun ByteArray.isPng(): Boolean =
        size >= 8 &&
            this[0] == 0x89.toByte() &&
            this[1] == 0x50.toByte() &&
            this[2] == 0x4e.toByte() &&
            this[3] == 0x47.toByte()

    private fun ByteArray.isWebP(): Boolean =
        size >= 12 && startsWithAscii("RIFF") && copyOfRange(8, 12).startsWithAscii("WEBP")

    private fun ByteArray.isStaticAvif(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            size >= 12 &&
            copyOfRange(4, 8).startsWithAscii("ftyp") &&
            containsAscii("avif") &&
            !containsAscii("avis")

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && value.indices.all { index -> this[index].toInt() and 0xff == value[index].code }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (size < value.length) return false
        return (0..size - value.length).any { offset ->
            value.indices.all { index -> this[offset + index].toInt() and 0xff == value[index].code }
        }
    }

    companion object {
        internal const val SafeThreshold = 0.15f
        internal const val BlockThreshold = 0.65f
        internal const val MaximumImageBytes = 4 * 1024 * 1024

        private const val ModelAsset = "dag/nsfw_open_nsfw_quantized.tflite"
        private const val InputSize = 224
        private const val ResizeSize = 256
        private const val OutputClasses = 2
        private const val UnsafeOutputIndex = 1
        private const val ModelThreads = 2
        private const val ImageMetricsTag = "DagImageMetrics"
        private const val MinimumBytes = 32
        private const val MinimumDimension = 24
        private const val MaximumSourceDimension = 16_384
        private const val MaximumDecodeDimension = 1_024
        private const val MaximumPoseSourceDimension = 960
        private const val MaximumPoseDimension = 480
        private const val PoseFemaleContextRatio = 0.65f
        private const val VggMeanBlue = 103.939f
        private const val VggMeanGreen = 116.779f
        private const val VggMeanRed = 123.68f
        private val SupportedBitmapMimes =
            setOf(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/avif",
                "image/heif",
                "image/heic",
                "image/bmp",
                "image/x-ms-bmp",
            )
    }
}

internal fun dagImageDecisionSignals(
    decision: DagImageDecision,
    scores: Map<String, Float>,
    calibration: DagImageCalibration,
): Set<String> =
    buildSet {
        fun signal(
            scoreKey: String,
            threshold: Float,
            signalName: String = scoreKey,
        ) {
            if ((scores[scoreKey] ?: 0f) >= threshold) add(signalName)
        }
        signal("professional", calibration.professionalSafe, "professional_content")
        signal("legacy", calibration.professionalSafe, "legacy_content")
        signal("female_breast_covered", calibration.femaleBreastCovered)
        signal("female_genitalia_covered", calibration.femaleGenitaliaCovered)
        signal("buttocks_covered", calibration.buttocksCovered)
        signal("armpits_exposed", calibration.armpitsExposed)
        signal("belly_exposed", calibration.bellyExposed)
        signal("explicit_region", calibration.explicitRegion)
        signal("sleeves_above_elbow", calibration.sleevesAboveElbow)
        signal("hem_above_knee", calibration.hemAboveKnee)
        if (isEmpty() && decision == DagImageDecision.Uncertain) add("uncertain_content")
    }
