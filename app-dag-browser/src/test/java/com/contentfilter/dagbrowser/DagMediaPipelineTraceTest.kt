package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagMediaPipelineTraceTest {
    @Test
    fun `trace accumulates stages and inference count without changing results`() {
        val ticks = listOf(0L, 1_000_000L, 2_000_000L, 5_000_000L).iterator()
        val trace = DagMediaPipelineTrace { ticks.next() }

        assertEquals("decode", trace.measure(DagMediaPipelineStage.Base64Decode) { "decode" })
        assertEquals("allow", trace.measureInference { "allow" })

        assertEquals(1.0, trace.elapsedMillis(DagMediaPipelineStage.Base64Decode))
        assertEquals(3.0, trace.elapsedMillis(DagMediaPipelineStage.Inference))
        assertEquals(1, trace.inferenceCount)
    }
}
