package com.contentfilter.dag2.policyreviewer;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReviewActivity extends Activity {
    private static final int BUTTON_HEIGHT_DP = 58;
    private static final List<String> REASONS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "adult_or_explicit",
                            "underwear_or_swimwear",
                            "deep_neckline_or_chest",
                            "abdomen",
                            "shoulder_or_armpit",
                            "elbow",
                            "knee",
                            "tight_clothing",
                            "transparency",
                            "age_uncertain",
                            "groups",
                            "other"));
    private static final List<String> REASON_LABELS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "Adulto o explícito",
                            "Ropa interior o traje de baño",
                            "Escote o pecho",
                            "Abdomen",
                            "Hombro o axila",
                            "Codo",
                            "Rodilla",
                            "Ropa ajustada",
                            "Transparencia",
                            "Edad incierta",
                            "Grupo",
                            "Otro"));

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final List<Sample> samples = new ArrayList<>();
    private final List<CheckBox> reasonChecks = new ArrayList<>();
    private ReviewStore store;
    private TextView progress;
    private TextView message;
    private ImageView image;
    private LinearLayout primaryPanel;
    private LinearLayout reasonPanel;
    private Button reasonContinue;
    private Button export;
    private Sample currentSample;
    private int imageGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            loadManifest();
            store =
                    new ReviewStore(
                            new File(getNoBackupFilesDir(), "dag-v2-policy-reviewer-04b"));
            buildUi();
            showStoredPosition();
        } catch (Throwable error) {
            TextView failure = new TextView(this);
            failure.setGravity(Gravity.CENTER);
            failure.setPadding(dp(24), dp(24), dp(24), dp(24));
            failure.setText("No se pudo abrir la evaluación\n" + sanitize(error.getClass().getSimpleName()));
            setContentView(failure);
        }
    }

    @Override
    protected void onDestroy() {
        imageGeneration++;
        imageExecutor.shutdownNow();
        if (image != null) {
            releaseImage();
        }
        super.onDestroy();
    }

    private void loadManifest() throws Exception {
        JSONObject root =
                new JSONObject(new String(readAsset("manifest.json"), StandardCharsets.UTF_8));
        JSONArray values = root.getJSONArray("samples");
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.getJSONObject(index);
            samples.add(
                    new Sample(
                            item.getString("sample_id"),
                            item.getString("file"),
                            item.getString("sha256"),
                            item.getBoolean("diagnostic")));
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("empty_manifest");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(244, 246, 245));

        TextView title = new TextView(this);
        title.setText("Evaluación humana DAG v2 · 04B");
        title.setTextSize(21);
        title.setTextColor(Color.rgb(23, 38, 33));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        progress = new TextView(this);
        progress.setTextSize(17);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(0, dp(7), 0, dp(7));
        root.addView(progress, matchWrap());

        message = new TextView(this);
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        message.setMinHeight(dp(44));
        root.addView(message, matchWrap());

        image = new ImageView(this);
        image.setBackgroundColor(Color.rgb(225, 229, 227));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        primaryPanel = new LinearLayout(this);
        primaryPanel.setOrientation(LinearLayout.VERTICAL);
        primaryPanel.setPadding(0, dp(8), 0, 0);
        primaryPanel.addView(primaryButton("✓ Mostrar", "show", Color.rgb(33, 92, 70)), matchButton());
        primaryPanel.addView(primaryButton("× Ocultar", "hide", Color.rgb(140, 48, 48)), matchButton());
        primaryPanel.addView(primaryButton("? No estoy seguro", "unsure", Color.rgb(91, 82, 45)), matchButton());
        root.addView(primaryPanel, matchWrap());

        reasonPanel = buildReasonPanel();
        reasonPanel.setVisibility(View.GONE);
        root.addView(reasonPanel, matchWrap());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        Button back = actionButton("Atrás");
        back.setOnClickListener(view -> moveBack());
        navigation.addView(back, weightedButton());
        Button undo = actionButton("Deshacer");
        undo.setOnClickListener(view -> undo());
        navigation.addView(undo, weightedButton());
        root.addView(navigation, matchWrap());

        export = actionButton("Exportar evaluación 04B");
        export.setOnClickListener(view -> exportEvaluation());
        root.addView(export, matchButton());
        setContentView(root);
    }

    private LinearLayout buildReasonPanel() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("La decisión ya quedó registrada. Indicá uno o varios motivos:");
        heading.setTextSize(16);
        heading.setPadding(0, dp(8), 0, dp(4));
        container.addView(heading, matchWrap());
        ScrollView scroll = new ScrollView(this);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < REASONS.size(); index++) {
            CheckBox chip = new CheckBox(this);
            chip.setText(REASON_LABELS.get(index));
            chip.setTextSize(17);
            chip.setMinHeight(dp(48));
            chip.setTag(REASONS.get(index));
            chip.setOnCheckedChangeListener((button, checked) -> updateReasonContinue());
            reasonChecks.add(chip);
            chips.addView(chip, matchWrap());
        }
        scroll.addView(chips);
        container.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        reasonContinue = actionButton("Continuar");
        reasonContinue.setOnClickListener(view -> finishReasons(false));
        container.addView(reasonContinue, matchButton());
        Button skip = actionButton("Continuar sin motivo");
        skip.setOnClickListener(view -> finishReasons(true));
        skip.setTag("skip_reasons");
        container.addView(skip, matchButton());
        return container;
    }

    private Button primaryButton(String text, String decision, int color) {
        Button button = actionButton(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
        button.setOnClickListener(view -> recordPrimary(decision));
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setMinHeight(dp(BUTTON_HEIGHT_DP));
        return button;
    }

    private void showStoredPosition() {
        int index = Math.max(0, Math.min(samples.size() - 1, store.currentIndex()));
        if (store.reviewedCount() == samples.size()) {
            showComplete();
            return;
        }
        if (store.get(samples.get(index).sampleId) != null) {
            index = firstPendingFrom(index);
        }
        showSample(index);
    }

    private int firstPendingFrom(int startingIndex) {
        for (int offset = 0; offset < samples.size(); offset++) {
            int index = (startingIndex + offset) % samples.size();
            if (store.get(samples.get(index).sampleId) == null) {
                return index;
            }
        }
        return startingIndex;
    }

    private void showSample(int index) {
        try {
            store.setCurrentIndex(index);
        } catch (Throwable error) {
            showError("No se pudo guardar la posición");
            return;
        }
        currentSample = samples.get(index);
        clearReasonSelection();
        reasonPanel.setVisibility(View.GONE);
        primaryPanel.setVisibility(View.VISIBLE);
        export.setEnabled(store.reviewedCount() == samples.size());
        int pending = samples.size() - store.reviewedCount();
        progress.setText(
                String.format(
                        Locale.US,
                        "%d decisiones humanas pendientes · %d/%d",
                        pending,
                        index + 1,
                        samples.size()));
        message.setText("Cargando imagen verificada…");
        loadImage(currentSample);
    }

    private void loadImage(Sample sample) {
        releaseImage();
        int generation = ++imageGeneration;
        setPrimaryEnabled(false);
        imageExecutor.execute(
                () -> {
                    try {
                        byte[] bytes = readAsset("images/" + sample.file);
                        String actual = sha256(bytes);
                        if (!actual.equals(sample.sha256)) {
                            throw new IllegalStateException("sha256_mismatch");
                        }
                        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Arrays.fill(bytes, (byte) 0);
                        if (decoded == null) {
                            throw new IllegalStateException("decode_failed");
                        }
                        runOnUiThread(
                                () -> {
                                    if (generation != imageGeneration || isFinishing()) {
                                        decoded.recycle();
                                        return;
                                    }
                                    image.setImageBitmap(decoded);
                                    message.setText("Elegí una decisión. No hay respuesta preseleccionada.");
                                    setPrimaryEnabled(true);
                                    ReviewRecord record = store.get(sample.sampleId);
                                    if (record != null
                                            && sample.diagnostic
                                            && !record.decision.equals("show")
                                            && record.reasons.isEmpty()) {
                                        showReasons();
                                    }
                                });
                    } catch (Throwable error) {
                        runOnUiThread(
                                () -> {
                                    if (generation == imageGeneration) {
                                        showError("Imagen rechazada por integridad");
                                    }
                                });
                    }
                });
    }

    private void recordPrimary(String decision) {
        if (currentSample == null) {
            return;
        }
        try {
            store.recordPrimary(currentSample.sampleId, decision);
            updateProgressOnly();
            if (decision.equals("show")) {
                advance();
            } else {
                showReasons();
            }
        } catch (Throwable error) {
            showError("No se pudo guardar la decisión");
        }
    }

    private void showReasons() {
        primaryPanel.setVisibility(View.GONE);
        reasonPanel.setVisibility(View.VISIBLE);
        View skip = reasonPanel.findViewWithTag("skip_reasons");
        skip.setVisibility(currentSample.diagnostic ? View.GONE : View.VISIBLE);
        message.setText(
                currentSample.diagnostic
                        ? "Subconjunto diagnóstico: elegí al menos un motivo."
                        : "Motivos opcionales. Podés continuar sin elegir.");
        updateReasonContinue();
    }

    private void updateReasonContinue() {
        if (reasonContinue == null || currentSample == null) {
            return;
        }
        boolean selected = false;
        for (CheckBox check : reasonChecks) {
            selected |= check.isChecked();
        }
        reasonContinue.setEnabled(selected || !currentSample.diagnostic);
    }

    private void finishReasons(boolean skip) {
        if (currentSample == null) {
            return;
        }
        List<String> reasons = new ArrayList<>();
        if (!skip) {
            for (CheckBox check : reasonChecks) {
                if (check.isChecked()) {
                    reasons.add((String) check.getTag());
                }
            }
        }
        if (currentSample.diagnostic && reasons.isEmpty()) {
            message.setText("Elegí al menos un motivo para esta muestra diagnóstica.");
            return;
        }
        try {
            store.attachReasons(currentSample.sampleId, reasons);
            advance();
        } catch (Throwable error) {
            showError("No se pudieron guardar los motivos");
        }
    }

    private void advance() {
        if (store.reviewedCount() == samples.size()) {
            showComplete();
            return;
        }
        int next = firstPendingFrom((store.currentIndex() + 1) % samples.size());
        showSample(next);
    }

    private void moveBack() {
        if (samples.isEmpty()) {
            return;
        }
        int previous = (store.currentIndex() - 1 + samples.size()) % samples.size();
        showSample(previous);
    }

    private void undo() {
        try {
            String sampleId = store.undoLast();
            if (sampleId == null) {
                message.setText("No hay una decisión para deshacer.");
                return;
            }
            int index = indexOf(sampleId);
            showSample(index < 0 ? 0 : index);
            message.setText("Última decisión deshecha.");
        } catch (Throwable error) {
            showError("No se pudo deshacer");
        }
    }

    private void showComplete() {
        currentSample = null;
        imageGeneration++;
        releaseImage();
        primaryPanel.setVisibility(View.GONE);
        reasonPanel.setVisibility(View.GONE);
        progress.setText("0 decisiones humanas pendientes · 203/203");
        message.setText("Revisión completa. Exportá el JSONL para continuar 04B.");
        export.setEnabled(true);
    }

    private void exportEvaluation() {
        try {
            List<String> order = new ArrayList<>();
            for (Sample sample : samples) {
                order.add(sample.sampleId);
            }
            File directory = new File(getNoBackupFilesDir(), "dag-v2-policy-reviewer-04b/exports");
            ReviewExporter.ExportResult result =
                    ReviewExporter.export(directory, order, store.snapshot());
            Uri download = copyToDownloads(result.file);
            message.setText(
                    "Exportación lista\nSHA-256: "
                            + result.sha256
                            + "\n"
                            + (download == null ? "Disponible por ADB." : "Copiada a Downloads."));
        } catch (Throwable error) {
            showError("La exportación requiere completar todas las decisiones");
        }
    }

    private Uri copyToDownloads(File source) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, "dag-v2-evaluation-04b.jsonl");
        values.put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri =
                getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return null;
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException("downloads_output_unavailable");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
        return uri;
    }

    private void updateProgressOnly() {
        int pending = samples.size() - store.reviewedCount();
        progress.setText(
                String.format(
                        Locale.US,
                        "%d decisiones humanas pendientes · %d/%d",
                        pending,
                        store.currentIndex() + 1,
                        samples.size()));
    }

    private void clearReasonSelection() {
        for (CheckBox check : reasonChecks) {
            check.setChecked(false);
        }
    }

    private void setPrimaryEnabled(boolean enabled) {
        for (int index = 0; index < primaryPanel.getChildCount(); index++) {
            primaryPanel.getChildAt(index).setEnabled(enabled);
        }
    }

    private void releaseImage() {
        if (image == null) {
            return;
        }
        if (image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap bitmap =
                    ((android.graphics.drawable.BitmapDrawable) image.getDrawable()).getBitmap();
            image.setImageDrawable(null);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } else {
            image.setImageDrawable(null);
        }
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private int indexOf(String sampleId) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).sampleId.equals(sampleId)) {
                return index;
            }
        }
        return -1;
    }

    private void showError(String text) {
        message.setText(text);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchButton() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(BUTTON_HEIGHT_DP));
        params.setMargins(0, dp(3), 0, dp(3));
        return params;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(BUTTON_HEIGHT_DP), 1f);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Sample {
        final String sampleId;
        final String file;
        final String sha256;
        final boolean diagnostic;

        Sample(String sampleId, String file, String sha256, boolean diagnostic) {
            this.sampleId = sampleId;
            this.file = file;
            this.sha256 = sha256;
            this.diagnostic = diagnostic;
        }
    }
}
