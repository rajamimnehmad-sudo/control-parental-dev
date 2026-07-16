package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DagPageFingerprintTest {
    @Test
    fun `fingerprint is stable but changes with URL content or visual decision`() {
        val images = DagImagePageSummary(allowed = 3, blocked = 0, uncertain = 1)
        val baseline = dagPageFingerprint("https://example.com/a", "Title", "Body", images)

        assertEquals(baseline, dagPageFingerprint("https://example.com/a", "Title", "Body", images))
        assertNotEquals(baseline, dagPageFingerprint("https://example.com/b", "Title", "Body", images))
        assertNotEquals(baseline, dagPageFingerprint("https://example.com/a", "Title", "Changed", images))
        assertNotEquals(
            baseline,
            dagPageFingerprint(
                "https://example.com/a",
                "Title",
                "Body",
                images.copy(blocked = 1),
            ),
        )
    }
}
