package com.contentfilter.dagbrowser

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagOnDeviceModelContractTest {
    @Test
    fun `embedded canary and rollback models match their frozen artifacts`() {
        val model = File("src/main/assets/${DagOnDeviceImageAnalyzer.ModelAssetPath}")
        val fallback = File("src/main/assets/${DagVisualModelInfo.FallbackModelAssetPath}")

        assertTrue(model.isFile)
        assertTrue(fallback.isFile)
        assertEquals("GloshIA Visual", DagVisualModelInfo.PublicName)
        assertEquals("R3 Canary", DagVisualModelInfo.FunctionalVersion)
        assertEquals("ONNX Runtime Android 1.27.0", DagVisualModelInfo.Runtime)
        assertEquals("dag-36", DagVisualModelInfo.PolicyVersion)
        assertEquals(ExpectedModelSha256, DagVisualModelInfo.ModelSha256)
        assertEquals(DagVisualModelInfo.ModelSha256, model.sha256())
        assertEquals(ExpectedFallbackSha256, DagVisualModelInfo.FallbackModelSha256)
        assertEquals(DagVisualModelInfo.FallbackModelSha256, fallback.sha256())
        assertEquals(
            listOf(DagVisualModelInfo.ModelAssetPath, DagVisualModelInfo.FallbackModelAssetPath),
            DagOnDeviceImageAnalyzer.ModelAssetPaths,
        )
        assertTrue(model.length() in 10_000_000L..11_000_000L)
        assertTrue(fallback.length() in 8_000_000L..9_000_000L)
        assertEquals(0.4f, DagOnDeviceImageAnalyzer.FilterThreshold)
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
            "0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8"
        const val ExpectedFallbackSha256 =
            "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee"
    }
}
