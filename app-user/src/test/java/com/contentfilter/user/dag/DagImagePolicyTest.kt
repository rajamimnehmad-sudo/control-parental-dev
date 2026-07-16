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
        assertEquals(DagImageDecision.Uncertain, dagImageDecision(0.081f))
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
        assertEquals(DagImageDecision.Uncertain, dagProfessionalImageDecision(0.081f))
        assertEquals(
            DagImageDecision.Blocked,
            dagProfessionalImageDecision(DagProfessionalImageClassifier.BlockThreshold),
        )
        assertEquals(DagImageDecision.Uncertain, dagProfessionalImageDecision(Float.NaN))
    }

    @Test
    fun `professional probability conversion is stable`() {
        assertTrue(binarySoftmaxFirst(4f, -4f) > 0.99f)
        assertTrue(binarySoftmaxFirst(-4f, 4f) < 0.01f)
    }

    @Test
    fun `image requests are detected by accept header or extension`() {
        assertTrue(isProbableImageRequest("https://cdn.example/resource", mapOf("Accept" to "image/avif,image/webp")))
        assertTrue(isProbableImageRequest("https://cdn.example/photo.JPG?width=200", emptyMap()))
        assertTrue(isProbableImageRequest("https://cdn.example/photo.heic", emptyMap()))
        assertFalse(isProbableImageRequest("https://cdn.example/site.css", mapOf("Accept" to "text/css")))
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
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
