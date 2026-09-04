package com.contentfilter.user.chromedataplane

import com.glosh.visual.GloshiaImageBounds
import com.glosh.visual.GloshiaImageContract
import com.glosh.visual.GloshiaImagePreprocessResult
import com.glosh.visual.GloshiaImagePreprocessor
import com.glosh.visual.GloshiaPreparedImage
import com.glosh.visual.GloshiaVisualAnalysisResult
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.GloshiaVisualModelInfo
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromePhotosGloshiaDecisionEngineTest {
    @Test
    fun `shared R3_1 allow maps to SAFE and wipes prepared bytes`() {
        val prepared = preparedResult("image/png")
        val engine = engine(prepared, probabilities = listOf(0.2f))

        val result = engine.decide("png".toByteArray(), "image/png")

        assertEquals(ChromePhotoDecision.Safe, result.decision)
        assertEquals("model_allow", result.reason)
        assertEquals(GloshiaVisualModelInfo.ModelSha256, engine.identity.modelSha256)
        assertTrue(prepared.images().all { image -> image.rgb888.all { it == 0.toByte() } })
    }

    @Test
    fun `shared R3_1 model filter maps to BLOCK without changing thresholds`() {
        val result =
            engine(preparedResult("image/jpeg"), probabilities = listOf(0.4f))
                .decide("jpeg".toByteArray(), "image/jpeg")

        assertEquals(ChromePhotoDecision.Block, result.decision)
        assertEquals("model_filter", result.reason)
        assertEquals(0.4f, result.filterProbability)
        assertEquals("FullThreshold", result.basis)
    }

    @Test
    fun `individual model execution failure maps to UNKNOWN without declaring engine unhealthy`() {
        val analyzer = GloshiaVisualAnalyzer { GloshiaVisualAnalysisResult.Unavailable("model_execution_failed") }
        val engine = ChromePhotosGloshiaDecisionEngine(analyzer, preprocessor(preparedResult("image/webp")))
        val result = engine.decide("webp".toByteArray(), "image/webp")

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals("model_execution_failed", result.reason)
        assertEquals(ChromePhotoDecisionSource.Error, result.source)
        assertTrue(engine.isHealthy())
    }

    @Test
    fun `systemic analyzer unavailable marks engine unhealthy`() {
        val analyzer = GloshiaVisualAnalyzer { GloshiaVisualAnalysisResult.Unavailable("analyzer_unavailable") }
        val engine = ChromePhotosGloshiaDecisionEngine(analyzer, preprocessor(preparedResult("image/png")))

        val result = engine.decide("png".toByteArray(), "image/png")

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertTrue(!engine.isHealthy())
    }

    @Test
    fun `decode rejection and MIME mismatch fail closed`() {
        val rejected =
            ChromePhotosGloshiaDecisionEngine(
                analyzer = analyzer(listOf(0.1f)),
                preprocessor = GloshiaImagePreprocessor { GloshiaImagePreprocessResult.Rejected("decode_failed") },
            ).decide("bad".toByteArray(), "image/png")
        val mismatched =
            engine(preparedResult("image/jpeg"), listOf(0.1f))
                .decide("bad".toByteArray(), "image/png")

        assertEquals(ChromePhotoDecision.Unknown, rejected.decision)
        assertEquals("decode_failed", rejected.reason)
        assertEquals(ChromePhotoDecision.Unknown, mismatched.decision)
        assertEquals("invalid_decoded_image", mismatched.reason)
    }

    @Test
    fun `unsupported format is rejected before decode`() {
        val preprocessCalls = AtomicInteger()
        val engine =
            ChromePhotosGloshiaDecisionEngine(
                analyzer = analyzer(listOf(0.1f)),
                preprocessor =
                    GloshiaImagePreprocessor {
                        preprocessCalls.incrementAndGet()
                        preparedResult("image/gif")
                    },
            )

        val result = engine.decide("video".toByteArray(), "video/mp4")

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals("unsupported_mime", result.reason)
        assertEquals(0, preprocessCalls.get())
    }

    @Test
    fun `animated GIF analyzes bounded frames and allows only after all sampled frames pass`() {
        val decodedFrames = AtomicInteger()
        val decoder = fakeGifDecoder(decodedFrames)
        val engine =
            ChromePhotosGloshiaDecisionEngine(
                analyzer = analyzer(listOf(0.1f, 0.1f)),
                preprocessor = GloshiaImagePreprocessor { error("animated GIF must use frame decoder") },
                gifFrameDecoder = decoder,
            )

        val result = engine.decide(animatedGif(), "image/gif")

        assertEquals(ChromePhotoDecision.Safe, result.decision)
        assertEquals("model_allow", result.reason)
        assertEquals(2, decodedFrames.get())
    }

    @Test
    fun `animated GIF blocks as soon as a sampled frame is unsafe`() {
        val decodedFrames = AtomicInteger()
        val engine =
            ChromePhotosGloshiaDecisionEngine(
                analyzer = analyzer(listOf(0.8f)),
                preprocessor = GloshiaImagePreprocessor { error("animated GIF must use frame decoder") },
                gifFrameDecoder = fakeGifDecoder(decodedFrames),
            )

        val result = engine.decide(animatedGif(), "image/gif")

        assertEquals(ChromePhotoDecision.Block, result.decision)
        assertEquals("model_filter", result.reason)
        assertEquals(1, decodedFrames.get())
    }

    @Test
    fun `oversized decoded dimensions fail closed before inference`() {
        val analyzerCalls = AtomicInteger()
        val result =
            ChromePhotosGloshiaDecisionEngine(
                analyzer =
                    GloshiaVisualAnalyzer {
                        analyzerCalls.incrementAndGet()
                        GloshiaVisualAnalysisResult.Classified(0.1f)
                    },
                preprocessor =
                    preprocessor(
                        preparedResult(
                            mime = "image/avif",
                            width = GloshiaImageContract.MaxDimension + 1,
                        ),
                    ),
            ).decide("avif".toByteArray(), "image/avif")

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals("invalid_decoded_image", result.reason)
        assertEquals(0, analyzerCalls.get())
    }

    @Test
    fun `close is idempotent and later decisions stay UNKNOWN`() {
        val analyzer = CloseableAnalyzer()
        val engine = ChromePhotosGloshiaDecisionEngine(analyzer, preprocessor(preparedResult("image/png")))

        engine.close()
        engine.close()
        val result = engine.decide("png".toByteArray(), "image/png")

        assertEquals(1, analyzer.closeCalls.get())
        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals("analyzer_closed", result.reason)
    }

    private fun engine(
        prepared: GloshiaImagePreprocessResult.Ready,
        probabilities: List<Float>,
    ) = ChromePhotosGloshiaDecisionEngine(analyzer(probabilities), preprocessor(prepared))

    private fun analyzer(probabilities: List<Float>): GloshiaVisualAnalyzer {
        val values = ArrayDeque(probabilities)
        return GloshiaVisualAnalyzer { GloshiaVisualAnalysisResult.Classified(values.removeFirst()) }
    }

    private fun preprocessor(result: GloshiaImagePreprocessResult) = GloshiaImagePreprocessor { result }

    private fun preparedResult(
        mime: String,
        width: Int = 640,
        height: Int = 480,
    ) = GloshiaImagePreprocessResult.Ready(
        image = preparedImage(1),
        regionalImages = emptyList(),
        sourceBounds = GloshiaImageBounds(width, height, mime),
    )

    private fun preparedImage(fill: Int) =
        GloshiaPreparedImage(
            width = GloshiaImageContract.TargetSize,
            height = GloshiaImageContract.TargetSize,
            rgb888 = ByteArray(GloshiaImageContract.PreparedByteCount) { fill.toByte() },
        )

    private fun fakeGifDecoder(decodedFrames: AtomicInteger) =
        object : ChromeGifFrameDecoder {
            override fun decode(
                bytes: ByteArray,
                timeline: ChromeGifTimeline,
                inspectFrame: (ChromeGifFrame, GloshiaPreparedImage) -> Boolean,
            ): ChromeGifFrameDecodeResult {
                timeline.frames.forEachIndexed { index, frame ->
                    decodedFrames.incrementAndGet()
                    if (!inspectFrame(frame, preparedImage(index + 1))) {
                        return ChromeGifFrameDecodeResult.Stopped
                    }
                }
                return ChromeGifFrameDecodeResult.Completed
            }
        }

    private fun animatedGif() = byteArrayOf(
        *"GIF89a".toByteArray(), 1, 0, 1, 0, 0x80.toByte(), 0, 0,
        0, 0, 0, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 1, 0x44, 0,
        0x21, 0xf9.toByte(), 4, 0, 5, 0, 0, 0,
        0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 1, 0x4c, 0,
        0x3b,
    )

    private fun GloshiaImagePreprocessResult.Ready.images() = listOf(image) + regionalImages

    private class CloseableAnalyzer : GloshiaVisualAnalyzer, Closeable {
        val closeCalls = AtomicInteger()

        override fun analyze(image: GloshiaPreparedImage) = GloshiaVisualAnalysisResult.Classified(0.1f)

        override fun close() {
            closeCalls.incrementAndGet()
        }
    }
}
