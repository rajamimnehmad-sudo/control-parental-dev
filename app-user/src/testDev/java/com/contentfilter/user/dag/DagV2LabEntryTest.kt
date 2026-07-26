package com.contentfilter.user.dag

import com.contentfilter.user.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagV2LabEntryTest {
    @Test
    fun `dev flavor enables only the explicit internal dag2 component`() {
        assertTrue(BuildConfig.DAG_V2_BROWSER_AVAILABLE)
        assertEquals("com.contentfilter.user.dag2.DagV2LabActivity", DagV2LabActivityClassName)
    }
}
