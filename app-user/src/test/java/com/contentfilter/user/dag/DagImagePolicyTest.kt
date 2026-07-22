package com.contentfilter.user.dag

import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagImagePolicyTest {
    @Test
    fun `professional model artifact is pinned`() {
        val relative = "src/main/assets/dag/nsfw_marqo_vit_tiny_384.onnx"
        val model = listOf(File(relative), File("app-user/$relative")).first(File::isFile)
        val digest = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).toHex()

        assertEquals("0366969ece89f252f05fad2c730d6c7e3373000e1ff43e4cfab8425aad94405b", digest)
    }

    @Test
    fun `only low unsafe scores are allowed`() {
        assertEquals(DagImageDecision.Allowed, dagImageDecision(0f))
        assertEquals(DagImageDecision.Allowed, dagImageDecision(DagImageClassifier.SafeThreshold))
        assertEquals(DagImageDecision.Uncertain, dagImageDecision(0.151f))
        assertEquals(DagImageDecision.Blocked, dagImageDecision(DagImageClassifier.BlockThreshold))
    }

    @Test
    fun `invalid model output fails closed`() {
        assertEquals(DagImageDecision.Uncertain, dagImageDecision(Float.NaN))
        assertEquals(DagImageDecision.Uncertain, dagImageDecision(-0.1f))
        assertEquals(DagImageDecision.Uncertain, dagImageDecision(1.1f))
    }

    @Test
    fun `professional model only allows extremely low unsafe probability`() {
        assertEquals(DagImageDecision.Allowed, dagProfessionalImageDecision(0f))
        assertEquals(
            DagImageDecision.Allowed,
            dagProfessionalImageDecision(DagProfessionalImageClassifier.SafeThreshold),
        )
        assertEquals(DagImageDecision.Uncertain, dagProfessionalImageDecision(0.151f))
        assertEquals(
            DagImageDecision.Blocked,
            dagProfessionalImageDecision(DagProfessionalImageClassifier.BlockThreshold),
        )
        assertEquals(DagImageDecision.Uncertain, dagProfessionalImageDecision(Float.NaN))
    }

    @Test
    fun `active DAG calibration changes thresholds without replacing the model`() {
        val calibration =
            DagImageCalibration(
                professionalSafe = 0.25f,
                professionalBlock = 0.55f,
                femaleBreastCovered = 0.40f,
            )

        assertEquals(DagImageDecision.Allowed, dagProfessionalImageDecision(0.20f, calibration))
        assertEquals(DagImageDecision.Blocked, dagProfessionalImageDecision(0.60f, calibration))
        assertFalse(
            requiresKosherModestyBlur(
                DagModestyScores(femaleBreastCovered = 0.35f),
                calibration,
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleBreastCovered = 0.45f),
                calibration,
            ),
        )
    }

    @Test
    fun `unsafe calibration extremes are bounded before classification`() {
        val calibration =
            DagImageCalibration(
                version = 3,
                professionalSafe = 0.05f,
                professionalBlock = 0.05f,
                femaleBreastCovered = 0.0022f,
                sleevesAboveElbow = 0.01f,
            ).withSafeBounds()

        assertEquals(3, calibration.version)
        assertEquals(0.05f, calibration.professionalSafe)
        assertEquals(0.35f, calibration.professionalBlock)
        assertEquals(0.08f, calibration.femaleBreastCovered)
        assertEquals(0.45f, calibration.sleevesAboveElbow)
    }

    @Test
    fun `professional probability conversion is stable`() {
        assertTrue(binarySoftmaxFirst(4f, -4f) > 0.99f)
        assertTrue(binarySoftmaxFirst(-4f, 4f) < 0.01f)
    }

    @Test
    fun `professional decision is authoritative and legacy resolves uncertainty`() {
        assertEquals(
            DagImageDecision.Allowed,
            dagEnsembleImageDecision(DagImageDecision.Allowed, DagImageDecision.Allowed),
        )
        assertEquals(
            DagImageDecision.Blocked,
            dagEnsembleImageDecision(DagImageDecision.Blocked, DagImageDecision.Allowed),
        )
        assertEquals(
            DagImageDecision.Allowed,
            dagEnsembleImageDecision(DagImageDecision.Allowed, DagImageDecision.Uncertain),
        )
        assertEquals(
            DagImageDecision.Blocked,
            dagEnsembleImageDecision(DagImageDecision.Blocked, DagImageDecision.Blocked),
        )
        assertEquals(
            DagImageDecision.Blocked,
            dagEnsembleImageDecision(DagImageDecision.Uncertain, DagImageDecision.Blocked),
        )
    }

    @Test
    fun `image requests are detected by accept header or extension`() {
        assertTrue(isProbableImageRequest("https://cdn.example/resource", mapOf("Accept" to "image/avif,image/webp")))
        assertTrue(isProbableImageRequest("https://cdn.example/photo.JPG?width=200", emptyMap()))
        assertTrue(isProbableImageRequest("https://cdn.example/photo.heic", emptyMap()))
        assertTrue(isProbableImageRequest("https://cdn.example/favicon.ico", emptyMap()))
        assertFalse(isProbableImageRequest("https://cdn.example/site.css", mapOf("Accept" to "text/css")))
        assertEquals(2, DagImageDeliveryPolicy.MaximumConcurrentClassifications)
        assertTrue(
            DagImageDeliveryPolicy.MaximumConcurrentImages >=
                DagImageDeliveryPolicy.MaximumConcurrentClassifications,
        )
    }

    @Test
    fun `manual calibration lookup ignores only the image fragment`() {
        assertEquals(
            "https://cdn.example/photo.jpg?size=large",
            normalizeImageUrl("https://cdn.example/photo.jpg?size=large#preview"),
        )
    }

    @Test
    fun `viewport waits for pending images and fails closed at its deadline`() {
        assertEquals(DagViewportReadinessAction.Ready, dagViewportReadinessAction(0, 0L))
        assertEquals(DagViewportReadinessAction.Wait, dagViewportReadinessAction(2, 1_000L))
        assertEquals(
            DagViewportReadinessAction.HidePending,
            dagViewportReadinessAction(2, DagViewportReadinessPolicy.MaximumWaitMillis),
        )
        assertEquals(1, DagViewportReadinessPolicy.PreparedViewportCount)
        assertEquals(1, DagViewportReadinessPolicy.PrefetchViewportCount)
        assertTrue(DagViewportReadinessPolicy.VisualSettleMillis <= 300L)
    }

    @Test
    fun `video and audio stay blocked without relying on extension`() {
        assertTrue(isBlockedMediaRequest("https://cdn.example/stream", mapOf("accept" to "video/mp4")))
        assertTrue(isBlockedMediaRequest("https://cdn.example/file.m3u8?token=x", emptyMap()))
        assertFalse(isBlockedMediaRequest("https://cdn.example/photo.jpg", mapOf("Accept" to "image/jpeg")))
    }

    @Test
    fun `image proxy rejects local and private destinations`() {
        assertFalse(isPublicAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(isPublicAddress(InetAddress.getByName("192.168.1.10")))
        assertFalse(isPublicAddress(InetAddress.getByName("100.64.0.1")))
        assertFalse(isPublicAddress(InetAddress.getByName("fc00::1")))
        assertTrue(isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun `safe static svg icons pass and executable svg fails closed`() {
        val safe = "<svg viewBox=\"0 0 24 24\"><path d=\"M1 1h20v20z\"/></svg>".encodeToByteArray()
        val xmlSafe = "<?xml version=\"1.0\"?><svg><circle cx=\"2\" cy=\"2\" r=\"1\"/></svg>".encodeToByteArray()
        val sprite = "<svg><defs><path id=\"check\" d=\"M1 2l2 2\"/></defs><use href=\"#check\"/></svg>".encodeToByteArray()
        val gradient = "<svg><defs><linearGradient id=\"g\"/></defs><path fill=\"url(#g)\" d=\"M0 0h2v2z\"/></svg>".encodeToByteArray()
        val script = "<svg><script>alert(1)</script></svg>".encodeToByteArray()
        val external = "<svg><use href=\"https://evil.example/a.svg#x\"/></svg>".encodeToByteArray()
        val unquotedExternal = "<svg><use href=https://evil.example/a.svg#x /></svg>".encodeToByteArray()
        val externalPaint = "<svg><path fill=\"url(https://evil.example/a.svg#g)\"/></svg>".encodeToByteArray()

        assertTrue(isSafeStaticSvg(safe, "image/svg+xml; charset=utf-8"))
        assertTrue(isSafeStaticSvg(xmlSafe, "image/svg+xml"))
        assertTrue(isSafeStaticSvg(sprite, "image/svg+xml"))
        assertTrue(isSafeStaticSvg(gradient, "image/svg+xml"))
        assertFalse(isSafeStaticSvg(script, "image/svg+xml"))
        assertFalse(isSafeStaticSvg(external, "image/svg+xml"))
        assertFalse(isSafeStaticSvg(unquotedExternal, "image/svg+xml"))
        assertFalse(isSafeStaticSvg(externalPaint, "image/svg+xml"))
        assertFalse(isSafeStaticSvg(safe, "text/html"))
    }

    @Test
    fun `modesty detector blurs female covered or exposed regions but not faces alone`() {
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleFace = 0.85f, femaleBreastCovered = 0.55f),
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleFace = 0.60f, armpitsExposed = 0.25f),
            ),
        )
        assertFalse(requiresKosherModestyBlur(DagModestyScores(femaleFace = 0.90f)))
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleBreastCovered = 0.35f),
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleGenitaliaCovered = 0.35f),
            ),
        )
        assertFalse(
            requiresKosherModestyBlur(
                DagModestyScores(femaleFace = 0.90f, femaleBreastCovered = 0.10f),
            ),
        )
    }

    @Test
    fun `only genuinely doubtful modesty scores enter calibration`() {
        assertEquals(
            DagImageDecision.Uncertain,
            dagModestyImageDecision(DagModestyScores(femaleBreastCovered = 0.25f)),
        )
        assertEquals(
            DagImageDecision.Blocked,
            dagModestyImageDecision(DagModestyScores(femaleBreastCovered = 0.35f)),
        )
        assertEquals(
            DagImageDecision.Allowed,
            dagModestyImageDecision(DagModestyScores(femaleFace = 0.90f)),
        )
    }

    @Test
    fun `elbow and knee exposure require female context and use calibrated thresholds`() {
        val calibration = DagImageCalibration(sleevesAboveElbow = 0.70f, hemAboveKnee = 0.75f)

        assertFalse(
            requiresKosherModestyBlur(
                DagModestyScores(sleevesAboveElbow = 0.90f, hemAboveKnee = 0.90f),
                calibration,
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleFace = 0.80f, sleevesAboveElbow = 0.71f),
                calibration,
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(femaleFace = 0.80f, hemAboveKnee = 0.76f),
                calibration,
            ),
        )
    }

    @Test
    fun `young children allow ordinary exposed limbs but not underwear or nudity`() {
        val calibration = DagImageCalibration()
        val ordinaryChild =
            DagModestyScores(
                femaleFace = 0.90f,
                armpitsExposed = 0.90f,
                bellyExposed = 0.90f,
                sleevesAboveElbow = 0.90f,
                hemAboveKnee = 0.90f,
            )

        assertFalse(requiresKosherModestyBlur(ordinaryChild, calibration, DagImageAudienceContext.YoungChild))
        assertTrue(
            requiresKosherModestyBlur(
                ordinaryChild.copy(buttocksCovered = 0.60f),
                calibration,
                DagImageAudienceContext.YoungChild,
            ),
        )
        assertTrue(
            requiresKosherModestyBlur(
                ordinaryChild.copy(explicitRegion = 0.60f),
                calibration,
                DagImageAudienceContext.YoungChild,
            ),
        )
    }

    @Test
    fun `girls use female criteria while male chest and belly stay allowed`() {
        val calibration = DagImageCalibration()
        assertTrue(
            requiresKosherModestyBlur(
                DagModestyScores(hemAboveKnee = 0.80f),
                calibration,
                DagImageAudienceContext.FemaleSixPlus,
            ),
        )
        assertFalse(
            requiresKosherModestyBlur(
                DagModestyScores(maleFace = 0.90f, maleBreastExposed = 0.90f, bellyExposed = 0.90f),
                calibration,
                DagImageAudienceContext.Male,
            ),
        )
    }

    @Test
    fun `male chest and young child context can resolve generic model false positives`() {
        val calibration = DagImageCalibration()
        assertEquals(
            DagImageDecision.Allowed,
            dagAudienceAwareImageDecision(
                DagImageDecision.Blocked,
                DagImageDecision.Allowed,
                DagModestyScores(maleFace = 0.90f, maleBreastExposed = 0.80f),
                calibration,
                DagImageAudienceContext.Male,
            ),
        )
        assertEquals(
            DagImageDecision.Allowed,
            dagAudienceAwareImageDecision(
                DagImageDecision.Uncertain,
                DagImageDecision.Allowed,
                DagModestyScores(femaleFace = 0.90f),
                calibration,
                DagImageAudienceContext.YoungChild,
            ),
        )
    }

    @Test
    fun `audience context recognizes babies men and girls without substring collisions`() {
        assertEquals(
            DagImageAudienceContext.IntimateClothing,
            dagImageAudienceContext("https://cdn.example/look.jpg", "https://shop.example/bebes/ropa-interior"),
        )
        assertEquals(
            DagImageAudienceContext.YoungChild,
            dagImageAudienceContext("https://cdn.example/look.jpg", "https://shop.example/bebés/0-a-3"),
        )
        assertEquals(
            DagImageAudienceContext.Male,
            dagImageAudienceContext("https://cdn.example/man-shirtless.jpg", "https://shop.example/hombre"),
        )
        assertEquals(
            DagImageAudienceContext.FemaleSixPlus,
            dagImageAudienceContext("https://cdn.example/look.jpg", "https://shop.example/niñas/vestidos"),
        )
        assertEquals(
            DagImageAudienceContext.Unknown,
            dagImageAudienceContext("https://cdn.example/humanity.jpg", "https://shop.example/home"),
        )
    }

    @Test
    fun `underwear context stays blurred for children and men`() {
        val calibration = DagImageCalibration()
        val maleUnderwear = DagModestyScores(maleFace = 0.90f, maleBreastExposed = 0.90f)

        assertTrue(
            requiresKosherModestyBlur(
                maleUnderwear,
                calibration,
                DagImageAudienceContext.IntimateClothing,
            ),
        )
        assertEquals(
            DagImageDecision.Uncertain,
            dagModestyImageDecision(
                maleUnderwear,
                calibration,
                DagImageAudienceContext.IntimateClothing,
            ),
        )
    }

    @Test
    fun `human explanation signals identify the threshold that fired`() {
        val calibration = DagImageCalibration(version = 4, sleevesAboveElbow = 0.70f)
        val signals =
            dagImageDecisionSignals(
                DagImageDecision.Uncertain,
                mapOf("female_face" to 0.9f, "sleeves_above_elbow" to 0.72f),
                calibration,
            )

        assertEquals(setOf("sleeves_above_elbow"), signals)
    }

    @Test
    fun `calibration thumbnail hash is deterministic`() {
        val thumbnail = "same reviewed image".encodeToByteArray()

        assertEquals(thumbnail.dagCalibrationHash(), thumbnail.dagCalibrationHash())
        assertEquals(64, thumbnail.dagCalibrationHash().length)
    }

    @Test
    fun `strong modesty evidence blocks even when professional model is uncertain`() {
        assertEquals(
            DagImageDecision.Blocked,
            dagCombinedImageDecision(DagImageDecision.Uncertain, DagImageDecision.Blocked),
        )
        assertEquals(
            DagImageDecision.Uncertain,
            dagCombinedImageDecision(DagImageDecision.Allowed, DagImageDecision.Uncertain),
        )
    }

    @Test
    fun `modesty model artifact is pinned`() {
        val relative = "src/main/assets/dag/nudenet_modesty_320n_uint8.onnx"
        val model = listOf(File(relative), File("app-user/$relative")).first(File::isFile)
        val digest = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).toHex()

        assertEquals("2e5d2b1471903c5bad06596dbb57bc991a489e27d5b475a62269321db42f123b", digest)
    }

    @Test
    fun `pose model artifact is pinned`() {
        val relative = "src/main/assets/dag/movenet_singlepose_lightning_int8.tflite"
        val model = listOf(File(relative), File("app-user/$relative")).first(File::isFile)
        val digest = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).toHex()

        assertEquals("cd7cc22fa946e5d146a7b98d496853e1923e22828d3972d579973f27f91bb105", digest)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
