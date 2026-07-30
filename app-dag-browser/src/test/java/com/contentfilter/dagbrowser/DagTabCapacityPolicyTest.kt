package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagTabCapacityPolicyTest {
    @Test
    fun `capacity allows the fiftieth tab but rejects another`() {
        assertTrue(DagTabCapacityPolicy.canCreate(49))
        assertFalse(DagTabCapacityPolicy.canCreate(50))
        assertFalse(DagTabCapacityPolicy.canCreate(-1))
    }

    @Test
    fun `all fifty thumbnails stay within twelve megabytes`() {
        assertTrue(DagTabCapacityPolicy.MaxThumbnailMemoryBytes <= 12_000_000)
    }
}
