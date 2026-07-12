package com.contentfilter.feature.vpn.domainlist

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebDomainListTest {
    @Test
    fun `categorizes adult mixed and dev canary while educational exception wins`() {
        val list =
            WebDomainList.parse(
                bundle(
                    version = 4,
                    adult = setOf("adult.example", "education.example"),
                    mixed = setOf("mixed.example"),
                    exceptions = setOf("education.example"),
                    canaries = setOf("canary.example"),
                ),
            )

        assertEquals(WebDomainList.CategoryAdult, list.categoryFor("www.adult.example"))
        assertEquals(WebDomainList.CategoryMixedAdult, list.categoryFor("sub.mixed.example"))
        assertEquals(WebDomainList.CategoryDevCanary, list.categoryFor("canary.example"))
        assertNull(list.categoryFor("education.example"))
        assertNull(list.categoryFor("ordinary.example"))
    }

    @Test
    fun `removing canary changes decision without application code`() {
        val included = WebDomainList.parse(bundle(version = 1, canaries = setOf("canary.example")))
        val removed = WebDomainList.parse(bundle(version = 2))

        assertEquals(WebDomainList.CategoryDevCanary, included.categoryFor("canary.example"))
        assertNull(removed.categoryFor("canary.example"))
    }

    @Test
    fun `valid signatures are accepted and tampered signatures rejected`() {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val payload =
            JSONObject().put("version", 9).put("environment", "DEV").put("dataUrl", "https://example.test/list.bin")
                .put("sizeBytes", 3).put("sha256", "abc").put("dataSignature", "signature").put("totalCount", 2)
                .put("formatVersion", 1).put("signatureStatus", "valid").toString().encodeToByteArray()
        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(payload)
                sign()
            }
        val envelope =
            JSONObject().put("signedPayload", Base64.getEncoder().encodeToString(payload))
                .put("manifestSignature", Base64.getEncoder().encodeToString(signature)).toString().encodeToByteArray()
        val verifier = DomainListManifestVerifier(Base64.getEncoder().encodeToString(keyPair.public.encoded))

        assertEquals(9, verifier.verifyAndParse(envelope).version)
        val tampered =
            JSONObject(
                envelope.decodeToString(),
            ).put(
                "manifestSignature",
                Base64.getEncoder().encodeToString(
                    signature.copyOf().also {
                        it[it.lastIndex] = (it.last() + 1).toByte()
                    },
                ),
            ).toString().encodeToByteArray()
        assertFails { verifier.verifyAndParse(tampered) }
    }

    @Test
    fun `data signature rejects changed bytes`() {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val data = byteArrayOf(1, 2, 3)
        val signature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(data)
                sign()
            }
        val verifier = DomainListManifestVerifier(Base64.getEncoder().encodeToString(keyPair.public.encoded))

        assertTrue(verifier.verifyData(data, Base64.getEncoder().encodeToString(signature)))
        assertTrue(!verifier.verifyData(byteArrayOf(1, 2, 4), Base64.getEncoder().encodeToString(signature)))
    }

    private fun bundle(
        version: Long,
        adult: Set<String> = emptySet(),
        mixed: Set<String> = emptySet(),
        exceptions: Set<String> = emptySet(),
        canaries: Set<String> = emptySet(),
    ): ByteArray {
        val bitCount = 1_024
        val adultBits = bloom(adult, bitCount)
        val mixedBits = bloom(mixed, bitCount)
        val exceptionBytes = exceptions.joinToString("\n").encodeToByteArray()
        val canaryBytes = canaries.joinToString("\n").encodeToByteArray()
        return ByteBuffer.allocate(
            WebDomainList.HeaderSize + adultBits.size + mixedBits.size + exceptionBytes.size + canaryBytes.size,
        )
            .order(ByteOrder.BIG_ENDIAN)
            .put(WebDomainList.Magic).putLong(version).putInt(1).putInt(7).putInt(bitCount).putInt(bitCount)
            .putInt(adult.size).putInt(mixed.size).putInt(exceptionBytes.size).putInt(canaryBytes.size)
            .put(adultBits).put(mixedBits).put(exceptionBytes).put(canaryBytes).array()
    }

    private fun bloom(
        values: Set<String>,
        bitCount: Int,
    ): ByteArray =
        ByteArray(bitCount / 8).also { bits ->
            values.forEach { value ->
                BloomFilter.indexes(value, bitCount, 7).forEach {
                        bit ->
                    bits[bit ushr 3] = (bits[bit ushr 3].toInt() or (1 shl (bit and 7))).toByte()
                }
            }
        }
}
