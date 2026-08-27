package com.glosh.visual

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GloshiaVisualParityTest {
    @Test
    fun `shared artifact preserves frozen R3_1 identity`() {
        val model = File("src/main/assets/${GloshiaVisualModelInfo.ModelAssetPath}")

        assertTrue(model.isFile)
        assertEquals(ExpectedModelSha256, model.sha256())
        assertEquals("R3.1", GloshiaVisualModelInfo.FunctionalVersion)
        assertEquals("ONNX Runtime Android 1.27.0", GloshiaVisualModelInfo.Runtime)
        assertEquals("dag-36", GloshiaVisualModelInfo.PolicyVersion)
    }

    @Test
    fun `shared tensor is bit exact with legacy DAG preprocessing`() {
        val rgb = ByteArray(GloshiaImageContract.PreparedByteCount) { (it * 37 % 256).toByte() }
        val actual = FloatArray(GloshiaImageContract.PreparedByteCount)
        val legacy = legacyNormalizeNchw(rgb)

        GloshiaTensorPreprocessor.normalizeNchw(rgb, actual)

        assertContentEquals(legacy, actual)
    }

    @Test
    fun `shared policy matches frozen legacy decisions`() {
        val scenarios =
            listOf(
                listOf(0.2f),
                listOf(0.4f),
                listOf(0.96f),
                listOf(0.35f, 0.2f, 0.46f),
                listOf(0.2f, 0.55f, 0.55f),
                listOf(0.2f, 0.71f),
                listOf(0.2f, 0.3f, 0.4f),
            )
        scenarios.forEach { probabilities ->
            val queue = ArrayDeque(probabilities)
            val actual =
                GloshiaPreparedRasterPolicy.decide(
                    candidateId = CandidateId,
                    preparedImages =
                        if (probabilities.first() < 0.3f) {
                            List(probabilities.size) { preparedImage() }
                        } else {
                            listOf(preparedImage())
                        },
                    analyzer =
                        GloshiaVisualAnalyzer {
                            GloshiaVisualAnalysisResult.Classified(queue.removeFirst())
                        },
                )
            val expected = legacyDecision(probabilities)

            assertEquals(expected.action, actual.action, probabilities.toString())
            assertEquals(expected.reason, actual.reason, probabilities.toString())
            assertEquals(expected.probability, actual.filterProbability, probabilities.toString())
            assertEquals(expected.basis, actual.basis, probabilities.toString())
        }
    }

    private fun legacyDecision(probabilities: List<Float>): LegacyDecision {
        val full = probabilities.first()
        if (full >= 0.4f) {
            return LegacyDecision(
                GloshiaVisualAction.Block,
                "model_filter",
                full,
                if (full >= 0.95f) {
                    GloshiaVisualDecisionBasis.FullStrong
                } else {
                    GloshiaVisualDecisionBasis.FullThreshold
                },
            )
        }
        if (full < 0.3f && probabilities.size == 1) {
            return LegacyDecision(
                GloshiaVisualAction.Allow,
                "model_allow",
                full,
                GloshiaVisualDecisionBasis.None,
            )
        }
        var maximum = full
        var votes = 0
        probabilities.drop(1).forEach { probability ->
            maximum = maxOf(maximum, probability)
            if (probability >= 0.5f) votes += 1
            val basis =
                when {
                    full >= 0.3f && probability >= 0.45f ->
                        GloshiaVisualDecisionBasis.UncertainRegional
                    probability >= 0.7f -> GloshiaVisualDecisionBasis.RegionalStrong
                    votes >= 2 -> GloshiaVisualDecisionBasis.RegionalConsensus
                    else -> null
                }
            if (basis != null) {
                return LegacyDecision(
                    GloshiaVisualAction.Block,
                    "model_filter",
                    maximum,
                    basis,
                )
            }
        }
        return LegacyDecision(
            GloshiaVisualAction.Allow,
            "model_allow",
            maximum,
            GloshiaVisualDecisionBasis.None,
        )
    }

    private fun legacyNormalizeNchw(rgb: ByteArray): FloatArray {
        val output = FloatArray(GloshiaImageContract.PreparedByteCount)
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

    private fun preparedImage() =
        GloshiaPreparedImage(
            224,
            224,
            ByteArray(GloshiaImageContract.PreparedByteCount),
        )

    private fun File.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") {
            "%02x".format(it)
        }

    private data class LegacyDecision(
        val action: GloshiaVisualAction,
        val reason: String,
        val probability: Float,
        val basis: GloshiaVisualDecisionBasis,
    )

    private companion object {
        const val CandidateId = "golden_legacy_shared"
        const val ExpectedModelSha256 =
            "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
    }
}
