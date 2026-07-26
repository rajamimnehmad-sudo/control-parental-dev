package com.contentfilter.dag2.benchmark;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TargetedSignalActivity extends Activity {
    private static final String TAG = "DagV2Targeted04B";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService parallel = Executors.newFixedThreadPool(2);
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setPadding(48, 48, 48, 48);
        status.setText("DAG v2 · señales dirigidas 04B\nCPU, sin segmentación universal\nIniciando…");
        setContentView(status);
        worker.execute(this::runBenchmark);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        parallel.shutdownNow();
        super.onDestroy();
    }

    private void runBenchmark() {
        try {
            JSONObject manifest =
                    new JSONObject(new String(readAsset("corpus/manifest.json"), StandardCharsets.UTF_8));
            JSONArray samples = manifest.getJSONArray("samples");
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            OrtSession adult =
                    environment.createSession(
                            readAsset("models/nsfw_marqo_vit_tiny_384.onnx"), options);
            PoseLandmarker pose = createPose();
            List<Double> adultTimes = new ArrayList<>();
            List<Double> poseTimes = new ArrayList<>();
            List<Double> localTimes = new ArrayList<>();
            List<Double> policyTimes = new ArrayList<>();
            List<Double> sequentialTimes = new ArrayList<>();
            List<Double> parallelTimes = new ArrayList<>();
            JSONArray records = new JSONArray();
            long cpuStarted = SystemClock.currentThreadTimeMillis();
            int failures = 0;
            for (int index = 0; index < samples.length(); index++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                JSONObject sample = samples.getJSONObject(index);
                Bitmap bitmap = loadBitmap(sample.getString("file"));
                long started = elapsedNanos();
                double adultScore = runAdult(environment, adult, bitmap);
                double adultMs = elapsedMs(started);
                adultTimes.add(adultMs);
                started = elapsedNanos();
                PoseLandmarkerResult poseResult = detect(pose, bitmap);
                double poseMs = elapsedMs(started);
                poseTimes.add(poseMs);
                started = elapsedNanos();
                LocalSignals signals = localSignals(bitmap, poseResult);
                double localMs = elapsedMs(started);
                localTimes.add(localMs);
                started = elapsedNanos();
                double policyScore = boundedPolicy(signals, adultScore, poseResult.landmarks().size());
                double policyMs = elapsedMs(started);
                policyTimes.add(policyMs);
                sequentialTimes.add(adultMs + poseMs + localMs + policyMs);

                started = elapsedNanos();
                Future<Double> adultFuture =
                        parallel.submit(() -> runAdult(environment, adult, bitmap));
                Future<PoseLandmarkerResult> poseFuture =
                        parallel.submit(() -> detect(pose, bitmap));
                adultFuture.get();
                PoseLandmarkerResult parallelPose = poseFuture.get();
                localSignals(bitmap, parallelPose);
                boundedPolicy(signals, adultScore, parallelPose.landmarks().size());
                parallelTimes.add(elapsedMs(started));

                JSONObject record = new JSONObject();
                record.put("sample_id", sample.getString("sample_id"));
                record.put("adult_score", adultScore);
                record.put("person_count", poseResult.landmarks().size());
                record.put("pose_confidence", signals.poseConfidence);
                record.put("shoulder_skin", signals.shoulderSkin);
                record.put("elbow_skin", signals.elbowSkin);
                record.put("knee_skin", signals.kneeSkin);
                record.put("torso_skin", signals.torsoSkin);
                record.put("signal_uncertainty", signals.uncertainty);
                record.put("policy_upper_bound_score", policyScore);
                records.put(record);
                bitmap.recycle();
            }
            long cpuMs = SystemClock.currentThreadTimeMillis() - cpuStarted;
            int pssKb = currentPssKb();
            int thermal = ((PowerManager) getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
            int batteryTenthsCelsius = batteryTemperature();
            logStage("adult", adultTimes);
            logStage("pose", poseTimes);
            logStage("local_signals", localTimes);
            logStage("policy_upper_bound", policyTimes);
            logStage("sequential", sequentialTimes);
            logStage("adult_pose_parallel", parallelTimes);
            JSONObject output = new JSONObject();
            output.put("schema_version", 1);
            output.put("sample_count", samples.length());
            output.put("runtime", "CPU");
            output.put("nnapi_used", false);
            output.put("heavy_segmentation_percent", 0.0);
            output.put("cpu_time_ms", cpuMs);
            output.put("pss_kb", pssKb);
            output.put("thermal_status", thermal);
            output.put("battery_temperature_tenths_c", batteryTenthsCelsius);
            output.put("failures", failures);
            output.put("records", records);
            writePrivateResult(output);
            Log.i(
                    TAG,
                    String.format(
                            Locale.US,
                            "{\"event\":\"complete\",\"samples\":%d,\"pss_kb\":%d,"
                                    + "\"cpu_ms\":%d,\"thermal_status\":%d,"
                                    + "\"battery_temperature_tenths_c\":%d,\"failures\":%d}",
                            samples.length(),
                            pssKb,
                            cpuMs,
                            thermal,
                            batteryTenthsCelsius,
                            failures));
            pose.close();
            adult.close();
            options.close();
            runOnUiThread(
                    () ->
                            status.setText(
                                    "Señales 04B completas\n"
                                            + samples.length()
                                            + " imágenes\nSin segmentación universal"));
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "{\"event\":\"failed\",\"reason\":\""
                            + sanitize(error.getClass().getSimpleName())
                            + "\"}");
            runOnUiThread(
                    () ->
                            status.setText(
                                    "Señales 04B fallaron\n"
                                            + sanitize(error.getClass().getSimpleName())));
        }
    }

    private PoseLandmarker createPose() {
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

    private PoseLandmarkerResult detect(PoseLandmarker pose, Bitmap bitmap) {
        MPImage image = new BitmapImageBuilder(bitmap).build();
        try {
            return pose.detect(image);
        } finally {
            image.close();
        }
    }

    private static LocalSignals localSignals(
            Bitmap source,
            PoseLandmarkerResult result) {
        int maximum = Math.max(source.getWidth(), source.getHeight());
        double scale = maximum > 512 ? 512.0 / maximum : 1.0;
        Bitmap bitmap =
                scale < 1.0
                        ? Bitmap.createScaledBitmap(
                                source,
                                Math.max(1, (int) (source.getWidth() * scale)),
                                Math.max(1, (int) (source.getHeight() * scale)),
                                true)
                        : source;
        int radius = Math.max(3, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 28);
        List<Double> shoulders = new ArrayList<>();
        List<Double> elbows = new ArrayList<>();
        List<Double> knees = new ArrayList<>();
        List<Double> torsos = new ArrayList<>();
        List<Double> confidences = new ArrayList<>();
        for (List<NormalizedLandmark> person : result.landmarks()) {
            collectPatches(bitmap, person, new int[] {11, 12}, radius, shoulders, confidences);
            collectPatches(bitmap, person, new int[] {13, 14}, radius, elbows, confidences);
            collectPatches(bitmap, person, new int[] {25, 26}, radius, knees, confidences);
            torsos.add(torsoSkin(bitmap, person));
        }
        double shoulder = mean(shoulders);
        double elbow = mean(elbows);
        double knee = mean(knees);
        double torso = mean(torsos);
        double confidence = mean(confidences);
        double disagreement =
                standardDeviation(Arrays.asList(shoulder, elbow, knee, torso));
        if (bitmap != source) {
            bitmap.recycle();
        }
        return new LocalSignals(
                shoulder,
                elbow,
                knee,
                torso,
                confidence,
                Math.min(1.0, (1.0 - confidence) * 0.6 + disagreement * 0.4));
    }

    private static void collectPatches(
            Bitmap bitmap,
            List<NormalizedLandmark> person,
            int[] indexes,
            int radius,
            List<Double> output,
            List<Double> confidences) {
        for (int index : indexes) {
            NormalizedLandmark landmark = person.get(index);
            double confidence =
                    Math.min(
                            landmark.visibility().orElse(0.0f),
                            landmark.presence().orElse(0.0f));
            confidences.add(confidence);
            if (confidence >= 0.25) {
                output.add(
                        skinRatio(
                                bitmap,
                                (int) (landmark.x() * bitmap.getWidth()),
                                (int) (landmark.y() * bitmap.getHeight()),
                                radius));
            }
        }
    }

    private static double torsoSkin(Bitmap bitmap, List<NormalizedLandmark> person) {
        int[] indexes = {11, 12, 23, 24};
        int left = bitmap.getWidth();
        int right = 0;
        int top = bitmap.getHeight();
        int bottom = 0;
        int valid = 0;
        for (int index : indexes) {
            NormalizedLandmark landmark = person.get(index);
            double confidence =
                    Math.min(
                            landmark.visibility().orElse(0.0f),
                            landmark.presence().orElse(0.0f));
            if (confidence < 0.25) {
                continue;
            }
            int x = Math.max(0, Math.min(bitmap.getWidth() - 1, (int) (landmark.x() * bitmap.getWidth())));
            int y = Math.max(0, Math.min(bitmap.getHeight() - 1, (int) (landmark.y() * bitmap.getHeight())));
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
            valid++;
        }
        if (valid < 4 || right <= left || bottom <= top) {
            return 0.0;
        }
        int skin = 0;
        int total = 0;
        int stride = Math.max(1, Math.min(right - left, bottom - top) / 32);
        for (int y = top; y <= bottom; y += stride) {
            for (int x = left; x <= right; x += stride) {
                skin += skinVotes(bitmap.getPixel(x, y)) >= 2 ? 1 : 0;
                total++;
            }
        }
        return total == 0 ? 0.0 : skin / (double) total;
    }

    private static double skinRatio(Bitmap bitmap, int centerX, int centerY, int radius) {
        int left = Math.max(0, centerX - radius);
        int right = Math.min(bitmap.getWidth() - 1, centerX + radius);
        int top = Math.max(0, centerY - radius);
        int bottom = Math.min(bitmap.getHeight() - 1, centerY + radius);
        int skin = 0;
        int total = 0;
        for (int y = top; y <= bottom; y += 2) {
            for (int x = left; x <= right; x += 2) {
                skin += skinVotes(bitmap.getPixel(x, y)) >= 2 ? 1 : 0;
                total++;
            }
        }
        return total == 0 ? 0.0 : skin / (double) total;
    }

    private static int skinVotes(int pixel) {
        double red = (pixel >> 16) & 0xff;
        double green = (pixel >> 8) & 0xff;
        double blue = pixel & 0xff;
        double y = 0.299 * red + 0.587 * green + 0.114 * blue;
        double cb = 128 - 0.168736 * red - 0.331264 * green + 0.5 * blue;
        double cr = 128 + 0.5 * red - 0.418688 * green - 0.081312 * blue;
        boolean ycbcr = y > 35 && cb >= 77 && cb <= 127 && cr >= 133 && cr <= 173;
        double maximum = Math.max(red, Math.max(green, blue));
        double minimum = Math.min(red, Math.min(green, blue));
        double saturation = maximum > 0 ? (maximum - minimum) / maximum : 0;
        boolean hsv = maximum >= 50 && saturation >= 0.12 && saturation <= 0.75 && red >= green * 0.85;
        double light = (maximum + minimum) / 5.10;
        double aProxy = red - green;
        double bProxy = (red + green) / 2.0 - blue;
        boolean lab = light > 20 && light < 95 && aProxy > 5 && aProxy < 90 && bProxy > 5 && bProxy < 100;
        return (ycbcr ? 1 : 0) + (hsv ? 1 : 0) + (lab ? 1 : 0);
    }

    private static double boundedPolicy(
            LocalSignals signals,
            double adultScore,
            int personCount) {
        double[] features = {
            adultScore,
            Math.min(1.0, personCount / 4.0),
            signals.poseConfidence,
            signals.shoulderSkin,
            signals.elbowSkin,
            signals.kneeSkin,
            signals.torsoSkin,
            signals.uncertainty
        };
        double score = -0.4;
        for (int index = 0; index < 12; index++) {
            double value = features[index % features.length];
            score += value > (0.1 + (index % 5) * 0.15) ? 0.08 : -0.02;
        }
        return 1.0 / (1.0 + Math.exp(-score));
    }

    private double runAdult(
            OrtEnvironment environment,
            OrtSession session,
            Bitmap bitmap)
            throws Exception {
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap crop =
                Bitmap.createBitmap(
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
        try (OnnxTensor tensor =
                        OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.wrap(values),
                                new long[] {1, 3, 384, 384});
             OrtSession.Result result =
                        session.run(Collections.singletonMap("pixel_values", tensor))) {
            float[][] logits = (float[][]) result.get(0).getValue();
            double maximum = Math.max(logits[0][0], logits[0][1]);
            double first = Math.exp(logits[0][0] - maximum);
            double second = Math.exp(logits[0][1] - maximum);
            return first / (first + second);
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

    private void writePrivateResult(JSONObject value) throws Exception {
        File directory = new File(getNoBackupFilesDir(), "dag-v2-targeted-04b");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("private_storage_unavailable");
        }
        File temporary = new File(directory, "android-signals.json.tmp");
        File target = new File(directory, "android-signals.json");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write((value.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        try {
            java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int currentPssKb() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        Debug.MemoryInfo[] infos =
                manager.getProcessMemoryInfo(new int[] {android.os.Process.myPid()});
        return infos.length == 0 ? -1 : infos[0].getTotalPss();
    }

    private int batteryTemperature() {
        Intent value = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return value == null ? -1 : value.getIntExtra("temperature", -1);
    }

    private static void logStage(String name, List<Double> values) {
        Collections.sort(values);
        Log.i(
                TAG,
                String.format(
                        Locale.US,
                        "{\"event\":\"stage\",\"name\":\"%s\",\"count\":%d,"
                                + "\"p50_ms\":%.3f,\"p95_ms\":%.3f,\"max_ms\":%.3f}",
                        name,
                        values.size(),
                        percentile(values, 0.50),
                        percentile(values, 0.95),
                        values.isEmpty() ? 0.0 : values.get(values.size() - 1)));
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int index =
                Math.max(
                        0,
                        Math.min(
                                values.size() - 1,
                                (int) Math.ceil(values.size() * fraction) - 1));
        return values.get(index);
    }

    private static double mean(List<Double> values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return values.isEmpty() ? 0.0 : total / values.size();
    }

    private static double standardDeviation(List<Double> values) {
        double mean = mean(values);
        double total = 0.0;
        for (double value : values) {
            total += (value - mean) * (value - mean);
        }
        return values.isEmpty() ? 0.0 : Math.sqrt(total / values.size());
    }

    private static long elapsedNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }

    private static double elapsedMs(long started) {
        return (elapsedNanos() - started) / 1_000_000.0;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static final class LocalSignals {
        final double shoulderSkin;
        final double elbowSkin;
        final double kneeSkin;
        final double torsoSkin;
        final double poseConfidence;
        final double uncertainty;

        LocalSignals(
                double shoulderSkin,
                double elbowSkin,
                double kneeSkin,
                double torsoSkin,
                double poseConfidence,
                double uncertainty) {
            this.shoulderSkin = shoulderSkin;
            this.elbowSkin = elbowSkin;
            this.kneeSkin = kneeSkin;
            this.torsoSkin = torsoSkin;
            this.poseConfidence = poseConfidence;
            this.uncertainty = uncertainty;
        }
    }
}
