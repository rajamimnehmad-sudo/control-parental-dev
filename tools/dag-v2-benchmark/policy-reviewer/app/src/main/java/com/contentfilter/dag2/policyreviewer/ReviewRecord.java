package com.contentfilter.dag2.policyreviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ReviewRecord {
    final String sampleId;
    final String decision;
    final List<String> reasons;
    final int reviewNumber;
    final String reviewedAt;

    ReviewRecord(
            String sampleId,
            String decision,
            List<String> reasons,
            int reviewNumber,
            String reviewedAt) {
        this.sampleId = sampleId;
        this.decision = decision;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        this.reviewNumber = reviewNumber;
        this.reviewedAt = reviewedAt;
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("sample_id", sampleId);
        value.put("decision", decision);
        value.put("reasons", new JSONArray(reasons));
        value.put("review_number", reviewNumber);
        value.put("reviewed_at", reviewedAt);
        return value;
    }

    static ReviewRecord fromJson(JSONObject value) throws JSONException {
        JSONArray reasonsJson = value.getJSONArray("reasons");
        List<String> reasons = new ArrayList<>();
        for (int index = 0; index < reasonsJson.length(); index++) {
            reasons.add(reasonsJson.getString(index));
        }
        return new ReviewRecord(
                value.getString("sample_id"),
                value.getString("decision"),
                reasons,
                value.getInt("review_number"),
                value.getString("reviewed_at"));
    }
}
