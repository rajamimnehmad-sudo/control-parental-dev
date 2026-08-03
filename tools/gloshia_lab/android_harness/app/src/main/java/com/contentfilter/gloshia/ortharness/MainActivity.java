package com.contentfilter.gloshia.ortharness;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.app.Activity;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String TAG = "GloshiaOrtHarness";
    private static final String CANDIDATE_NAME = "r2.1-candidate-02-int8.onnx";
    private static final String R1_NAME = "r1-official.onnx";
    private static final String INPUT_NAME = "pixel_values";
    private static final long[] INPUT_SHAPE = {1, 3, 224, 224};
    private static final int INPUT_FLOATS = 3 * 224 * 224;
    private static final int SYNTHETIC_RUNS = 30;
    private static final String CANDIDATE_SHA256 =
            "c212d005db271bebfb3fb80aade4c056334e0f4f07f2f1543976050f8c8afa3c";
    private static final String R1_SHA256 =
            "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusView = new TextView(this);
        statusView.setText("GloshIA ORT harness ejecutando…");
        statusView.setTextIsSelectable(true);
        statusView.setPadding(32, 48, 32, 48);
        setContentView(statusView);
        new Thread(this::runHarness, "gloshia-ort-harness").start();
    }

    private void runHarness() {
        JSONObject report = new JSONObject();
        try {
            report.put("schema_version", "gloshia-r2.1-ort-android-harness-v1");
            report.put("ticket", "GLOSHIA-R2.1-ORT-ANDROID-HARNESS-12");
            report.put("device", new JSONObject()
                    .put("model", Build.MODEL)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT));
            report.put("ort_version", "1.27.0");
            report.put("contract", new JSONObject()
                    .put("input_name", INPUT_NAME)
                    .put("input_shape", new JSONArray(INPUT_SHAPE))
                    .put("input_type", "float32")
                    .put("output_name", "filter_probability")
                    .put("threshold", 0.4));

            File candidate = new File(getFilesDir(), CANDIDATE_NAME);
            File r1 = new File(getFilesDir(), R1_NAME);
            report.put("candidate", fileIdentity(candidate, CANDIDATE_SHA256));
            report.put("r1", fileIdentity(r1, R1_SHA256));

            JSONObject candidateSmoke = runModel(candidate, null, null, true);
            report.put("candidate_smoke", candidateSmoke);
            if (!candidateSmoke.optBoolean("session_opened", false)
                    || !candidateSmoke.optBoolean("finite_outputs", false)) {
                report.put("status", "NO-GO");
                report.put("gate_failure", "candidate_smoke_failed");
                writeReport(report);
                return;
            }

            JSONObject r1Smoke = runModel(r1, null, null, true);
            report.put("r1_smoke", r1Smoke);

            File metadataFile = new File(getFilesDir(), "evaluation-metadata.json");
            File tensorFile = new File(getFilesDir(), "evaluation-inputs.bin");
            if (metadataFile.isFile() && tensorFile.isFile()) {
                JSONObject metadata = new JSONObject(readText(metadataFile));
                JSONArray samples = metadata.getJSONArray("samples");
                JSONObject candidateEvaluation = evaluateFrozen(candidate, tensorFile, samples);
                JSONObject r1Evaluation = evaluateFrozen(r1, tensorFile, samples);
                report.put("candidate_evaluation", candidateEvaluation);
                report.put("r1_evaluation", r1Evaluation);
                report.put("evaluation_metadata_sha256", sha256(metadataFile));
                report.put("evaluation_input_sha256", sha256(tensorFile));
                report.put("final_sealed_opened", false);
                boolean candidatePassed = candidateEvaluation.optBoolean("finite_outputs", false)
                        && candidateEvaluation.optInt("false_permissions", 1) == 0
                        && candidateEvaluation.optInt("decision_mismatches_vs_fp32", 1) == 0;
                report.put("status", candidatePassed ? "CONDITIONAL-GO-NO-CANARY" : "NO-GO");
            } else {
                report.put("status", "SMOKE-ONLY");
                report.put("full_evaluation", "not_run; input tensors absent");
            }
            writeReport(report);
        } catch (Throwable error) {
            try {
                report.put("status", "NO-GO");
                report.put("harness_error", error.toString());
                writeReport(report);
            } catch (Exception ignored) {
                Log.e(TAG, "could not write failure report", error);
            }
        }
    }

    private JSONObject runModel(File model, float[] input, JSONObject ignored, boolean synthetic)
            throws Exception {
        JSONObject result = new JSONObject();
        result.put("model", model.getName());
        result.put("session_opened", false);
        result.put("finite_outputs", false);
        result.put("synthetic", synthetic);
        if (!model.isFile()) {
            result.put("error", "model_missing");
            return result;
        }
        try (OrtEnvironment environment = OrtEnvironment.getEnvironment();
             OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            try (OrtSession session = environment.createSession(model.getAbsolutePath(), options)) {
                result.put("session_opened", true);
                List<Double> timings = new ArrayList<>();
                boolean finite = true;
                float[] pixels = input == null ? syntheticInput(0) : input;
                for (int warmup = 0; warmup < 5; warmup++) infer(environment, session, pixels);
                for (int run = 0; run < SYNTHETIC_RUNS; run++) {
                    long started = System.nanoTime();
                    float probability = infer(environment, session, pixels);
                    timings.add((System.nanoTime() - started) / 1_000_000.0);
                    finite = finite && Float.isFinite(probability);
                }
                Collections.sort(timings);
                result.put("finite_outputs", finite);
                result.put("synthetic_probability", infer(environment, session, pixels));
                result.put("latency_ms", latency(timings));
                result.put("memory", memory());
                result.put("temperature_c", batteryTemperature());
            }
            result.put("session_closed", true);
        } catch (Throwable error) {
            result.put("error_type", error.getClass().getName());
            result.put("error", error.toString());
            result.put("session_closed", true);
        }
        return result;
    }

    private JSONObject evaluateFrozen(File model, File tensorFile, JSONArray samples) throws Exception {
        JSONObject result = new JSONObject();
        List<Double> timings = new ArrayList<>();
        int allowAsAllow = 0;
        int allowAsFilter = 0;
        int filterAsAllow = 0;
        int filterAsFilter = 0;
        int decisionMismatches = 0;
        int probabilityMismatches = 0;
        double totalProbabilityDelta = 0.0;
        double maxProbabilityDelta = 0.0;
        boolean finite = true;
        long peakPss = 0;
        JSONArray mismatchSamples = new JSONArray();
        JSONObject bySplit = new JSONObject();
        try (OrtEnvironment environment = OrtEnvironment.getEnvironment();
             OrtSession.SessionOptions options = new OrtSession.SessionOptions();
             OrtSession session = createSession(environment, options, model);
             BufferedInputStream input = new BufferedInputStream(new FileInputStream(tensorFile))) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            ByteBuffer bytes = ByteBuffer.allocate(INPUT_FLOATS * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < samples.length(); index++) {
                bytes.clear();
                readFully(input, bytes.array());
                bytes.rewind();
                float[] pixels = new float[INPUT_FLOATS];
                bytes.asFloatBuffer().get(pixels);
                long started = System.nanoTime();
                float probability = infer(environment, session, pixels);
                timings.add((System.nanoTime() - started) / 1_000_000.0);
                finite = finite && Float.isFinite(probability);
                peakPss = Math.max(peakPss, memory().optLong("total_pss_kb", 0));
                JSONObject sample = samples.getJSONObject(index);
                String human = sample.getString("human_action");
                String action = probability >= 0.4 ? "filter" : "allow";
                String expected = sample.getString("fp32_action");
                double probabilityDelta = Math.abs(probability - sample.getDouble("fp32_probability"));
                totalProbabilityDelta += probabilityDelta;
                maxProbabilityDelta = Math.max(maxProbabilityDelta, probabilityDelta);
                if (!action.equals(expected)) {
                    decisionMismatches++;
                    mismatchSamples.put(new JSONObject()
                            .put("sample_id", sample.getString("sample_id"))
                            .put("split", sample.getString("split"))
                            .put("human_action", human)
                            .put("fp32_action", expected)
                            .put("android_action", action)
                            .put("fp32_probability", sample.getDouble("fp32_probability"))
                            .put("android_probability", probability));
                }
                if (probabilityDelta > 0.0001) {
                    probabilityMismatches++;
                }
                if ("allow".equals(human) && "allow".equals(action)) allowAsAllow++;
                if ("allow".equals(human) && "filter".equals(action)) allowAsFilter++;
                if ("filter".equals(human) && "allow".equals(action)) filterAsAllow++;
                if ("filter".equals(human) && "filter".equals(action)) filterAsFilter++;
                JSONObject splitResult = bySplit.optJSONObject(sample.getString("split"));
                if (splitResult == null) {
                    splitResult = new JSONObject()
                            .put("samples", 0)
                            .put("allow_as_allow", 0)
                            .put("allow_as_filter", 0)
                            .put("filter_as_allow", 0)
                            .put("filter_as_filter", 0)
                            .put("decision_mismatches_vs_fp32", 0);
                    bySplit.put(sample.getString("split"), splitResult);
                }
                splitResult.put("samples", splitResult.optInt("samples") + 1);
                if ("allow".equals(human) && "allow".equals(action)) {
                    splitResult.put("allow_as_allow", splitResult.optInt("allow_as_allow") + 1);
                }
                if ("allow".equals(human) && "filter".equals(action)) {
                    splitResult.put("allow_as_filter", splitResult.optInt("allow_as_filter") + 1);
                }
                if ("filter".equals(human) && "allow".equals(action)) {
                    splitResult.put("filter_as_allow", splitResult.optInt("filter_as_allow") + 1);
                }
                if ("filter".equals(human) && "filter".equals(action)) {
                    splitResult.put("filter_as_filter", splitResult.optInt("filter_as_filter") + 1);
                }
                if (!action.equals(expected)) {
                    splitResult.put("decision_mismatches_vs_fp32",
                            splitResult.optInt("decision_mismatches_vs_fp32") + 1);
                }
            }
        }
        Collections.sort(timings);
        result.put("samples", samples.length());
        result.put("finite_outputs", finite);
        result.put("decision_mismatches_vs_fp32", decisionMismatches);
        result.put("probability_mismatches_over_1e-4", probabilityMismatches);
        result.put("mean_abs_probability_delta", totalProbabilityDelta / samples.length());
        result.put("max_abs_probability_delta", maxProbabilityDelta);
        result.put("decision_mismatch_samples", mismatchSamples);
        result.put("by_split", bySplit);
        result.put("confusion_matrix", new JSONObject()
                .put("allow_as_allow", allowAsAllow)
                .put("allow_as_filter", allowAsFilter)
                .put("filter_as_allow", filterAsAllow)
                .put("filter_as_filter", filterAsFilter));
        result.put("false_permissions", filterAsAllow);
        result.put("false_filters", allowAsFilter);
        result.put("latency_ms", latency(timings));
        result.put("peak_total_pss_kb", peakPss);
        result.put("temperature_c", batteryTemperature());
        result.put("session_closed", true);
        return result;
    }

    private OrtSession createSession(OrtEnvironment environment, OrtSession.SessionOptions options, File model)
            throws Exception {
        options.setIntraOpNumThreads(2);
        options.setInterOpNumThreads(1);
        return environment.createSession(model.getAbsolutePath(), options);
    }

    private float infer(OrtEnvironment environment, OrtSession session, float[] pixels) throws Exception {
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixels), INPUT_SHAPE);
             OrtSession.Result output = session.run(Collections.singletonMap(INPUT_NAME, tensor))) {
            Object value = output.get(0).getValue();
            if (!(value instanceof float[][])) throw new IllegalStateException("unexpected_output_type");
            return ((float[][]) value)[0][0];
        }
    }

    private float[] syntheticInput(int kind) {
        float[] pixels = new float[INPUT_FLOATS];
        if (kind == 1) {
            java.util.Arrays.fill(pixels, 0.5f);
        } else if (kind == 2) {
            for (int index = 0; index < pixels.length; index++) {
                pixels[index] = (float) Math.sin(index * 0.001);
            }
        }
        return pixels;
    }

    private JSONObject fileIdentity(File file, String expected) throws Exception {
        return new JSONObject()
                .put("path", file.getAbsolutePath())
                .put("exists", file.isFile())
                .put("bytes", file.isFile() ? file.length() : 0)
                .put("sha256", file.isFile() ? sha256(file) : JSONObject.NULL)
                .put("expected_sha256", expected)
                .put("hash_matches", file.isFile() && expected.equals(sha256(file)));
    }

    private JSONObject memory() throws Exception {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return new JSONObject()
                .put("total_pss_kb", info.getTotalPss())
                .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize());
    }

    private Double batteryTemperature() {
        android.content.Intent intent = registerReceiver(null,
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return null;
        int tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        return tenths == Integer.MIN_VALUE ? null : tenths / 10.0;
    }

    private JSONObject latency(List<Double> values) throws Exception {
        if (values.isEmpty()) return new JSONObject();
        return new JSONObject()
                .put("runs", values.size())
                .put("p50_ms", percentile(values, 0.50))
                .put("p95_ms", percentile(values, 0.95))
                .put("max_ms", values.get(values.size() - 1));
    }

    private double percentile(List<Double> values, double fraction) {
        int index = Math.max(0, Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1));
        return values.get(index);
    }

    private static void readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = input.read(target, offset, target.length - offset);
            if (read < 0) throw new IOException("unexpected_eof");
            offset += read;
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            readFully(input, data);
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void writeReport(JSONObject report) throws Exception {
        File target = new File(getFilesDir(), "result.json");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Log.i(TAG, report.toString());
        runOnUiThread(() -> statusView.setText(report.toString()));
    }
}
