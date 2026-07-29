package com.contentfilter.dagbrowser

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagMediaBytesPolicyTest {
    @Test
    fun `bounded supported image reaches unavailable analyzer and stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaAnalysisPolicy.AnalyzerUnavailableReason, decision.reason)
    }

    @Test
    fun `model probability below threshold releases the image`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.399f) },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.399f, decision.filterProbability)
    }

    @Test
    fun `model probability at threshold stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.4f) },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.4f, decision.filterProbability)
    }

    @Test
    fun `invalid model probability stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(Float.NaN) },
            )

        assertEquals(DagMediaAction.Block, decision.action)
    }

    @Test
    fun `invalid base64 stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1)).copy(bytesBase64 = "not-base64"),
                boundsReader = DagImageBoundsReader { DagImageBounds(1, 1, "image/png") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.InvalidPayloadReason, decision.reason)
    }

    @Test
    fun `declared and decoded byte lengths must match`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)).copy(declaredByteLength = 2),
                boundsReader = DagImageBoundsReader { DagImageBounds(1, 1, "image/png") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.InvalidPayloadReason, decision.reason)
    }

    @Test
    fun `unsupported bytes stay blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { null },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsupportedImageReason, decision.reason)
    }

    @Test
    fun `static avif reaches the same bounded classifier`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/avif") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.2f) },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
    }

    @Test
    fun `unsupported decoded format stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/heic") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsupportedImageReason, decision.reason)
    }

    @Test
    fun `declared payload above transport cap stays blocked before decoding`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload =
                    payload(byteArrayOf(1))
                        .copy(declaredByteLength = DagMediaBytesPolicy.MaxCaptureBytes + 1),
                boundsReader = DagImageBoundsReader { error("must not decode") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.InvalidPayloadReason, decision.reason)
    }

    @Test
    fun `large ordinary image can use the fallback transport`() {
        val bytes = ByteArray(512 * 1024 + 1) { 1 }
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(bytes),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 1_600, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.2f) },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
    }

    @Test
    fun `decompression bomb dimensions stay blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader =
                    DagImageBoundsReader {
                        DagImageBounds(width = 20_000, height = 20_000, mimeType = "image/png")
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsafeDimensionsReason, decision.reason)
    }

    @Test
    fun `preprocessor rejection stays blocked with its bounded reason`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor =
                    DagImagePreprocessor {
                        DagImagePreprocessResult.Rejected(
                            AndroidDagImagePreprocessor.AnimatedImageReason,
                        )
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(AndroidDagImagePreprocessor.AnimatedImageReason, decision.reason)
    }

    @Test
    fun `malformed preprocessor output stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor =
                    DagImagePreprocessor {
                        DagImagePreprocessResult.Ready(
                            DagPreparedImage(
                                width = 1,
                                height = 1,
                                rgb888 = byteArrayOf(0, 0, 0),
                            ),
                        )
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(AndroidDagImagePreprocessor.DecodeFailedReason, decision.reason)
    }

    @Test
    fun `prepared rgb is overwritten after the fail closed decision`() {
        val rgb = ByteArray(DagImageDecodeContract.PreparedByteCount) { 127 }
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor =
                    DagImagePreprocessor {
                        DagImagePreprocessResult.Ready(
                            DagPreparedImage(
                                width = DagImageDecodeContract.TargetSize,
                                height = DagImageDecodeContract.TargetSize,
                                rgb888 = rgb,
                            ),
                        )
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertTrue(rgb.all { it == 0.toByte() })
    }

    private val readyPreprocessor =
        DagImagePreprocessor {
            DagImagePreprocessResult.Ready(
                DagPreparedImage(
                    width = DagImageDecodeContract.TargetSize,
                    height = DagImageDecodeContract.TargetSize,
                    rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount),
                ),
            )
        }

    private fun payload(bytes: ByteArray) =
        DagMediaBytesPayload(
            candidateId = "response_1_abcd",
            sourceUrl = "https://images.example/photo.jpg",
            declaredByteLength = bytes.size,
            bytesBase64 = Base64.getEncoder().encodeToString(bytes),
        )
}
