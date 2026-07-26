package com.contentfilter.dag2.benchmark;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class BenchmarkActivity extends Activity {
    private static final String TAG = "DagV2Benchmark";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(48, 48, 48, 48);
        status.setText("DAG v2 benchmark 04A\nCPU, imágenes locales\nIniciando…");
        setContentView(status);
        executor.execute(this::runBenchmark);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runBenchmark() {
        try {
            List<String> files = new ArrayList<>(Arrays.asList(getAssets().list("corpus")));
            files.remove("manifest.json");
            Collections.sort(files);
            if (files.size() < 50 || files.size() > 100) {
                throw new IllegalStateException("invalid_subset_count");
            }
            Expectations expectations = loadExpectations();
            long started = elapsedNanos();
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            OrtSession adult = environment.createSession(readAsset("models/nsfw_marqo_vit_tiny_384.onnx"), sessionOptions);
            double adultLoad = elapsedMs(started);
            started = elapsedNanos();
            OrtSession warmAdult =
                    environment.createSession(readAsset("models/nsfw_marqo_vit_tiny_384.onnx"), sessionOptions);
            double adultWarmLoad = elapsedMs(started);
            warmAdult.close();

            started = elapsedNanos();
            PoseLandmarker pose = createCpuPose();
            double poseLoad = elapsedMs(started);
            started = elapsedNanos();
            PoseLandmarker warmPose = createCpuPose();
            double poseWarmLoad = elapsedMs(started);
            warmPose.close();

            started = elapsedNanos();
            ImageSegmenter segmenter = createCpuSegmenter();
            double segmentLoad = elapsedMs(started);
            started = elapsedNanos();
            ImageSegmenter warmSegmenter = createCpuSegmenter();
            double segmentWarmLoad = elapsedMs(started);
            warmSegmenter.close();

            List<Double> adultMs = new ArrayList<>();
            List<Double> poseMs = new ArrayList<>();
            List<Double> segmentMs = new ArrayList<>();
            List<Double> adultParityDifferences = new ArrayList<>();
            List<String> adultParity = new ArrayList<>();
            List<String> poseParity = new ArrayList<>();
            int adultRoundedMatches = 0;
            int poseCountMatches = 0;
            int responses = 0;
            for (String file : files) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                Bitmap source;
                try (InputStream input = getAssets().open("corpus/" + file)) {
                    source = BitmapFactory.decodeStream(input);
                }
                if (source == null) {
                    continue;
                }
                started = elapsedNanos();
                double adultScore = runAdult(environment, adult, source);
                adultMs.add(elapsedMs(started));
                adultParity.add(String.format(Locale.US, "%s:%.3f\n", file, adultScore));
                double adultDifference = Math.abs(adultScore - expectations.adultScores.get(file));
                adultParityDifferences.add(adultDifference);
                if (Math.round(adultScore * 1000.0)
                        == Math.round(expectations.adultScores.get(file) * 1000.0)) {
                    adultRoundedMatches++;
                }

                MPImage image = new BitmapImageBuilder(source).build();
                started = elapsedNanos();
                PoseLandmarkerResult poseResult = pose.detect(image);
                poseMs.add(elapsedMs(started));
                poseParity.add(String.format(Locale.US, "%s:%d\n", file, poseResult.landmarks().size()));
                if (poseResult.landmarks().size() == expectations.personCounts.get(file)) {
                    poseCountMatches++;
                }

                started = elapsedNanos();
                ImageSegmenterResult segmentResult = segmenter.segment(image);
                if (segmentResult.categoryMask().isPresent()) {
                    responses++;
                }
                segmentMs.add(elapsedMs(started));
                image.close();
                source.recycle();
            }
            int pssKb = currentPssKb();
            int thermal = ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
            logLoads("adult_cpu", adultLoad, adultWarmLoad);
            logLoads("pose_cpu", poseLoad, poseWarmLoad);
            logLoads("segment_cpu", segmentLoad, segmentWarmLoad);
            logSummary("adult_cpu", files.size(), adultLoad, adultMs, pssKb, thermal);
            logSummary("pose_cpu", files.size(), poseLoad, poseMs, pssKb, thermal);
            logSummary("segment_cpu", files.size(), segmentLoad, segmentMs, pssKb, thermal);
            logParity("adult_score_3dp", adultParity);
            logParity("pose_count", poseParity);
            logParitySummary(
                    adultParityDifferences,
                    adultRoundedMatches,
                    poseCountMatches,
                    files.size());
            segmenter.close();
            pose.close();
            adult.close();
            sessionOptions.close();

            runAccelerated(files, environment);
            pssKb = currentPssKb();
            thermal = ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
            Log.i(TAG, String.format(
                    Locale.US,
                    "{\"event\":\"complete\",\"samples\":%d,\"segment_responses\":%d,\"pss_kb\":%d,\"thermal_status\":%d}",
                    files.size(), responses, pssKb, thermal));
            runOnUiThread(() -> status.setText("Benchmark completo\n" + files.size() + " imágenes\nResultados sanitizados en logcat"));
        } catch (Throwable error) {
            Log.e(TAG, "{\"event\":\"failed\",\"reason\":\"" + sanitize(error.getClass().getSimpleName()) + "\"}");
            runOnUiThread(() -> status.setText("Benchmark falló\n" + error.getClass().getSimpleName()));
        }
    }

    private PoseLandmarker createCpuPose() {
        return PoseLandmarker.createFromOptions(
                this,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(
                                BaseOptions.builder()
                                        .setModelAssetPath("models/pose_landmarker_lite.task")
                                        .build())
                        .setRunningMode(RunningMode.IMAGE)
                        .setNumPoses(4)
                        .setMinPoseDetectionConfidence(0.25f)
                        .setMinPosePresenceConfidence(0.25f)
                        .build());
    }

    private ImageSegmenter createCpuSegmenter() {
        return ImageSegmenter.createFromOptions(
                this,
                ImageSegmenter.ImageSegmenterOptions.builder()
                        .setBaseOptions(
                                BaseOptions.builder()
                                        .setModelAssetPath("models/selfie_multiclass_256x256.tflite")
                                        .build())
                        .setRunningMode(RunningMode.IMAGE)
                        .setOutputCategoryMask(true)
                        .setOutputConfidenceMasks(false)
                        .build());
    }

    private Expectations loadExpectations() throws Exception {
        JSONObject root =
                new JSONObject(new String(readAsset("corpus/manifest.json"), StandardCharsets.UTF_8));
        JSONArray samples = root.getJSONArray("samples");
        Map<String, Double> adultScores = new HashMap<>();
        Map<String, Integer> personCounts = new HashMap<>();
        for (int index = 0; index < samples.length(); index++) {
            JSONObject sample = samples.getJSONObject(index);
            String file = sample.getString("file");
            adultScores.put(file, sample.getDouble("expected_adult_score"));
            personCounts.put(file, sample.getInt("expected_person_count"));
        }
        return new Expectations(adultScores, personCounts);
    }

    private void runAccelerated(List<String> files, OrtEnvironment environment) {
        try {
            long started = elapsedNanos();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.addNnapi();
            OrtSession session =
                    environment.createSession(readAsset("models/nsfw_marqo_vit_tiny_384.onnx"), options);
            double loadMs = elapsedMs(started);
            List<Double> values = new ArrayList<>();
            for (String file : files) {
                Bitmap bitmap = loadBitmap(file);
                started = elapsedNanos();
                runAdult(environment, session, bitmap);
                values.add(elapsedMs(started));
                bitmap.recycle();
            }
            logSummary(
                    "adult_nnapi",
                    files.size(),
                    loadMs,
                    values,
                    currentPssKb(),
                    ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus());
            session.close();
            options.close();
        } catch (Throwable error) {
            logDelegateFailure("adult_nnapi", error);
        }

        try {
            long started = elapsedNanos();
            PoseLandmarker pose = PoseLandmarker.createFromOptions(
                    this,
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(
                                    BaseOptions.builder()
                                            .setModelAssetPath("models/pose_landmarker_lite.task")
                                            .setDelegate(Delegate.GPU)
                                            .build())
                            .setRunningMode(RunningMode.IMAGE)
                            .setNumPoses(4)
                            .setMinPoseDetectionConfidence(0.25f)
                            .setMinPosePresenceConfidence(0.25f)
                            .build());
            double poseLoad = elapsedMs(started);
            started = elapsedNanos();
            ImageSegmenter segmenter = ImageSegmenter.createFromOptions(
                    this,
                    ImageSegmenter.ImageSegmenterOptions.builder()
                            .setBaseOptions(
                                    BaseOptions.builder()
                                            .setModelAssetPath("models/selfie_multiclass_256x256.tflite")
                                            .setDelegate(Delegate.GPU)
                                            .build())
                            .setRunningMode(RunningMode.IMAGE)
                            .setOutputCategoryMask(true)
                            .setOutputConfidenceMasks(false)
                            .build());
            double segmentLoad = elapsedMs(started);
            List<Double> poseMs = new ArrayList<>();
            List<Double> segmentMs = new ArrayList<>();
            for (String file : files) {
                Bitmap bitmap = loadBitmap(file);
                MPImage image = new BitmapImageBuilder(bitmap).build();
                started = elapsedNanos();
                pose.detect(image);
                poseMs.add(elapsedMs(started));
                started = elapsedNanos();
                segmenter.segment(image);
                segmentMs.add(elapsedMs(started));
                image.close();
                bitmap.recycle();
            }
            int pssKb = currentPssKb();
            int thermal = ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
            logSummary("pose_gpu", files.size(), poseLoad, poseMs, pssKb, thermal);
            logSummary("segment_gpu", files.size(), segmentLoad, segmentMs, pssKb, thermal);
            segmenter.close();
            pose.close();
        } catch (Throwable error) {
            logDelegateFailure("mediapipe_gpu", error);
        }
    }

    private Bitmap loadBitmap(String file) throws Exception {
        try (InputStream input = getAssets().open("corpus/" + file)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IllegalArgumentException("decode_failed");
            }
            return bitmap;
        }
    }

    private static void logDelegateFailure(String stage, Throwable error) {
        Log.i(TAG, "{\"event\":\"delegate_unavailable\",\"stage\":\""
                + stage + "\",\"reason\":\"" + sanitize(error.getClass().getSimpleName()) + "\"}");
    }

    private static final class Expectations {
        private final Map<String, Double> adultScores;
        private final Map<String, Integer> personCounts;

        private Expectations(
                Map<String, Double> adultScores,
                Map<String, Integer> personCounts) {
            this.adultScores = adultScores;
            this.personCounts = personCounts;
        }
    }

    private double runAdult(OrtEnvironment environment, OrtSession session, Bitmap bitmap) throws Exception {
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap crop = Bitmap.createBitmap(
                bitmap,
                (bitmap.getWidth() - side) / 2,
                (bitmap.getHeight() - side) / 2,
                side,
                side);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, 384, 384, true);
        if (crop != bitmap && crop != scaled) {
            crop.recycle();
        }
        int[] pixels = new int[384 * 384];
        scaled.getPixels(pixels, 0, 384, 0, 0, 384, 384);
        if (scaled != bitmap) {
            scaled.recycle();
        }
        float[] values = new float[384 * 384 * 3];
        int plane = 384 * 384;
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            values[index] = (((pixel >> 16) & 0xff) / 127.5f) - 1f;
            values[plane + index] = (((pixel >> 8) & 0xff) / 127.5f) - 1f;
            values[plane * 2 + index] = ((pixel & 0xff) / 127.5f) - 1f;
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                     environment,
                     FloatBuffer.wrap(values),
                     new long[] {1, 3, 384, 384});
             OrtSession.Result result = session.run(Collections.singletonMap("pixel_values", tensor))) {
            float[][] logits = (float[][]) result.get(0).getValue();
            double maximum = Math.max(logits[0][0], logits[0][1]);
            double first = Math.exp(logits[0][0] - maximum);
            double second = Math.exp(logits[0][1] - maximum);
            return first / (first + second);
        }
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static long elapsedNanos() {
        return android.os.SystemClock.elapsedRealtimeNanos();
    }

    private static double elapsedMs(long started) {
        return (elapsedNanos() - started) / 1_000_000.0;
    }

    private int currentPssKb() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        Debug.MemoryInfo[] infos = manager.getProcessMemoryInfo(new int[] {android.os.Process.myPid()});
        return infos.length == 0 ? -1 : infos[0].getTotalPss();
    }

    private static void logLoads(String stage, double coldMs, double warmMs) {
        Log.i(TAG, String.format(
                Locale.US,
                "{\"event\":\"model_load\",\"stage\":\"%s\",\"cold_ms\":%.3f,\"warm_ms\":%.3f}",
                stage,
                coldMs,
                warmMs));
    }

    private static void logSummary(
            String stage,
            int samples,
            double loadMs,
            List<Double> values,
            int pssKb,
            int thermal) {
        Collections.sort(values);
        Log.i(TAG, String.format(
                Locale.US,
                "{\"event\":\"stage\",\"stage\":\"%s\",\"samples\":%d,\"load_ms\":%.3f,"
                    + "\"p50_ms\":%.3f,\"p95_ms\":%.3f,\"max_ms\":%.3f,\"pss_kb\":%d,\"thermal_status\":%d}",
                stage,
                samples,
                loadMs,
                percentile(values, 0.50),
                percentile(values, 0.95),
                values.isEmpty() ? 0.0 : values.get(values.size() - 1),
                pssKb,
                thermal));
    }

    private static void logParity(String stage, List<String> values) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String value : values) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder output = new StringBuilder();
        for (byte value : digest.digest()) {
            output.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        Log.i(TAG, "{\"event\":\"parity\",\"stage\":\"" + stage
                + "\",\"sha256\":\"" + output + "\"}");
    }

    private static void logParitySummary(
            List<Double> adultDifferences,
            int adultRoundedMatches,
            int poseCountMatches,
            int sampleCount) {
        double total = 0.0;
        double maximum = 0.0;
        for (double difference : adultDifferences) {
            total += difference;
            maximum = Math.max(maximum, difference);
        }
        double mean = adultDifferences.isEmpty() ? 0.0 : total / adultDifferences.size();
        Log.i(TAG, String.format(
                Locale.US,
                "{\"event\":\"parity_summary\",\"samples\":%d,\"adult_mae\":%.6f,"
                        + "\"adult_max_abs\":%.6f,\"adult_rounded_3dp_matches\":%d,"
                        + "\"pose_count_matches\":%d,\"segment_response_matches\":%d}",
                sampleCount,
                mean,
                maximum,
                adultRoundedMatches,
                poseCountMatches,
                sampleCount));
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int index = Math.max(0, Math.min(values.size() - 1, (int) Math.ceil(fraction * values.size()) - 1));
        return values.get(index);
    }

    private static String sanitize(String text) {
        String sanitized = text.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }
}
