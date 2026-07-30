package com.contentfilter.dagbrowser

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagOnDeviceModelContractTest {
    @Test
    fun `embedded model is the calibrated bounded finetune artifact`() {
        val model = File("src/main/assets/${DagOnDeviceImageAnalyzer.ModelAssetPath}")

        assertTrue(model.isFile)
        assertEquals(ExpectedModelSha256, model.sha256())
        assertTrue(model.length() in 8_000_000L..9_000_000L)
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
            "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee"
    }
}
