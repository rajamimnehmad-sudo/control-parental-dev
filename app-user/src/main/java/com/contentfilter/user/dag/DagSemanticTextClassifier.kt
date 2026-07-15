package com.contentfilter.user.dag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.sqrt

internal data class DagSemanticPrediction(
    val category: String,
    val confidence: Float,
    val margin: Float,
    val modelVersion: String,
)

internal fun dagSemanticDecision(prediction: DagSemanticPrediction?): DagClassification =
    when {
        prediction == null -> DagClassification.Uncertain
        prediction.category == "sensitive_context" -> DagClassification.Uncertain
        prediction.category !in SemanticUnsafeCategories -> DagClassification.Allowed
        prediction.confidence >= SemanticBlockThreshold && prediction.margin >= SemanticBlockMargin ->
            DagClassification.Blocked
        prediction.confidence >= SemanticReviewThreshold && prediction.margin >= SemanticReviewMargin ->
            DagClassification.Uncertain
        else -> DagClassification.Allowed
    }

private const val SemanticBlockThreshold = 0.55f
private const val SemanticBlockMargin = 0.15f
private const val SemanticReviewThreshold = 0.30f
private const val SemanticReviewMargin = 0.05f
private val SemanticUnsafeCategories = setOf("sexual", "dating", "gambling", "drugs", "violence")

@Singleton
class DagSemanticTextClassifier private constructor(
    private val modelSource: () -> InputStream,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this({ context.assets.open(ModelAsset) })

    internal constructor(modelBytes: ByteArray) : this({ ByteArrayInputStream(modelBytes) })

    private val model: DagSemanticLinearModel? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { modelSource().use(DagSemanticLinearModel::read) }.getOrNull()
    }

    internal fun classify(text: String): DagSemanticPrediction? = model?.classify(text)

    private companion object {
        const val ModelAsset = "dag/dag_text_intent_v1.bin"
    }
}

private class DagSemanticLinearModel(
    private val buckets: Int,
    private val biases: FloatArray,
    private val weights: FloatArray,
) {
    fun classify(rawText: String): DagSemanticPrediction? =
        rawText.semanticFeatures(buckets).takeIf(Map<Int, Float>::isNotEmpty)?.let { vector ->
            val logits = FloatArray(Categories.size) { biases[it] }
            Categories.indices.forEach { classIndex ->
                val offset = classIndex * buckets
                vector.forEach { (feature, value) ->
                    logits[classIndex] += weights[offset + feature] * value
                }
            }
            val maximum = logits.max()
            val exponentials = DoubleArray(logits.size) { exp((logits[it] - maximum).toDouble()) }
            val total = exponentials.sum()
            total.takeIf { it.isFinite() && it > 0.0 }?.let { validTotal ->
                val ranking = exponentials.indices.sortedByDescending { exponentials[it] }
                val bestIndex = ranking.first()
                val secondIndex = ranking.getOrElse(1) { bestIndex }
                DagSemanticPrediction(
                    category = Categories[bestIndex],
                    confidence = (exponentials[bestIndex] / validTotal).toFloat(),
                    margin = ((exponentials[bestIndex] - exponentials[secondIndex]) / validTotal).toFloat(),
                    modelVersion = "dag-local-text-2",
                )
            }
        }

    companion object {
        private val Magic =
            byteArrayOf(
                'D'.code.toByte(),
                'A'.code.toByte(),
                'G'.code.toByte(),
                'T'.code.toByte(),
                'X'.code.toByte(),
                'T'.code.toByte(),
                '1'.code.toByte(),
                0,
            )
        private val Categories =
            listOf(
                "general",
                "sexual",
                "dating",
                "gambling",
                "drugs",
                "violence",
                "sensitive_context",
            )

        fun read(input: InputStream): DagSemanticLinearModel {
            val bytes = input.readBytes()
            require(bytes.size >= HeaderBytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(Magic.size).also(buffer::get)
            require(magic.contentEquals(Magic))
            require(buffer.int == FormatVersion)
            val buckets = buffer.int
            val classes = buffer.int
            require(buckets in MinimumBuckets..MaximumBuckets)
            require(classes == Categories.size)
            val expectedFloats = classes + classes * buckets
            require(buffer.remaining() == expectedFloats * Float.SIZE_BYTES)
            val biases = FloatArray(classes) { buffer.float }
            val weights = FloatArray(classes * buckets) { buffer.float }
            require(biases.all(Float::isFinite) && weights.all(Float::isFinite))
            return DagSemanticLinearModel(buckets, biases, weights)
        }

        private const val FormatVersion = 1
        private const val HeaderBytes = 20
        private const val MinimumBuckets = 512
        private const val MaximumBuckets = 16_384
    }
}

private fun String.semanticFeatures(buckets: Int): Map<Int, Float> {
    val normalized = semanticNormalization()
    if (normalized.isBlank()) return emptyMap()
    val counts = HashMap<Int, Float>()
    val words = normalized.split(' ').filter(String::isNotBlank)
    words.forEach { word ->
        val padded = "^$word$"
        for (size in 3..5) {
            if (padded.length < size) continue
            for (offset in 0..padded.length - size) {
                val feature = "c:${padded.substring(offset, offset + size)}".featureBucket(buckets)
                counts[feature] = (counts[feature] ?: 0f) + 1f
            }
        }
        val feature = "w:$word".featureBucket(buckets)
        counts[feature] = (counts[feature] ?: 0f) + 2f
    }
    words.zipWithNext().forEach { (left, right) ->
        val feature = "b:$left $right".featureBucket(buckets)
        counts[feature] = (counts[feature] ?: 0f) + 2f
    }
    val norm = sqrt(counts.values.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0f } ?: 1f
    counts.entries.forEach { it.setValue(it.value / norm) }
    return counts
}

private fun String.semanticNormalization(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"), "")
        .replace('0', 'o')
        .replace('1', 'i')
        .replace('3', 'e')
        .replace('4', 'a')
        .replace('5', 's')
        .replace('7', 't')
        .replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
        .trim()

private fun String.featureBucket(buckets: Int): Int {
    var hash = 0x811c9dc5L
    toByteArray(Charsets.UTF_8).forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash = (hash * 0x01000193L) and 0xffffffffL
    }
    return (hash % buckets).toInt()
}
