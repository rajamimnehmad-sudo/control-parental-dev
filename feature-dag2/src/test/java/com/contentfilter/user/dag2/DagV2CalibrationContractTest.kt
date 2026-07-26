package com.contentfilter.user.dag2

import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagV2CalibrationContractTest {
    @Test
    fun `transport diagnostics are sanitized and contain no destination data`() {
        assertEquals("network_timeout", SocketTimeoutException("secret.example/path").dagV2CalibrationTransportReason())
        assertEquals("network_dns", UnknownHostException("secret.example").dagV2CalibrationTransportReason())
        assertEquals("network_failure", IllegalStateException("private payload").dagV2CalibrationTransportReason())
    }

    @Test
    fun `wire labels map exactly and unsure is excluded from binary evidence`() {
        assertEquals("show", DagV2CalibrationDecision.Show.wireValue)
        assertEquals("hide", DagV2CalibrationDecision.Hide.wireValue)
        assertEquals("unsure", DagV2CalibrationDecision.Unsure.wireValue)
        assertTrue(DagV2CalibrationDecision.Show.isTrainingExample())
        assertTrue(DagV2CalibrationDecision.Hide.isTrainingExample())
        assertFalse(DagV2CalibrationDecision.Unsure.isTrainingExample())
    }

    @Test
    fun `hundreds of labels cannot activate an image path model or threshold`() {
        val provider = DagV2FailClosedImageDecisionProvider()

        repeat(500) {
            DagV2CalibrationDecision.entries.forEach { decision ->
                decision.isTrainingExample()
                assertEquals(DagV2ImageDecision.Hide, provider.decide())
            }
        }
    }

    @Test
    fun `exact and perceptual fingerprints deduplicate within documented distance`() {
        val deduplicator = DagV2CalibrationLocalDeduplicator()
        val original = DagV2CalibrationFingerprintResult("a".repeat(64), "0000000000000000")
        deduplicator.remember(original)

        assertEquals(original.contentSha256, deduplicator.equivalent(original))
        assertEquals(
            original.contentSha256,
            deduplicator.equivalent(
                DagV2CalibrationFingerprintResult("b".repeat(64), "000000000000001f"),
            ),
        )
        assertEquals(
            null,
            deduplicator.equivalent(
                DagV2CalibrationFingerprintResult("c".repeat(64), "000000000000003f"),
            ),
        )
    }

    @Test
    fun `dhash hamming distance is deterministic`() {
        assertEquals(0, DagV2CalibrationFingerprint.hammingDistance("0123456789abcdef", "0123456789abcdef"))
        assertEquals(64, DagV2CalibrationFingerprint.hammingDistance("0000000000000000", "ffffffffffffffff"))
    }

    @Test
    fun `preview fetch is reachable only through explicit candidate action`() {
        val controller = source("DagV2CalibrationController.kt")
        val pipeline = source("DagV2ImagePipeline.kt")

        assertTrue(controller.contains("fun openCandidate(candidateId: String)"))
        assertTrue(controller.contains("fetcher.fetch(candidate)"))
        assertFalse(pipeline.contains("DagV2CalibrationImageFetcher"))
        assertFalse(pipeline.contains("jpegBytes"))
    }

    @Test
    fun `real preview is native and never written into WebView`() {
        val screen = source("DagV2LabScreen.kt")
        val pipeline = source("DagV2ImagePipeline.kt")

        assertTrue(screen.contains("Dialog(onDismissRequest = onClose)"))
        assertTrue(screen.contains("BitmapFactory.decodeByteArray"))
        assertFalse(screen.contains("evaluateJavascript") && screen.contains("preview.jpegBytes"))
        assertTrue(pipeline.contains("neutralImageFactory.create()"))
        assertFalse(pipeline.contains("DagV2CalibrationNormalizedImage"))
    }

    @Test
    fun `outbox is separate encrypted and cannot reuse v1 store`() {
        val outbox = source("DagV2CalibrationOutbox.kt")

        assertTrue(outbox.contains("AES/GCM/NoPadding"))
        assertTrue(outbox.contains("noBackupFilesDir"))
        assertTrue(outbox.contains("DagV2CalibrationOutboxNamespace"))
        assertFalse(outbox.contains("DagCalibrationOutboxStore"))
        assertFalse(outbox.contains("\"dag-calibration-outbox\""))
        assertFalse(outbox.contains(".writeText("))
    }

    @Test
    fun `payload omits private navigation material and model activation fields`() {
        val gateway = source("DagV2CalibrationGateway.kt")

        listOf(
            "\"resource_url\"",
            "\"document_url\"",
            "\"query\"",
            "\"cookies\"",
            "\"referer\"",
            "\"headers\"",
            "\"model_version\"",
            "\"thresholds\"",
        ).forEach { forbidden -> assertFalse(gateway.contains(forbidden), forbidden) }
        assertTrue(gateway.contains("\"source_url_hash\""))
        assertTrue(gateway.contains("\"review_decision\""))
    }

    @Test
    fun `preview network pins public DNS and validates every redirect`() {
        val fetcher = source("DagV2CalibrationImageFetcher.kt")

        assertTrue(fetcher.contains(".dns(DagV2PublicOnlyDns)"))
        assertTrue(fetcher.contains("destinationGuard.validateNavigation(currentUrl)"))
        assertTrue(fetcher.contains("URI(currentUrl).resolve(location)"))
        assertTrue(fetcher.contains("MaxRedirects"))
        assertTrue(fetcher.contains("matchesRasterSignature"))
        assertFalse(fetcher.contains("followRedirects(true)"))
    }

    private fun source(name: String): String = File("src/main/java/com/contentfilter/user/dag2/$name").readText()
}
