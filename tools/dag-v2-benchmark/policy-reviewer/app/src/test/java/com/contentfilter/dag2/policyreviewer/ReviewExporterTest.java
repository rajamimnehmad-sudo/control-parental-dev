package com.contentfilter.dag2.policyreviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ReviewExporterTest {
    private File directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("dag-v2-review-export").toFile();
    }

    @After
    public void tearDown() throws Exception {
        if (directory.exists()) {
            Files.walk(directory.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.delete(path);
                                } catch (Exception ignored) {
                                    // Test cleanup only.
                                }
                            });
        }
    }

    @Test
    public void exportContainsOnlySanitizedContractFields() throws Exception {
        List<String> order = Arrays.asList("sample-a", "sample-b");
        Map<String, ReviewRecord> records = new LinkedHashMap<>();
        records.put(
                "sample-a",
                new ReviewRecord(
                        "sample-a",
                        "show",
                        Collections.emptyList(),
                        1,
                        "2026-07-26T12:00:00Z"));
        records.put(
                "sample-b",
                new ReviewRecord(
                        "sample-b",
                        "hide",
                        Collections.singletonList("knee"),
                        2,
                        "2026-07-26T12:01:00Z"));

        ReviewExporter.ExportResult result = ReviewExporter.export(directory, order, records);
        List<String> lines = Files.readAllLines(result.file.toPath(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        for (String line : lines) {
            JSONObject item = new JSONObject(line);
            java.util.List<String> keys = new java.util.ArrayList<>();
            java.util.Iterator<String> iterator = item.keys();
            while (iterator.hasNext()) {
                keys.add(iterator.next());
            }
            java.util.Collections.sort(keys);
            assertEquals(
                    Arrays.asList(
                            "decision",
                            "policy_version",
                            "reasons",
                            "review_number",
                            "reviewed_at",
                            "reviewer_version",
                            "sample_id"),
                    keys);
            assertFalse(line.contains("url"));
            assertFalse(line.contains("model"));
            assertFalse(line.contains("cookie"));
            assertFalse(line.contains("author"));
        }
        assertEquals(result.sha256, ReviewExporter.sha256(result.file));
        assertTrue(result.sizeBytes > 0);
        assertFalse(new File(directory, "dag-v2-evaluation-04b.jsonl.tmp").exists());
    }

    @Test
    public void incompleteReviewCannotExport() throws Exception {
        Map<String, ReviewRecord> records = new LinkedHashMap<>();
        records.put(
                "sample-a",
                new ReviewRecord(
                        "sample-a",
                        "show",
                        Collections.emptyList(),
                        1,
                        "2026-07-26T12:00:00Z"));
        try {
            ReviewExporter.export(directory, Arrays.asList("sample-a", "sample-b"), records);
            fail("incomplete export should fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("decisions_pending"));
        }
    }
}
