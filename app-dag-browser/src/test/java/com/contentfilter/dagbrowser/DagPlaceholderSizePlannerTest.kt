package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DagPlaceholderSizePlannerTest {
    @Test
    fun `large landscape placeholder preserves ratio inside bounded edge`() {
        assertEquals(
            DagPlaceholderSize(320, 180),
            DagPlaceholderSizePlanner.plan(1920, 1080),
        )
    }

    @Test
    fun `large portrait placeholder preserves ratio inside bounded edge`() {
        assertEquals(
            DagPlaceholderSize(180, 320),
            DagPlaceholderSizePlanner.plan(1080, 1920),
        )
    }

    @Test
    fun `small and missing dimensions stay safe`() {
        assertEquals(DagPlaceholderSize(80, 40), DagPlaceholderSizePlanner.plan(80, 40))
        assertEquals(DagPlaceholderSize(64, 64), DagPlaceholderSizePlanner.plan(null, null))
    }

    @Test
    fun `placeholder cache stays bounded`() {
        val source = File("src/main/java/com/contentfilter/dagbrowser/DagBlockedImagePlaceholder.kt").readText()

        assertContains(source, "MaximumCachedSizes = 32")
        assertContains(source, "size > MaximumCachedSizes")
        assertContains(source, "synchronized(cache)")
    }
}
