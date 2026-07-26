package com.contentfilter.dag2.policyreviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ReviewStore {
    private static final int SCHEMA_VERSION = 1;
    private final File directory;
    private final File stateFile;
    private final Map<String, ReviewRecord> current = new LinkedHashMap<>();
    private final Map<String, Integer> revisionCounters = new HashMap<>();
    private final List<JSONObject> actionStack = new ArrayList<>();
    private final List<JSONObject> audit = new ArrayList<>();
    private int currentIndex;

    ReviewStore(File directory) throws IOException, JSONException {
        this.directory = directory;
        this.stateFile = new File(directory, "progress.json");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("private_storage_unavailable");
        }
        load();
    }

    synchronized int currentIndex() {
        return currentIndex;
    }

    synchronized void setCurrentIndex(int value) throws IOException, JSONException {
        currentIndex = Math.max(0, value);
        persist();
    }

    synchronized int reviewedCount() {
        return current.size();
    }

    synchronized ReviewRecord get(String sampleId) {
        return current.get(sampleId);
    }

    synchronized Map<String, ReviewRecord> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(current));
    }

    synchronized int auditCount() {
        return audit.size();
    }

    synchronized ReviewRecord recordPrimary(String sampleId, String decision)
            throws IOException, JSONException {
        if (!decision.equals("show") && !decision.equals("hide") && !decision.equals("unsure")) {
            throw new IllegalArgumentException("invalid_decision");
        }
        ReviewRecord previous = current.get(sampleId);
        int revision = revisionCounters.getOrDefault(sampleId, 0) + 1;
        revisionCounters.put(sampleId, revision);
        ReviewRecord next =
                new ReviewRecord(sampleId, decision, Collections.emptyList(), revision, Instant.now().toString());
        current.put(sampleId, next);
        JSONObject action = new JSONObject();
        action.put("sample_id", sampleId);
        action.put("previous", previous == null ? JSONObject.NULL : previous.toJson());
        action.put("current", next.toJson());
        actionStack.add(action);
        appendAudit("decision", sampleId, previous, next);
        persist();
        return next;
    }

    synchronized ReviewRecord attachReasons(String sampleId, List<String> reasons)
            throws IOException, JSONException {
        ReviewRecord record = current.get(sampleId);
        if (record == null) {
            throw new IllegalStateException("decision_required_before_reasons");
        }
        ReviewRecord updated =
                new ReviewRecord(
                        record.sampleId,
                        record.decision,
                        reasons,
                        record.reviewNumber,
                        record.reviewedAt);
        current.put(sampleId, updated);
        for (int index = actionStack.size() - 1; index >= 0; index--) {
            JSONObject action = actionStack.get(index);
            if (sampleId.equals(action.getString("sample_id"))) {
                action.put("current", updated.toJson());
                break;
            }
        }
        appendAudit("reasons", sampleId, record, updated);
        persist();
        return updated;
    }

    synchronized String undoLast() throws IOException, JSONException {
        if (actionStack.isEmpty()) {
            return null;
        }
        JSONObject action = actionStack.remove(actionStack.size() - 1);
        String sampleId = action.getString("sample_id");
        ReviewRecord before = current.get(sampleId);
        Object previous = action.get("previous");
        ReviewRecord restored = null;
        if (previous == JSONObject.NULL) {
            current.remove(sampleId);
        } else {
            restored = ReviewRecord.fromJson((JSONObject) previous);
            current.put(sampleId, restored);
        }
        appendAudit("undo", sampleId, before, restored);
        persist();
        return sampleId;
    }

    private void appendAudit(
            String action,
            String sampleId,
            ReviewRecord previous,
            ReviewRecord next)
            throws JSONException {
        JSONObject event = new JSONObject();
        event.put("action", action);
        event.put("sample_id", sampleId);
        event.put("at", Instant.now().toString());
        event.put("previous", previous == null ? JSONObject.NULL : previous.toJson());
        event.put("next", next == null ? JSONObject.NULL : next.toJson());
        audit.add(event);
    }

    private void load() throws IOException, JSONException {
        if (!stateFile.exists()) {
            return;
        }
        JSONObject root =
                new JSONObject(new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8));
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            throw new IOException("unsupported_progress_schema");
        }
        currentIndex = root.optInt("current_index", 0);
        JSONObject decisions = root.getJSONObject("decisions");
        java.util.Iterator<String> decisionKeys = decisions.keys();
        while (decisionKeys.hasNext()) {
            String sampleId = decisionKeys.next();
            current.put(sampleId, ReviewRecord.fromJson(decisions.getJSONObject(sampleId)));
        }
        JSONObject revisions = root.getJSONObject("revision_counters");
        java.util.Iterator<String> revisionKeys = revisions.keys();
        while (revisionKeys.hasNext()) {
            String sampleId = revisionKeys.next();
            revisionCounters.put(sampleId, revisions.getInt(sampleId));
        }
        JSONArray stack = root.getJSONArray("action_stack");
        for (int index = 0; index < stack.length(); index++) {
            actionStack.add(stack.getJSONObject(index));
        }
        JSONArray auditJson = root.getJSONArray("audit");
        for (int index = 0; index < auditJson.length(); index++) {
            audit.add(auditJson.getJSONObject(index));
        }
    }

    private void persist() throws IOException, JSONException {
        JSONObject root = new JSONObject();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("current_index", currentIndex);
        JSONObject decisions = new JSONObject();
        for (Map.Entry<String, ReviewRecord> entry : current.entrySet()) {
            decisions.put(entry.getKey(), entry.getValue().toJson());
        }
        root.put("decisions", decisions);
        root.put("revision_counters", new JSONObject(revisionCounters));
        root.put("action_stack", new JSONArray(actionStack));
        root.put("audit", new JSONArray(audit));
        byte[] bytes = (root.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        File temporary = new File(directory, "progress.json.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        try {
            Files.move(
                    temporary.toPath(),
                    stateFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(
                    temporary.toPath(),
                    stateFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
