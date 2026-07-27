package com.contentfilter.dagbrowser

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class DagMediaBytesPolicyTest {
    @Test
    fun `bounded supported image reaches unavailable analyzer and stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaAnalysisPolicy.AnalyzerUnavailableReason, decision.reason)
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
    fun `unsupported decoded format stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/avif") },
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

    private fun payload(bytes: ByteArray) =
        DagMediaBytesPayload(
            candidateId = "response_1_abcd",
            sourceUrl = "https://images.example/photo.jpg",
            declaredByteLength = bytes.size,
            bytesBase64 = Base64.getEncoder().encodeToString(bytes),
        )
}
