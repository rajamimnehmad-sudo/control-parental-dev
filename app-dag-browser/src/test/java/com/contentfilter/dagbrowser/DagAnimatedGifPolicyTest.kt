package com.contentfilter.dagbrowser

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class DagAnimatedGifPolicyTest {
    @Test
    fun `safe animated GIF checks every frame cheaply and samples stable frames with the model`() {
        val analyzed = mutableListOf<Int>()
        var decoded = 0
        val decision =
            decide(
                frameValues = listOf(10, 10, 10),
                onDecoded = { decoded += 1 },
                analyzer =
                    DagImageAnalyzer { image ->
                        analyzed += image.rgb888.first().toInt() and 0xff
                        DagImageAnalysisResult.Classified(0.01f)
                    },
            )

        assertEquals(3, decoded)
        assertEquals(listOf(10), analyzed)
        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
    }

    @Test
    fun `one unsafe frame blocks the GIF without inspecting or releasing later frames`() {
        val analyzed = mutableListOf<Int>()
        val decision =
            decide(
                frameValues = listOf(10, 200, 30),
                analyzer =
                    DagImageAnalyzer { image ->
                        val value = image.rgb888.first().toInt() and 0xff
                        analyzed += value
                        DagImageAnalysisResult.Classified(if (value >= 200) 0.9f else 0.01f)
                    },
            )

        assertEquals(listOf(10, 200), analyzed)
        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
    }

    @Test
    fun `decoder failure blocks the GIF and never falls back to its first frame`() {
        val decision =
            decide(
                frameValues = emptyList(),
                decodeResult =
                    DagGifFrameDecodeResult.Rejected(
                        AndroidDagGifFrameDecoder.DecodeFailedReason,
                    ),
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(AndroidDagGifFrameDecoder.DecodeFailedReason, decision.reason)
    }

    @Test
    fun `complex animation stops at the heavy analysis budget`() {
        var analyses = 0
        val decision =
            decide(
                frameValues = List(12) { index -> if (index % 2 == 0) 10 else 220 },
                analyzer =
                    DagImageAnalyzer {
                        analyses += 1
                        DagImageAnalysisResult.Classified(0.01f)
                    },
            )

        assertEquals(DagMediaBytesPolicy.MaximumGifHeavyAnalyses, analyses)
        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.AnimatedGifAnalysisLimitReason, decision.reason)
    }

    private fun decide(
        frameValues: List<Int>,
        decodeResult: DagGifFrameDecodeResult = DagGifFrameDecodeResult.Completed,
        analyzer: DagImageAnalyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.01f) },
        onDecoded: () -> Unit = {},
    ): DagMediaDecision {
        val gif = gifBytes(frameCount = frameValues.size.coerceAtLeast(2))
        return DagMediaBytesPolicy.decide(
            payload =
                DagMediaBytesPayload(
                    candidateId = "gif_candidate",
                    sourceUrl = "https://example.test/animation.gif",
                    declaredByteLength = gif.size,
                    bytesBase64 = Base64.getEncoder().encodeToString(gif),
                ),
            boundsReader = DagImageBoundsReader { DagImageBounds(2, 2, "image/gif") },
            preprocessor = DagImagePreprocessor { error("static decoder must not inspect animation") },
            analyzer = analyzer,
            gifFrameDecoder =
                DagGifFrameDecoder { _, timeline, inspectFrame ->
                    for ((index, value) in frameValues.take(timeline.frames.size).withIndex()) {
                        onDecoded()
                        val pixels =
                            ByteArray(DagImageDecodeContract.PreparedByteCount) { value.toByte() }
                        if (!inspectFrame(timeline.frames[index], DagPreparedImage(224, 224, pixels))) {
                            return@DagGifFrameDecoder DagGifFrameDecodeResult.Stopped
                        }
                    }
                    decodeResult
                },
        )
    }

    private fun gifBytes(frameCount: Int): ByteArray {
        val output = mutableListOf<Int>()
        output += "GIF89a".map(Char::code)
        output += listOf(2, 0, 2, 0, 0x80, 0, 0)
        output += listOf(0, 0, 0, 255, 255, 255)
        repeat(frameCount) {
            output += listOf(0x21, 0xf9, 4, 0, 5, 0, 0, 0)
            output += listOf(0x2c, 0, 0, 0, 0, 2, 0, 2, 0, 0)
            output += listOf(2, 2, 0x4c, 0x01, 0)
        }
        output += 0x3b
        return output.map(Int::toByte).toByteArray()
    }
}
