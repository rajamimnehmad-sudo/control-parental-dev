package com.contentfilter.dag2.policyreviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ReviewStoreTest {
    private File directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("dag-v2-review-store").toFile();
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
    public void startsWithZeroAutomaticLabels() throws Exception {
        ReviewStore store = new ReviewStore(directory);
        assertEquals(0, store.reviewedCount());
        assertTrue(store.snapshot().isEmpty());
        assertEquals(0, store.auditCount());
    }

    @Test
    public void persistsAndResumesCurrentDecisions() throws Exception {
        ReviewStore store = new ReviewStore(directory);
        store.setCurrentIndex(7);
        store.recordPrimary("sample-a", "hide");
        store.attachReasons("sample-a", Arrays.asList("elbow", "knee"));

        ReviewStore reopened = new ReviewStore(directory);
        assertEquals(7, reopened.currentIndex());
        assertEquals(1, reopened.reviewedCount());
        assertEquals("hide", reopened.get("sample-a").decision);
        assertEquals(Arrays.asList("elbow", "knee"), reopened.get("sample-a").reasons);
        assertTrue(new File(directory, "progress.json").isFile());
        assertFalse(new File(directory, "progress.json.tmp").exists());
    }

    @Test
    public void oneCurrentDecisionAndCorrectionHistoryArePreserved() throws Exception {
        ReviewStore store = new ReviewStore(directory);
        ReviewRecord first = store.recordPrimary("sample-a", "hide");
        ReviewRecord corrected = store.recordPrimary("sample-a", "show");

        assertEquals(1, store.reviewedCount());
        assertEquals("show", store.get("sample-a").decision);
        assertEquals(1, first.reviewNumber);
        assertEquals(2, corrected.reviewNumber);
        assertEquals(2, store.auditCount());

        ReviewStore reopened = new ReviewStore(directory);
        assertEquals(2, reopened.get("sample-a").reviewNumber);
        assertEquals(2, reopened.auditCount());
    }

    @Test
    public void undoRestoresPreviousDecisionAndSurvivesReopen() throws Exception {
        ReviewStore store = new ReviewStore(directory);
        store.recordPrimary("sample-a", "hide");
        store.attachReasons("sample-a", Collections.singletonList("abdomen"));
        store.recordPrimary("sample-a", "show");

        assertEquals("sample-a", store.undoLast());
        assertEquals("hide", store.get("sample-a").decision);
        assertEquals(Collections.singletonList("abdomen"), store.get("sample-a").reasons);
        assertTrue(store.auditCount() >= 4);

        ReviewStore reopened = new ReviewStore(directory);
        assertEquals("hide", reopened.get("sample-a").decision);
    }

    @Test
    public void undoFirstDecisionReturnsSampleToPending() throws Exception {
        ReviewStore store = new ReviewStore(directory);
        store.recordPrimary("sample-a", "unsure");
        assertEquals("sample-a", store.undoLast());
        assertEquals(0, store.reviewedCount());
        assertNull(store.get("sample-a"));
    }
}
