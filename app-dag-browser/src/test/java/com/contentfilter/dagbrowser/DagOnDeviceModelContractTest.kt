package com.contentfilter.dagbrowser

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagOnDeviceModelContractTest {
    @Test
    fun `single embedded production model matches its frozen artifact`() {
        val model = File("src/main/assets/${DagOnDeviceImageAnalyzer.ModelAssetPath}")

        assertTrue(model.isFile)
        assertEquals("GloshIA Visual", DagVisualModelInfo.PublicName)
        assertEquals("R3.1", DagVisualModelInfo.FunctionalVersion)
        assertEquals("ONNX Runtime Android 1.27.0", DagVisualModelInfo.Runtime)
        assertEquals("dag-36", DagVisualModelInfo.PolicyVersion)
        assertEquals(ExpectedModelSha256, DagVisualModelInfo.ModelSha256)
        assertEquals(DagVisualModelInfo.ModelSha256, model.sha256())
        assertTrue(model.length() in 9_000_000L..10_000_000L)
        assertEquals(0.4f, DagOnDeviceImageAnalyzer.FilterThreshold)
        assertEquals(0.95f, DagOnDeviceImageAnalyzer.FullStrongFilterThreshold)
        assertEquals(0.3f, DagOnDeviceImageAnalyzer.UncertainRegionalReviewFloor)
        assertEquals(0.45f, DagOnDeviceImageAnalyzer.UncertainRegionalFilterThreshold)
        assertEquals(0.5f, DagOnDeviceImageAnalyzer.RegionalFilterThreshold)
        assertEquals(0.7f, DagOnDeviceImageAnalyzer.RegionalStrongFilterThreshold)
        assertEquals(2, DagOnDeviceImageAnalyzer.RegionalConsensusMinimum)
    }

    private fun File.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(readBytes())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ExpectedModelSha256 =
            "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
    }
}
