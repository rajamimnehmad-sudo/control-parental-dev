package com.contentfilter.dagbrowser

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.os.Build
import android.os.SystemClock
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Isolated, opt-in microbenchmark for the TinyCLIP ONNX asset used by DAG.
 *
 * A generic androidTest run skips this class. An operator must pass `dagOrtBenchmark=true` and one
 * `dagOrtBackend` (`cpu_current`, `xnnpack` or `nnapi`). One instrumentation invocation measures
 * only that backend so backend order, cooldown and thermal gates can be controlled externally.
 * Non-CPU runs also require the synthetic output logged by a prior CPU run as
 * `dagOrtReferenceOutput`.
 *
 * This measures session construction plus sequential and two-worker session.run latency. Model
 * loading, tensor construction and deterministic input generation remain outside timed regions.
 * The numerical comparison is only a synthetic execution smoke test. It does not establish policy
 * parity, corpus quality or node-level execution-provider attribution; those are separate gates.
 */
@RunWith(AndroidJUnit4::class)
class TinyClipBackendBenchmarkTest {
    @Test
    fun benchmarkRequestedBackend() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "TinyCLIP backend benchmark is opt-in; pass $EnabledArgument=true",
            arguments.getString(EnabledArgument).equals("true", ignoreCase = true),
        )
        val requestedBackend =
            arguments.getString(BackendArgument)?.trim().orEmpty()
        val backend =
            Backend.entries.singleOrNull { it.label == requestedBackend }
                ?: error(
                    "$BackendArgument must be one of " +
                        Backend.entries.joinToString { it.label },
                )
        val referenceOutput =
            arguments
                .getString(ReferenceOutputArgument)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toFloatOrNull()
        require(
            referenceOutput == null ||
                (referenceOutput.isFinite() && referenceOutput in 0f..1f),
        ) {
            "$ReferenceOutputArgument must be a finite probability when supplied"
        }
        if (backend != Backend.CpuCurrent) {
            require(referenceOutput != null) {
                "$ReferenceOutputArgument must contain the finite output logged by a prior " +
                    "cpu_current run"
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val modelBytes =
            context.assets.open(DagOnDeviceImageAnalyzer.ModelAssetPath).use { input ->
                input.readBytes()
            }
        val primaryInput = deterministicNormalizedInput()
        val secondaryInput = primaryInput.copyOf()
        val environment = OrtEnvironment.getEnvironment()
        val metadata =
            JSONObject()
                .put("event", "start")
                .put("scope", "backend_microbenchmark_not_policy_parity")
                .put("selected_backend", backend.label)
                .put("model_asset", DagOnDeviceImageAnalyzer.ModelAssetPath)
                .put("model_sha256", modelBytes.sha256())
                .put("input_sha256", primaryInput.sha256())
                .put("input_shape", JSONArray(ModelInputShape.toList()))
                .put("warmups", WarmupCount)
                .put("iterations", MeasurementCount)
                .put("concurrent_workers", ConcurrentWorkers)
                .put("concurrent_warmup_rounds", ConcurrentWarmupRounds)
                .put("concurrent_measurement_rounds", ConcurrentMeasurementRounds)
                .put("reference_output", referenceOutput?.toDouble() ?: JSONObject.NULL)
                .put("device", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                .put("ort_version", environment.version)
                .put("available_providers", JSONArray(OrtEnvironment.getAvailableProviders().map { it.name }))
                .put(
                    "pending_gates",
                    JSONArray(
                        listOf(
                            "external_counterbalanced_repetitions_and_thermal_control",
                            "external_process_timeout_for_non_interruptible_native_run",
                            "node_level_execution_provider_attribution",
                            "frozen_corpus_policy_parity",
                        ),
                    ),
                )
        Log.i(LogTag, metadata.toString())

        var measuredResult: BackendResult? = null
        try {
            OnnxTensor
                .createTensor(
                    environment,
                    FloatBuffer.wrap(primaryInput),
                    ModelInputShape,
                ).use { primaryTensor ->
                    OnnxTensor
                        .createTensor(
                            environment,
                            FloatBuffer.wrap(secondaryInput),
                            ModelInputShape,
                        ).use { secondaryTensor ->
                            val measured =
                                benchmarkBackend(
                                    environment = environment,
                                    modelBytes = modelBytes,
                                    tensors = listOf(primaryTensor, secondaryTensor),
                                    backend = backend,
                                )
                            val probe =
                                when {
                                    measured.status != StatusOk -> ProviderProbe.BenchmarkFailed
                                    backend == Backend.CpuCurrent -> ProviderProbe.NotApplicable
                                    else ->
                                        probeFullGraphSupport(
                                            environment,
                                            modelBytes,
                                            primaryTensor,
                                            backend,
                                        )
                                }
                            measuredResult = measured.copy(providerProbe = probe)
                        }
                }
        } finally {
            primaryInput.fill(0f)
            secondaryInput.fill(0f)
            modelBytes.fill(0)
        }

        val timedResult = requireNotNull(measuredResult) { "requested_backend_was_not_measured" }
        Log.i(LogTag, timedResult.toJson().toString())
        assertEquals(
            "Requested backend ${backend.label} must complete every measurement: ${timedResult.error}",
            StatusOk,
            timedResult.status,
        )

        val outputs = timedResult.observedOutputs
        assertTrue("Requested backend produced no smoke outputs", outputs.isNotEmpty())
        if (backend == Backend.CpuCurrent && referenceOutput == null) {
            Log.i(
                LogTag,
                JSONObject()
                    .put("event", "numerical_smoke_reference")
                    .put("backend", backend.label)
                    .put("reference_output", outputs.last().toDouble())
                    .put("policy_parity", false)
                    .put("note", "reuse only with the same model and input SHA-256")
                    .toString(),
            )
        } else {
            val expected = requireNotNull(referenceOutput)
            val tolerance = max(AbsoluteTolerance, abs(expected) * RelativeTolerance)
            val maximumAbsoluteDelta = outputs.maxOf { output -> abs(output - expected) }
            Log.i(
                LogTag,
                JSONObject()
                    .put("event", "numerical_smoke")
                    .put("backend", backend.label)
                    .put("reference_output", expected.toDouble())
                    .put("observed_output_min", requireNotNull(outputs.minOrNull()).toDouble())
                    .put("observed_output_max", requireNotNull(outputs.maxOrNull()).toDouble())
                    .put("maximum_absolute_delta", maximumAbsoluteDelta.toDouble())
                    .put("tolerance", tolerance.toDouble())
                    .put("equivalent", maximumAbsoluteDelta <= tolerance)
                    .put("policy_parity", false)
                    .put("note", "synthetic execution smoke only; frozen corpus gate remains pending")
                    .toString(),
            )
            assertTrue(
                "${backend.label} synthetic outputs differ from CPU reference by " +
                    "$maximumAbsoluteDelta (tolerance $tolerance); this is not a policy parity gate",
                maximumAbsoluteDelta <= tolerance,
            )
        }
    }

    private fun benchmarkBackend(
        environment: OrtEnvironment,
        modelBytes: ByteArray,
        tensors: List<OnnxTensor>,
        backend: Backend,
    ): BackendResult {
        require(tensors.size == ConcurrentWorkers)
        var creationMillis: Double? = null
        val measurements = ArrayList<Double>(MeasurementCount)
        val concurrentMeasurements = ArrayList<Double>(ConcurrentMeasurementCount)
        val concurrentRoundMeasurements = ArrayList<Double>(ConcurrentMeasurementRounds)
        val observedOutputs = ArrayList<Float>(MeasurementCount + ConcurrentMeasurementCount)
        var output: Float? = null
        var failure: String? = null

        try {
            backend.newOptions(strictProviderOnly = false).use { options ->
                val creationStarted = SystemClock.elapsedRealtimeNanos()
                val session =
                    try {
                        environment.createSession(modelBytes, options)
                    } finally {
                        creationMillis = elapsedMillis(creationStarted)
                    }
                session.use {
                    repeat(WarmupCount) {
                        output = session.runScalar(tensors.first())
                    }
                    repeat(MeasurementCount) {
                        val started = SystemClock.elapsedRealtimeNanos()
                        output = session.runScalar(tensors.first())
                        measurements += elapsedMillis(started)
                        observedOutputs += requireNotNull(output)
                    }
                    val concurrent = benchmarkConcurrent(session, tensors)
                    concurrentMeasurements += concurrent.callMillis
                    concurrentRoundMeasurements += concurrent.roundMillis
                    observedOutputs += concurrent.outputs
                }
            }
        } catch (throwable: Exception) {
            if (throwable is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            failure = throwable.compactDescription()
        }

        val complete =
            failure == null &&
                measurements.size == MeasurementCount &&
                concurrentMeasurements.size == ConcurrentMeasurementCount &&
                concurrentRoundMeasurements.size == ConcurrentMeasurementRounds &&
                observedOutputs.size == MeasurementCount + ConcurrentMeasurementCount &&
                output != null
        return if (complete) {
            BackendResult(
                backend = backend,
                status = StatusOk,
                sessionCreationMillis = creationMillis,
                inferenceMillis = measurements,
                concurrentInferenceMillis = concurrentMeasurements,
                concurrentRoundMillis = concurrentRoundMeasurements,
                output = output,
                observedOutputs = observedOutputs,
                providerProbe = ProviderProbe.Pending,
                error = null,
            )
        } else {
            BackendResult(
                backend = backend,
                status = StatusError,
                sessionCreationMillis = creationMillis,
                inferenceMillis = measurements,
                concurrentInferenceMillis = concurrentMeasurements,
                concurrentRoundMillis = concurrentRoundMeasurements,
                output = output,
                observedOutputs = observedOutputs,
                providerProbe = ProviderProbe.Pending,
                error = failure ?: "incomplete_measurements",
            )
        }
    }

    private fun benchmarkConcurrent(
        session: OrtSession,
        tensors: List<OnnxTensor>,
    ): ConcurrentMeasurements {
        val executor = Executors.newFixedThreadPool(ConcurrentWorkers)
        return try {
            repeat(ConcurrentWarmupRounds) {
                runConcurrentRound(session, tensors, executor)
            }
            val callMillis = ArrayList<Double>(ConcurrentMeasurementCount)
            val roundMillis = ArrayList<Double>(ConcurrentMeasurementRounds)
            val outputs = ArrayList<Float>(ConcurrentMeasurementCount)
            repeat(ConcurrentMeasurementRounds) {
                val round = runConcurrentRound(session, tensors, executor)
                callMillis += round.calls.map(ConcurrentCall::elapsedMillis)
                roundMillis += round.elapsedMillis
                outputs += round.calls.map(ConcurrentCall::output)
            }
            ConcurrentMeasurements(
                callMillis = callMillis,
                roundMillis = roundMillis,
                outputs = outputs,
            )
        } finally {
            executor.shutdown()
            if (!Thread.currentThread().isInterrupted) {
                check(
                    executor.awaitTermination(
                        ConcurrentShutdownTimeoutSeconds,
                        TimeUnit.SECONDS,
                    ),
                ) {
                    "concurrent_executor_did_not_terminate"
                }
            }
        }
    }

    private fun runConcurrentRound(
        session: OrtSession,
        tensors: List<OnnxTensor>,
        executor: ExecutorService,
    ): ConcurrentRound {
        val ready = CountDownLatch(tensors.size)
        val start = CountDownLatch(1)
        val futures = mutableListOf<Future<ConcurrentCall>>()
        try {
            tensors.forEach { tensor ->
                futures +=
                    executor.submit<ConcurrentCall> {
                        ready.countDown()
                        start.await()
                        val started = SystemClock.elapsedRealtimeNanos()
                        val output = session.runScalar(tensor)
                        ConcurrentCall(
                            elapsedMillis = elapsedMillis(started),
                            output = output,
                        )
                    }
            }
        } catch (throwable: Throwable) {
            futures.forEach { future -> future.cancel(true) }
            start.countDown()
            throw throwable
        }
        var inferenceStarted = false
        return try {
            check(ready.await(ConcurrentWorkerReadyTimeoutSeconds, TimeUnit.SECONDS)) {
                "concurrent_workers_not_ready"
            }
            val roundStarted = SystemClock.elapsedRealtimeNanos()
            inferenceStarted = true
            start.countDown()

            // A native OrtSession.run is not safely cancellable from Java. Once released, wait for
            // both calls even if the instrumentation thread is interrupted, then restore the
            // interruption. A hard hang must be contained by killing the whole opt-in test process;
            // closing the shared session while a native run is active would introduce a race.
            val awaited = futures.map(::awaitConcurrentCall)
            val wasInterrupted = awaited.any(AwaitedConcurrentCall::interrupted)
            val failure = awaited.firstNotNullOfOrNull(AwaitedConcurrentCall::failure)
            if (wasInterrupted) {
                Thread.currentThread().interrupt()
                throw InterruptedException("concurrent_wait_interrupted")
            }
            check(failure == null) {
                "concurrent_worker_failed:${failure?.compactDescription()}"
            }
            ConcurrentRound(
                elapsedMillis = elapsedMillis(roundStarted),
                calls = awaited.map { result -> requireNotNull(result.call) },
            )
        } finally {
            if (!inferenceStarted) {
                futures.forEach { future -> future.cancel(true) }
                start.countDown()
            }
        }
    }

    private fun awaitConcurrentCall(future: Future<ConcurrentCall>): AwaitedConcurrentCall {
        var interrupted = false
        while (true) {
            try {
                return AwaitedConcurrentCall(
                    call = future.get(),
                    failure = null,
                    interrupted = interrupted,
                )
            } catch (_: InterruptedException) {
                interrupted = true
            } catch (throwable: Throwable) {
                return AwaitedConcurrentCall(
                    call = null,
                    failure = throwable,
                    interrupted = interrupted,
                )
            }
        }
    }

    /**
     * A normal EP session always retains ORT CPU fallback. This separate, untimed probe disables
     * that fallback: success proves that the whole graph can stay on the requested provider;
     * failure means the timed result may include partial or total CPU fallback.
     */
    private fun probeFullGraphSupport(
        environment: OrtEnvironment,
        modelBytes: ByteArray,
        tensor: OnnxTensor,
        backend: Backend,
    ): ProviderProbe =
        try {
            backend.newOptions(strictProviderOnly = true).use { options ->
                environment.createSession(modelBytes, options).use { session ->
                    session.runScalar(tensor)
                }
            }
            ProviderProbe.FullGraphSupported
        } catch (throwable: Exception) {
            ProviderProbe.FallbackRequired(throwable.compactDescription())
        }

    private fun OrtSession.runScalar(tensor: OnnxTensor): Float =
        run(mapOf(DagOnDeviceImageAnalyzer.ModelInputName to tensor)).use { result ->
            val rows = result[0].value as? Array<*> ?: error("unexpected_output_container")
            val row = rows.firstOrNull() as? FloatArray ?: error("unexpected_output_row")
            val value = row.firstOrNull() ?: error("missing_output_scalar")
            check(value.isFinite()) { "non_finite_output" }
            check(value in 0f..1f) { "out_of_range_output" }
            value
        }

    private fun deterministicNormalizedInput(): FloatArray {
        val pixelCount = TargetSize * TargetSize
        return FloatArray(RgbChannelCount * pixelCount).also { output ->
            for (pixelIndex in 0 until pixelCount) {
                for (channel in 0 until RgbChannelCount) {
                    val byteValue = (pixelIndex * 37 + channel * 101 + 17) and 0xFF
                    val unitValue = byteValue / 255f
                    output[channel * pixelCount + pixelIndex] =
                        (unitValue - ImageMean[channel]) / ImageStandardDeviation[channel]
                }
            }
        }
    }

    private fun Backend.newOptions(strictProviderOnly: Boolean): OrtSession.SessionOptions =
        OrtSession.SessionOptions().apply {
            when (this@newOptions) {
                Backend.CpuCurrent -> {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                }

                Backend.Xnnpack -> {
                    setIntraOpNumThreads(1)
                    setInterOpNumThreads(1)
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                    addXnnpack(mapOf("intra_op_num_threads" to "2"))
                }

                Backend.Nnapi -> {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED))
                }
            }
            if (strictProviderOnly) {
                addConfigEntry("session.disable_cpu_ep_fallback", "1")
            }
        }

    private fun BackendResult.toJson(): JSONObject {
        val sorted = inferenceMillis.sorted()
        val concurrentSorted = concurrentInferenceMillis.sorted()
        val concurrentRoundSorted = concurrentRoundMillis.sorted()
        return JSONObject()
            .put("event", "backend_result")
            .put("backend", backend.label)
            .put("configuration", backend.configuration)
            .put("status", status)
            .put("session_creation_ms", sessionCreationMillis ?: JSONObject.NULL)
            .put("warmups", WarmupCount)
            .put("iterations", inferenceMillis.size)
            .put("p50_ms", sorted.percentile(0.50))
            .put("p90_ms", sorted.percentile(0.90))
            .put("p95_ms", sorted.percentile(0.95))
            .put("min_ms", sorted.firstOrNull() ?: JSONObject.NULL)
            .put("max_ms", sorted.lastOrNull() ?: JSONObject.NULL)
            .put("concurrent_workers", ConcurrentWorkers)
            .put("concurrent_iterations", concurrentInferenceMillis.size)
            .put("concurrent_call_p50_ms", concurrentSorted.percentile(0.50))
            .put("concurrent_call_p90_ms", concurrentSorted.percentile(0.90))
            .put("concurrent_call_p95_ms", concurrentSorted.percentile(0.95))
            .put("concurrent_rounds", concurrentRoundMillis.size)
            .put("concurrent_round_p50_ms", concurrentRoundSorted.percentile(0.50))
            .put("concurrent_round_p90_ms", concurrentRoundSorted.percentile(0.90))
            .put("concurrent_round_p95_ms", concurrentRoundSorted.percentile(0.95))
            .put("output", output?.toDouble() ?: JSONObject.NULL)
            .put("observed_output_count", observedOutputs.size)
            .put(
                "observed_output_min",
                observedOutputs.minOrNull()?.toDouble() ?: JSONObject.NULL,
            )
            .put(
                "observed_output_max",
                observedOutputs.maxOrNull()?.toDouble() ?: JSONObject.NULL,
            )
            .put("provider_probe", providerProbe.label)
            .put("provider_probe_error", providerProbe.error ?: JSONObject.NULL)
            .put("policy_parity", false)
            .put("provider_node_attribution", "not_measured")
            .put("error", error ?: JSONObject.NULL)
    }

    private fun List<Double>.percentile(fraction: Double): Any {
        if (isEmpty()) return JSONObject.NULL
        val rank = ceil(size * fraction).toInt().coerceIn(1, size)
        return this[rank - 1]
    }

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun FloatArray.sha256(): String {
        val bytes = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach { value -> bytes.putFloat(value) }
        return bytes.array().sha256()
    }

    private fun elapsedMillis(startedNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0

    private fun Throwable.compactDescription(): String {
        val summary = "${javaClass.simpleName}:${message.orEmpty()}"
        return summary.replace(Regex("\\s+"), " ").take(MaxErrorLength)
    }

    private enum class Backend(
        val label: String,
        val configuration: String,
    ) {
        CpuCurrent(
            label = "cpu_current",
            configuration = "ORT CPU; intra=2; inter=1",
        ),
        Xnnpack(
            label = "xnnpack",
            configuration = "XNNPACK intra=2; ORT intra=1; inter=1; spinning=0; CPU fallback allowed",
        ),
        Nnapi(
            label = "nnapi",
            configuration =
                "NNAPI CPU_DISABLED; USE_FP16 off; dynamic-INT8 model; " +
                    "ORT CPU fallback intra=2/inter=1",
        ),
    }

    private sealed interface ProviderProbe {
        val label: String
        val error: String?

        data object NotApplicable : ProviderProbe {
            override val label = "not_applicable"
            override val error: String? = null
        }

        data object Pending : ProviderProbe {
            override val label = "pending"
            override val error: String? = null
        }

        data object BenchmarkFailed : ProviderProbe {
            override val label = "not_run_benchmark_failed"
            override val error: String? = null
        }

        data object FullGraphSupported : ProviderProbe {
            override val label = "full_graph_supported"
            override val error: String? = null
        }

        data class FallbackRequired(
            override val error: String,
        ) : ProviderProbe {
            override val label = "fallback_required_or_provider_unavailable"
        }
    }

    private data class BackendResult(
        val backend: Backend,
        val status: String,
        val sessionCreationMillis: Double?,
        val inferenceMillis: List<Double>,
        val concurrentInferenceMillis: List<Double>,
        val concurrentRoundMillis: List<Double>,
        val output: Float?,
        val observedOutputs: List<Float>,
        val providerProbe: ProviderProbe,
        val error: String?,
    )

    private data class ConcurrentMeasurements(
        val callMillis: List<Double>,
        val roundMillis: List<Double>,
        val outputs: List<Float>,
    )

    private data class ConcurrentRound(
        val elapsedMillis: Double,
        val calls: List<ConcurrentCall>,
    )

    private data class ConcurrentCall(
        val elapsedMillis: Double,
        val output: Float,
    )

    private data class AwaitedConcurrentCall(
        val call: ConcurrentCall?,
        val failure: Throwable?,
        val interrupted: Boolean,
    )

    private companion object {
        const val LogTag = "DagOrtBenchmark"
        const val EnabledArgument = "dagOrtBenchmark"
        const val BackendArgument = "dagOrtBackend"
        const val ReferenceOutputArgument = "dagOrtReferenceOutput"
        const val StatusOk = "ok"
        const val StatusError = "error"
        const val TargetSize = 224
        const val RgbChannelCount = 3
        const val WarmupCount = 5
        const val MeasurementCount = 30
        const val ConcurrentWorkers = 2
        const val ConcurrentWarmupRounds = 2
        const val ConcurrentMeasurementRounds = 15
        const val ConcurrentMeasurementCount = ConcurrentWorkers * ConcurrentMeasurementRounds
        const val ConcurrentWorkerReadyTimeoutSeconds = 5L
        const val ConcurrentShutdownTimeoutSeconds = 5L
        const val AbsoluteTolerance = 0.0001f
        const val RelativeTolerance = 0.001f
        const val MaxErrorLength = 480
        val ModelInputShape = longArrayOf(1, 3, TargetSize.toLong(), TargetSize.toLong())
        val ImageMean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val ImageStandardDeviation = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }
}
