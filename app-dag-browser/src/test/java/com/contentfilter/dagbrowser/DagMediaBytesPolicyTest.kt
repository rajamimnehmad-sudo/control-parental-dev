package com.contentfilter.dagbrowser

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagMediaBytesPolicyTest {
    @Test
    fun `oversized raster cannot bypass photo inference as a passive sprite`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(6_144, 64, "image/png") },
                preprocessor = DagImagePreprocessor { error("must not preprocess unsafe geometry") },
                analyzer = DagImageAnalyzer { error("must not classify unsafe geometry") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsafeDimensionsReason, decision.reason)
        assertEquals(null, decision.replacementBytesBase64)
    }

    @Test
    fun `ordinary unsafe geometry fails closed before preprocessing`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(5_000, 5_000, "image/png") },
                preprocessor = DagImagePreprocessor { error("must not preprocess unsafe geometry") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsafeDimensionsReason, decision.reason)
    }

    @Test
    fun `DEV compatibility mode releases exact bytes without decoding or classifying`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(bytes),
                boundsReader = DagImageBoundsReader { error("must not decode") },
                preprocessor = DagImagePreprocessor { error("must not preprocess") },
                analyzer = DagImageAnalyzer { error("must not classify") },
                classificationMode = DagMediaClassificationMode.DisabledForDevCompatibility,
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagMediaBytesPolicy.DevClassifierBypassReason, decision.reason)
    }

    @Test
    fun `DEV compatibility mode still rejects an invalid transport envelope`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(0x01)).copy(sourceUrl = "file:///private.jpg"),
                classificationMode = DagMediaClassificationMode.DisabledForDevCompatibility,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.InvalidPayloadReason, decision.reason)
    }

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
    fun `small decodable thumbnail reaches model and can be released`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(96, 96, "image/webp") },
                preprocessor = readyPreprocessor,
                analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.1f) },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
    }

    @Test
    fun `uncertain full signal below threshold receives bounded regional review`() {
        var inferenceCount = 0
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        DagImageAnalysisResult.Classified(0.399f)
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.399f, decision.filterProbability)
        assertEquals(5, inferenceCount)
    }

    @Test
    fun `clear ordinary allow skips regional review`() {
        var callCount = 0
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        callCount += 1
                        DagImageAnalysisResult.Classified(0.299f)
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(1, callCount)
    }

    @Test
    fun `one strong quadrant blocks an uncertain ordinary image`() {
        val probabilities = listOf(0.336f, 0.8f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.8f, decision.filterProbability)
    }

    @Test
    fun `quadrant signal at uncertain threshold blocks an ordinary image`() {
        val probabilities = listOf(0.336f, 0.45f).iterator()
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.45f, decision.filterProbability)
        assertEquals(DagMediaDecisionBasis.UncertainRegional, trace.decisionBasis)
    }

    @Test
    fun `weak quadrants keep an uncertain ordinary image allowed`() {
        val probabilities = listOf(0.336f, 0.44f, 0.2f, 0.1f, 0.2f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.44f, decision.filterProbability)
    }

    @Test
    fun `full image signal at threshold blocks without regional work`() {
        var inferenceCount = 0
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        DagImageAnalysisResult.Classified(0.4f)
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.4f, decision.filterProbability)
        assertEquals(1, inferenceCount)
        assertEquals(DagMediaDecisionBasis.FullThreshold, trace.decisionBasis)
    }

    @Test
    fun `full image signal above threshold cannot be vetoed by contextual crops`() {
        var inferenceCount = 0
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        DagImageAnalysisResult.Classified(0.8f)
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.8f, decision.filterProbability)
        assertEquals(1, inferenceCount)
        assertEquals(DagMediaDecisionBasis.FullThreshold, trace.decisionBasis)
    }

    @Test
    fun `very strong full image signal blocks without regional veto`() {
        var callCount = 0
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        callCount += 1
                        DagImageAnalysisResult.Classified(0.95f)
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.95f, decision.filterProbability)
        assertEquals(1, callCount)
        assertEquals(DagMediaDecisionBasis.FullStrong, trace.decisionBasis)
    }

    @Test
    fun `full image signal below strong gate still blocks at canonical threshold`() {
        var inferenceCount = 0
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        DagImageAnalysisResult.Classified(0.9499f)
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.9499f, decision.filterProbability)
        assertEquals(1, inferenceCount)
        assertEquals(DagMediaDecisionBasis.FullThreshold, trace.decisionBasis)
    }

    @Test
    fun `one strong generated quadrant records its exact decision basis`() {
        val probabilities = listOf(0.336f, 0.7f).iterator()
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
                trace = trace,
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.7f, decision.filterProbability)
        assertEquals(DagMediaDecisionBasis.UncertainRegional, trace.decisionBasis)
    }

    @Test
    fun `one marginal regional signal does not block a panoramic image`() {
        val probabilities = listOf(0.27f, 0.51f, 0.2f, 0.1f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 300, "image/jpeg") },
                preprocessor = preprocessorWithRegionalImages(3),
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.51f, decision.filterProbability)
    }

    @Test
    fun `two regional signals block a risky subject reduced inside a panoramic image`() {
        val probabilities = listOf(0.27f, 0.51f, 0.52f, 0.1f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 300, "image/jpeg") },
                preprocessor = preprocessorWithRegionalImages(3),
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.52f, decision.filterProbability)
    }

    @Test
    fun `one strong regional signal still blocks a panoramic image`() {
        val probabilities = listOf(0.27f, 0.7f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 300, "image/jpeg") },
                preprocessor = preprocessorWithRegionalImages(3),
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelFilterReason, decision.reason)
        assertEquals(0.7f, decision.filterProbability)
    }

    @Test
    fun `regional views use their stricter threshold without changing ordinary decisions`() {
        val probabilities = listOf(0.2f, 0.3f, 0.49f, 0.1f).iterator()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 300, "image/jpeg") },
                preprocessor = preprocessorWithRegionalImages(3),
                analyzer =
                    DagImageAnalyzer {
                        DagImageAnalysisResult.Classified(probabilities.next())
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason)
        assertEquals(0.49f, decision.filterProbability)
    }

    @Test
    fun `required regional analysis fails closed when the model becomes unavailable`() {
        var callCount = 0
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(1_200, 300, "image/jpeg") },
                preprocessor = preprocessorWithRegionalImages(1),
                analyzer =
                    DagImageAnalyzer {
                        callCount += 1
                        if (callCount == 1) {
                            DagImageAnalysisResult.Classified(0.2f)
                        } else {
                            DagImageAnalysisResult.Unavailable("regional_unavailable")
                        }
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals("regional_unavailable", decision.reason)
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
    fun `expired work stops before base64 decode and every image decoder`() {
        val trace = DagMediaPipelineTrace()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { error("must not read bounds") },
                preprocessor = DagImagePreprocessor { error("must not decode pixels") },
                analyzer = DagImageAnalyzer { error("must not run inference") },
                trace = trace,
                workGuard = DagMediaWorkGuard { false },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.AnalysisExpiredReason, decision.reason)
        assertEquals(0, trace.inferenceCount)
    }

    @Test
    fun `deadline reached during decode erases pixels and skips inference`() {
        var current = true
        val prepared = preparedImage().also { it.rgb888.fill(91) }
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor =
                    DagImagePreprocessor {
                        current = false
                        DagImagePreprocessResult.Ready(prepared)
                    },
                analyzer = DagImageAnalyzer { error("must not run expired inference") },
                workGuard = DagMediaWorkGuard { current },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.AnalysisExpiredReason, decision.reason)
        assertTrue(prepared.rgb888.all { it == 0.toByte() })
    }

    @Test
    fun `deadline reached after full inference skips every regional inference`() {
        var current = true
        var inferenceCount = 0
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer {
                        inferenceCount += 1
                        current = false
                        DagImageAnalysisResult.Classified(0.35f)
                    },
                workGuard = DagMediaWorkGuard { current },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.AnalysisExpiredReason, decision.reason)
        assertEquals(1, inferenceCount)
    }

    @Test
    fun `decoded source bytes are erased after the decision`() {
        var decodedBytes: ByteArray? = null
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader =
                    DagImageBoundsReader { bytes ->
                        decodedBytes = bytes
                        DagImageBounds(320, 240, "image/jpeg")
                    },
                preprocessor =
                    DagImagePreprocessor {
                        DagImagePreprocessResult.Rejected("expected_test_stop")
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals("expected_test_stop", decision.reason)
        assertTrue(requireNotNull(decodedBytes).all { it == 0.toByte() })
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
    fun `bounded passive ui vector bypasses raster classifier safely`() {
        val bytes =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path d="M12 21s-8-4.5-8-11a4 4 0 0 1 8-1 4 4 0 0 1 8 1c0 6.5-8 11-8 11z"/>
            </svg>
            """.trimIndent().toByteArray()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(bytes),
                boundsReader = DagImageBoundsReader { error("must not decode vector as raster") },
                preprocessor = DagImagePreprocessor { error("must not preprocess safe vector") },
                analyzer = DagImageAnalyzer { error("must not classify safe vector") },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagMediaBytesPolicy.SafeUiVectorReason, decision.reason)
    }

    @Test
    fun `gzip encoded passive ui vector is inspected and released safely`() {
        val svg =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path d="M4 12h16M12 4v16"/>
            </svg>
            """.trimIndent().toByteArray()
        val compressed =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { it.write(svg) }
                output.toByteArray()
            }
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(compressed),
                boundsReader = DagImageBoundsReader { error("must not decode vector as raster") },
                preprocessor = DagImagePreprocessor { error("must not preprocess safe vector") },
                analyzer = DagImageAnalyzer { error("must not classify safe vector") },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(DagMediaBytesPolicy.SafeUiVectorReason, decision.reason)
    }

    @Test
    fun `oversized gzip expansion fails closed before image decoding`() {
        val compressed =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use {
                    it.write(ByteArray(DagMediaBytesPolicy.MaxCaptureBytes + 1))
                }
                output.toByteArray()
            }
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(compressed),
                boundsReader = DagImageBoundsReader { error("oversized content must not decode") },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertEquals(DagMediaBytesPolicy.UnsupportedImageReason, decision.reason)
    }

    @Test
    fun `active svg remains unsupported and fail closed`() {
        val bytes =
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
              <script>alert(1)</script>
            </svg>
            """.trimIndent().toByteArray()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(bytes),
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
    fun `static HEIF aliases reach the same bounded classifier`() {
        for (mimeType in listOf("image/heic", "image/heif")) {
            val decision =
                DagMediaBytesPolicy.decide(
                    payload = payload(byteArrayOf(1, 2, 3)),
                    boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, mimeType) },
                    preprocessor = readyPreprocessor,
                    analyzer = DagImageAnalyzer { DagImageAnalysisResult.Classified(0.2f) },
                )

            assertEquals(DagMediaAction.Allow, decision.action, mimeType)
            assertEquals(DagOnDeviceImageAnalyzer.ModelAllowReason, decision.reason, mimeType)
        }
    }

    @Test
    fun `unsupported decoded format stays blocked`() {
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/bmp") },
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
    fun `every prepared rgb view is overwritten after the fail closed decision`() {
        val fullRgb = ByteArray(DagImageDecodeContract.PreparedByteCount) { 127 }
        val regionalRgb = ByteArray(DagImageDecodeContract.PreparedByteCount) { 63 }
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
                                rgb888 = fullRgb,
                            ),
                            regionalImages =
                                listOf(
                                    DagPreparedImage(
                                        width = DagImageDecodeContract.TargetSize,
                                        height = DagImageDecodeContract.TargetSize,
                                        rgb888 = regionalRgb,
                                    ),
                                ),
                        )
                    },
            )

        assertEquals(DagMediaAction.Block, decision.action)
        assertTrue(fullRgb.all { it == 0.toByte() })
        assertTrue(regionalRgb.all { it == 0.toByte() })
    }

    @Test
    fun `generated quadrant rgb buffers are overwritten after review`() {
        val reviewedBuffers = mutableListOf<ByteArray>()
        val decision =
            DagMediaBytesPolicy.decide(
                payload = payload(byteArrayOf(1, 2, 3)),
                boundsReader = DagImageBoundsReader { DagImageBounds(320, 240, "image/jpeg") },
                preprocessor = readyPreprocessor,
                analyzer =
                    DagImageAnalyzer { image ->
                        reviewedBuffers += image.rgb888
                        DagImageAnalysisResult.Classified(0.35f)
                    },
            )

        assertEquals(DagMediaAction.Allow, decision.action)
        assertEquals(5, reviewedBuffers.size)
        assertTrue(reviewedBuffers.drop(1).all { rgb -> rgb.all { it == 0.toByte() } })
    }

    private val readyPreprocessor =
        DagImagePreprocessor {
            DagImagePreprocessResult.Ready(
                preparedImage(),
            )
        }

    private fun preprocessorWithRegionalImages(count: Int) =
        DagImagePreprocessor {
            DagImagePreprocessResult.Ready(
                image = preparedImage(),
                regionalImages = List(count) { preparedImage() },
            )
        }

    private fun preparedImage() =
        DagPreparedImage(
            width = DagImageDecodeContract.TargetSize,
            height = DagImageDecodeContract.TargetSize,
            rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount),
        )

    private fun payload(bytes: ByteArray) =
        DagMediaBytesPayload(
            candidateId = "response_1_abcd",
            sourceUrl = "https://images.example/photo.jpg",
            declaredByteLength = bytes.size,
            bytesBase64 = Base64.getEncoder().encodeToString(bytes),
        )
}
