package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagPreparedRasterPolicyTest {
    @Test
    fun `safe raster uses the official allow threshold`() {
        val decision = decideWith(0.2f)

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.2f, decision.filterProbability)
    }

    @Test
    fun `blocked raster uses the official filter threshold`() {
        val decision = decideWith(DagOnDeviceImageAnalyzer.FilterThreshold)

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(DagOnDeviceImageAnalyzer.FilterThreshold, decision.filterProbability)
    }

    @Test
    fun `uncertain raster uses the same bounded regional review as images`() {
        val probabilities = ArrayDeque(listOf(0.35f, 0.46f))
        var inferenceCount = 0
        val decision =
            DagPreparedRasterPolicy.decide(
                candidateId = CandidateId,
                preparedImages = listOf(preparedImage()),
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        DagImageAnalysisResult.Classified(probabilities.removeFirst())
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.46f, decision.filterProbability)
        assertEquals(2, inferenceCount)
    }

    @Test
    fun `analyzer error and stale work fail closed`() {
        val unavailable =
            DagPreparedRasterPolicy.decide(
                candidateId = CandidateId,
                preparedImages = listOf(preparedImage()),
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Unavailable("fixture_error") },
            )
        val stale =
            DagPreparedRasterPolicy.decide(
                candidateId = CandidateId,
                preparedImages = listOf(preparedImage()),
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.1f) },
                workGuard = DagMediaWorkGuard { false },
            )

        assertEquals(DagMediaAction.Block, unavailable.action)
        assertEquals("fixture_error", unavailable.reason)
        assertEquals(DagMediaAction.Block, stale.action)
        assertEquals(DagMediaBytesPolicy.AnalysisExpiredReason, stale.reason)
    }

    @Test
    fun `invalid prepared raster never reaches analyzer`() {
        var called = false
        val decision =
            DagPreparedRasterPolicy.decide(
                candidateId = CandidateId,
                preparedImages =
                    listOf(
                        DagPreparedImage(
                            width = 1,
                            height = 1,
                            rgb888 = ByteArray(3),
                        ),
                    ),
                analyzer =
                    DagImageAnalyzer {
                        called = true
                        DagImageAnalysisResult.Classified(0.1f)
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(AndroidDagImagePreprocessor.DecodeFailedReason, decision.reason)
        assertTrue(!called)
    }

    @Test
    fun `image transport and video raster share the same final authority`() {
        val direct = decideWith(0.2f)
        val transported =
            DagMediaBytesPolicy.decide(
                payload =
                    DagMediaBytesPayload(
                        candidateId = CandidateId,
                        sourceUrl = "https://example.test/frame.png",
                        declaredByteLength = 1,
                        bytesBase64 = "AA==",
                    ),
                boundsReader =
                    DagImageBoundsReader {
                        DagImageBounds(
                            width = DagImageDecodeContract.TargetSize,
                            height = DagImageDecodeContract.TargetSize,
                            mimeType = "image/png",
                        )
                    },
                preprocessor =
                    DagImagePreprocessor {
                        DagImagePreprocessResult.Ready(
                            image = preparedImage(),
                            sourceBounds =
                                DagImageBounds(
                                    width = DagImageDecodeContract.TargetSize,
                                    height = DagImageDecodeContract.TargetSize,
                                    mimeType = "image/png",
                                ),
                        )
                    },
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.2f) },
            )

        assertEquals(direct.action, transported.action)
        assertEquals(direct.reason, transported.reason)
        assertEquals(direct.filterProbability, transported.filterProbability)
    }

    private fun decideWith(probability: Float): DagMediaDecision =
        DagPreparedRasterPolicy.decide(
            candidateId = CandidateId,
            preparedImages = listOf(preparedImage()),
            analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(probability) },
        )

    private fun preparedImage() =
        DagPreparedImage(
            width = DagImageDecodeContract.TargetSize,
            height = DagImageDecodeContract.TargetSize,
            rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount),
        )

    private companion object {
        const val CandidateId = "video_0123456789abcdef"
    }
}
