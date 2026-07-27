package com.contentfilter.dag2.policyreviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ReviewExporter {
    static final String POLICY_VERSION = "DAG_STRICT_MODESTY_V1";
    static final String REVIEWER_VERSION = "dag-v2-policy-reviewer-04b-1";

    static ExportResult export(
            File directory,
            List<String> orderedSampleIds,
            Map<String, ReviewRecord> records)
            throws IOException, JSONException {
        if (records.size() != orderedSampleIds.size()) {
            throw new IllegalStateException(
                    (orderedSampleIds.size() - records.size()) + "_decisions_pending");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("export_storage_unavailable");
        }
        File target = new File(directory, "dag-v2-evaluation-04b.jsonl");
        File temporary = new File(directory, "dag-v2-evaluation-04b.jsonl.tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            for (String sampleId : orderedSampleIds) {
                ReviewRecord record = records.get(sampleId);
                if (record == null) {
                    throw new IllegalStateException("decision_missing");
                }
                JSONObject line = new JSONObject();
                line.put("sample_id", record.sampleId);
                line.put("decision", record.decision);
                line.put("reasons", new JSONArray(record.reasons));
                line.put("review_number", record.reviewNumber);
                line.put("reviewed_at", record.reviewedAt);
                line.put("policy_version", POLICY_VERSION);
                line.put("reviewer_version", REVIEWER_VERSION);
                byte[] bytes = (line.toString() + "\n").getBytes(StandardCharsets.UTF_8);
                digest.update(bytes);
                output.write(bytes);
            }
            output.flush();
            output.getFD().sync();
        }
        try {
            Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return new ExportResult(target, toHex(digest.digest()), target.length());
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[64 * 1024];
            try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
                int read;
                while ((read = input.read(bytes)) >= 0) {
                    digest.update(bytes, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static String toHex(byte[] values) {
        StringBuilder output = new StringBuilder();
        for (byte value : values) {
            output.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return output.toString();
    }

    static final class ExportResult {
        final File file;
        final String sha256;
        final long sizeBytes;

        ExportResult(File file, String sha256, long sizeBytes) {
            this.file = file;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }
    }
}
