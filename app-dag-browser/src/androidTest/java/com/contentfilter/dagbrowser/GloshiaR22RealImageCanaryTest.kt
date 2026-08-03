package com.contentfilter.dagbrowser

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.ceil

/** Opt-in, reversible canary that uses DAG's real decode, preprocessing and policy path. */
@RunWith(AndroidJUnit4::class)
class GloshiaR22RealImageCanaryTest {
    @Test
    fun compareR1AndR22ThroughRealPipeline() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "R2.2 canary is opt-in; pass $EnabledArgument=true",
            arguments.getString(EnabledArgument).equals("true", ignoreCase = true),
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val manifest =
            testContext.assets.open("$CanaryDirectory/$ManifestName").bufferedReader().use { it.readText() }
        val candidateBytes = testContext.assets.open("$CanaryDirectory/$CandidateName").use { it.readBytes() }
        require(candidateBytes.sha256() == CandidateSha256) { "canary_candidate_hash_mismatch" }

        val samples = JSONObject(manifest).getJSONArray("samples")
        require(samples.length() == ExpectedSamples) { "canary_sample_count_mismatch" }
        val r1ModelBytes = context.assets.open(DagOnDeviceImageAnalyzer.ModelAssetPath).use { it.readBytes() }
        val r1Sha256 = r1ModelBytes.sha256()
        r1ModelBytes.fill(0)
        require(r1Sha256 == R1Sha256) { "r1_hash_mismatch" }

        val rows = JSONArray()
        val r1Analyzer = DagOnDeviceImageAnalyzer.create(context)
        val candidateAnalyzer = ByteArrayOnnxAnalyzer(candidateBytes)
        candidateBytes.fill(0)
        try {
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                val bytes =
                    testContext.assets
                        .open("$CanaryDirectory/${sample.getString("image_name")}")
                        .use { it.readBytes() }
                require(bytes.sha256() == sample.getString("sha256")) {
                    "canary_image_hash_mismatch:${sample.getString("sample_id")}"
                }
                val payload =
                    DagMediaBytesPayload(
                        candidateId = "canary$index",
                        sourceUrl = "https://canary.invalid/$index",
                        declaredByteLength = bytes.size,
                        bytesBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    )
                bytes.fill(0)
                val candidateFirst = index % 2 == 0
                val candidateRun =
                    if (candidateFirst) runPolicy(payload, candidateAnalyzer) else null
                val r1Run = runPolicy(payload, r1Analyzer)
                val resolvedCandidateRun = candidateRun ?: runPolicy(payload, candidateAnalyzer)
                rows.put(
                    JSONObject()
                        .put("sample_id", sample.getString("sample_id"))
                        .put("human_action", sample.getString("human_action"))
                        .put("r1", r1Run.toJson())
                        .put("r2_2", resolvedCandidateRun.toJson()),
                )
            }
        } finally {
            (r1Analyzer as? Closeable)?.close()
            candidateAnalyzer.close()
        }

        val report = buildReport(rows, r1Sha256)
        Log.i(LogTag, report.toString())
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("gloshia_canary_report", report.toString()) },
        )
        val r1 = report.getJSONObject("r1")
        val candidate = report.getJSONObject("r2_2")
        assertEquals(0, candidate.getInt("pipeline_errors"))
        assertTrue(
            "R2.2 must not add false permissions",
            candidate.getInt("false_permissions") <= r1.getInt("false_permissions"),
        )
        assertTrue(
            "R2.2 must not add false filters",
            candidate.getInt("false_filters") <= r1.getInt("false_filters"),
        )
        val r1P95 = r1.getDouble("policy_p95_ms")
        val candidateP95 = candidate.getDouble("policy_p95_ms")
        assertTrue(
            "R2.2 p95 regression: candidate=$candidateP95 r1=$r1P95",
            candidateP95 <= r1P95 * MaximumP95Ratio + MaximumP95SlackMs,
        )
    }

    private fun runPolicy(
        payload: DagMediaBytesPayload,
        analyzer: DagImageAnalyzer,
    ): PolicyRun {
        val trace = DagMediaPipelineTrace()
        val started = SystemClock.elapsedRealtimeNanos()
        val decision = DagMediaBytesPolicy.decide(payload = payload, analyzer = analyzer, trace = trace)
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        return PolicyRun(decision, trace, elapsedMs)
    }

    private fun buildReport(
        rows: JSONArray,
        r1Sha256: String,
    ): JSONObject {
        val r1 = summarize(rows, "r1")
        val candidate = summarize(rows, "r2_2")
        return JSONObject()
            .put("schema_version", "gloshia-r2.2-real-image-canary-v1")
            .put("ticket", "GLOSHIA-R2.2-REVERSIBLE-CANARY-18")
            .put(
                "device",
                JSONObject().put(
                    "model",
                    Build.MODEL,
                ).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT),
            )
            .put("samples", rows.length())
            .put("candidate_sha256", CandidateSha256)
            .put("r1_sha256", r1Sha256)
            .put("r1", r1)
            .put("r2_2", candidate)
            .put("rows", rows)
            .put("peak_pss_kb", Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss)
            .put("final_sealed_opened", false)
            .put(
                "status",
                if (
                    candidate.getInt("pipeline_errors") == 0 &&
                    candidate.getInt("false_permissions") <= r1.getInt("false_permissions") &&
                    candidate.getInt("false_filters") <= r1.getInt("false_filters") &&
                    candidate.getDouble("policy_p95_ms") <=
                    r1.getDouble("policy_p95_ms") * MaximumP95Ratio + MaximumP95SlackMs
                ) {
                    "GO_REVERSIBLE_CANARY"
                } else {
                    "NO-GO"
                },
            )
    }

    private fun summarize(
        rows: JSONArray,
        key: String,
    ): JSONObject {
        var allowAllow = 0
        var allowFilter = 0
        var filterAllow = 0
        var filterFilter = 0
        var errors = 0
        val policyTimes = mutableListOf<Double>()
        val inferenceTimes = mutableListOf<Double>()
        var inferenceCount = 0
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val human = row.getString("human_action")
            val run = row.getJSONObject(key)
            val action = run.getString("action")
            val reason = run.getString("reason")
            if (human == "allow" && action == "allow") allowAllow += 1
            if (human == "allow" && action == "block") allowFilter += 1
            if (human == "filter" && action == "allow") filterAllow += 1
            if (human == "filter" && action == "block") filterFilter += 1
            if (reason !in setOf(DagOnDeviceImageAnalyzer.ModelAllowReason, DagOnDeviceImageAnalyzer.ModelFilterReason)) errors += 1
            policyTimes += run.getDouble("policy_ms")
            inferenceTimes += run.getDouble("inference_ms")
            inferenceCount += run.getInt("inference_count")
        }
        policyTimes.sort()
        inferenceTimes.sort()
        return JSONObject()
            .put("allow_as_allow", allowAllow)
            .put("allow_as_filter", allowFilter)
            .put("filter_as_allow", filterAllow)
            .put("filter_as_filter", filterFilter)
            .put("false_permissions", filterAllow)
            .put("false_filters", allowFilter)
            .put("pipeline_errors", errors)
            .put("inference_count", inferenceCount)
            .put("policy_p50_ms", policyTimes.percentile(0.50))
            .put("policy_p95_ms", policyTimes.percentile(0.95))
            .put("inference_p50_ms", inferenceTimes.percentile(0.50))
            .put("inference_p95_ms", inferenceTimes.percentile(0.95))
    }

    private data class PolicyRun(
        val decision: DagMediaDecision,
        val trace: DagMediaPipelineTrace,
        val elapsedMs: Double,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("action", decision.action.wireValue)
                .put("reason", decision.reason)
                .put("filter_probability", decision.filterProbability?.toDouble() ?: JSONObject.NULL)
                .put("policy_ms", elapsedMs)
                .put("preprocess_ms", trace.elapsedMillis(DagMediaPipelineStage.Preprocess))
                .put("inference_ms", trace.elapsedMillis(DagMediaPipelineStage.Inference))
                .put("inference_count", trace.inferenceCount)
                .put("prepared_images", trace.preparedImageCount)
                .put("regional_images", trace.regionalImageCount)
    }

    private class ByteArrayOnnxAnalyzer(modelBytes: ByteArray) : DagImageAnalyzer, Closeable {
        private val environment = OrtEnvironment.getEnvironment()
        private val session =
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                environment.createSession(modelBytes, options)
            }
        private val normalized = FloatArray(DagImageDecodeContract.PreparedByteCount)

        override fun analyze(image: DagPreparedImage): DagImageAnalysisResult {
            if (!DagImageDecodeContract.isValid(image)) {
                return DagImageAnalysisResult.Unavailable(DagOnDeviceImageAnalyzer.InvalidModelInputReason)
            }
            val pixels = DagImageDecodeContract.TargetSize * DagImageDecodeContract.TargetSize
            for (pixel in 0 until pixels) {
                val source = pixel * DagImageDecodeContract.RgbChannelCount
                for (channel in 0 until DagImageDecodeContract.RgbChannelCount) {
                    val unit = (image.rgb888[source + channel].toInt() and 0xFF) / 255f
                    normalized[channel * pixels + pixel] = (unit - ImageMean[channel]) / ImageStd[channel]
                }
            }
            return try {
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(normalized), ModelInputShape).use { tensor ->
                    session.run(mapOf(DagOnDeviceImageAnalyzer.ModelInputName to tensor)).use { output ->
                        val rows = output[0].value as? Array<*>
                        val probability = (rows?.firstOrNull() as? FloatArray)?.firstOrNull() ?: Float.NaN
                        if (probability.isFinite() && probability in 0f..1f) {
                            DagImageAnalysisResult.Classified(probability)
                        } else {
                            DagImageAnalysisResult.Unavailable(DagOnDeviceImageAnalyzer.InvalidModelOutputReason)
                        }
                    }
                }
            } catch (_: Exception) {
                DagImageAnalysisResult.Unavailable(DagOnDeviceImageAnalyzer.ModelExecutionFailedReason)
            } finally {
                normalized.fill(0f)
            }
        }

        override fun close() = session.close()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") {
            "%02x".format(it)
        }

    private fun List<Double>.percentile(fraction: Double): Double {
        val rank = ceil(size * fraction).toInt().coerceIn(1, size)
        return this[rank - 1]
    }

    private companion object {
        const val EnabledArgument = "gloshiaR22Canary"
        const val LogTag = "GloshiaR22Canary"
        const val CanaryDirectory = "gloshia-r22-canary"
        const val ManifestName = "manifest.json"
        const val CandidateName = "r2.2-candidate-b-selective-k-int8.onnx"
        const val CandidateSha256 = "7e8826f72df12ca76f21b929c3c798c967ea381b558116fd45b27bb71d461bdb"
        const val R1Sha256 = "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee"
        const val ExpectedSamples = 40
        const val MaximumP95Ratio = 1.15
        const val MaximumP95SlackMs = 5.0
        val ModelInputShape = longArrayOf(1, 3, 224, 224)
        val ImageMean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val ImageStd = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
