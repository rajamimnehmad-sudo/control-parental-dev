package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DagNeuralModelVerificationTest {
    @Test
    fun `valid marker trusts the same verified private model`() {
        val marker =
            assertNotNull(
                dagNeuralModelVerificationMarker(
                    modelVersion = ModelVersion,
                    expectedSha256 = expectedSha256,
                    expectedBytes = ExpectedBytes,
                    metadata = metadata,
                ),
            )

        assertTrue(
            dagNeuralModelVerificationMarkerMatches(
                marker = marker,
                modelVersion = ModelVersion,
                expectedSha256 = expectedSha256,
                expectedBytes = ExpectedBytes,
                metadata = metadata,
            ),
        )
    }

    @Test
    fun `expected digest mismatch invalidates marker`() {
        val marker = validMarker()

        assertFalse(
            dagNeuralModelVerificationMarkerMatches(
                marker = marker,
                modelVersion = ModelVersion,
                expectedSha256 = "b".repeat(64),
                expectedBytes = ExpectedBytes,
                metadata = metadata,
            ),
        )
    }

    @Test
    fun `model file tamper invalidates marker metadata`() {
        val marker = validMarker()

        assertFalse(
            dagNeuralModelVerificationMarkerMatches(
                marker = marker,
                modelVersion = ModelVersion,
                expectedSha256 = expectedSha256,
                expectedBytes = ExpectedBytes,
                metadata = metadata.copy(changedNanoseconds = metadata.changedNanoseconds + 1L),
            ),
        )
        assertFalse(
            dagNeuralModelVerificationMarkerMatches(
                marker = marker,
                modelVersion = ModelVersion,
                expectedSha256 = expectedSha256,
                expectedBytes = ExpectedBytes,
                metadata = metadata.copy(bytes = ExpectedBytes - 1L),
            ),
        )
    }

    @Test
    fun `model version change invalidates marker`() {
        val marker = validMarker()

        assertFalse(
            dagNeuralModelVerificationMarkerMatches(
                marker = marker,
                modelVersion = "$ModelVersion-next",
                expectedSha256 = expectedSha256,
                expectedBytes = ExpectedBytes,
                metadata = metadata,
            ),
        )
    }

    private fun validMarker(): String =
        assertNotNull(
            dagNeuralModelVerificationMarker(
                modelVersion = ModelVersion,
                expectedSha256 = expectedSha256,
                expectedBytes = ExpectedBytes,
                metadata = metadata,
            ),
        )

    private companion object {
        const val ModelVersion = "dag-neural-test-v1"
        const val ExpectedBytes = 123_379_433L

        val expectedSha256 = "a".repeat(64)
        val metadata =
            DagNeuralModelFileMetadata(
                fileName = "dag-neural.onnx",
                bytes = ExpectedBytes,
                device = 7L,
                inode = 42L,
                modifiedSeconds = 1_750_000_000L,
                modifiedNanoseconds = 123_000_000L,
                changedSeconds = 1_750_000_001L,
                changedNanoseconds = 456_000_000L,
            )
    }
}
