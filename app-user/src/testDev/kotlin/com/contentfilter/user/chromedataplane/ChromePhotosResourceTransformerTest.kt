package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotosResourceTransformerTest {
    private val safe = "safe-image-bytes".toByteArray()
    private val sentinel = "sentinel-original-bytes".toByteArray()
    private val placeholder = "neutral-placeholder".toByteArray()

    @Test
    fun `safe image is byte-identical passthrough`() {
        val result = transformer().transform("image/png", safe)

        assertEquals(ChromePhotosResourceDecision.Safe, result.decision)
        assertContentEquals(safe, result.bytes)
        assertFalse(result.cacheHit)
    }

    @Test
    fun `sentinel is replaced before delivery`() {
        val result = transformer().transform("image/png", sentinel)

        assertEquals(ChromePhotosResourceDecision.Block, result.decision)
        assertContentEquals(placeholder, result.bytes)
        assertFalse(result.bytes.contentEquals(sentinel))
    }

    @Test
    fun `unknown image fails closed to placeholder`() {
        val result = transformer().transform("image/jpeg", "unknown".toByteArray())

        assertEquals(ChromePhotosResourceDecision.Unknown, result.decision)
        assertContentEquals(placeholder, result.bytes)
    }

    @Test
    fun `non image bytes pass unchanged`() {
        val html = "<html>fixture</html>".toByteArray()
        val result = transformer().transform("text/html; charset=utf-8", html)

        assertEquals(ChromePhotosResourceDecision.Passthrough, result.decision)
        assertContentEquals(html, result.bytes)
        assertEquals(null, result.contentHash)
    }

    @Test
    fun `decision cache uses content identity and clears`() {
        val transformer = transformer()

        val first = transformer.transform("image/png", safe)
        val second = transformer.transform("image/png", safe.copyOf())

        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        assertEquals(1, transformer.cacheSize())
        transformer.clear()
        assertEquals(0, transformer.cacheSize())
    }

    private fun transformer() =
        ChromePhotosResourceTransformer(
            safeBytes = listOf(safe),
            blockedBytes = listOf(sentinel),
            placeholderBytes = placeholder,
        )
}
