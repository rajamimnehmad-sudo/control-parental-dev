package com.contentfilter.user.chromeextension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChromeExtensionPolicyContractTest {
    @Test
    fun `encodes one entry as an Android JSON string`() {
        val result =
            ChromeExtensionPolicyContract.mutation(
                previousValue = null,
                extensionId = ExtensionId,
                updateUrl = UpdateUrl,
            )

        assertIs<String>(result.appliedValue)
        assertEquals("[\"$ExtensionId;$UpdateUrl\"]", result.appliedValue)
        assertFalse(result.legacyTypeDetected)
    }

    @Test
    fun `preserves multiple unrelated entries in order`() {
        val previous =
            "[\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://one.test/update.xml\"," +
                "\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb;https://two.test/update.xml\"]"

        val result = ChromeExtensionPolicyContract.mutation(previous, ExtensionId, UpdateUrl)

        assertEquals(
            listOf(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://one.test/update.xml",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb;https://two.test/update.xml",
                "$ExtensionId;$UpdateUrl",
            ),
            ChromeExtensionPolicyContract.entries(result.appliedValue),
        )
    }

    @Test
    fun `replaces only an existing entry for the same extension`() {
        val unrelated = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://example.test/update.xml"
        val result =
            ChromeExtensionPolicyContract.mutation(
                previousValue = "[\"$ExtensionId;http://127.0.0.1:9999/old.xml\",\"$unrelated\"]",
                extensionId = ExtensionId,
                updateUrl = UpdateUrl,
            )

        assertEquals(
            listOf(unrelated, "$ExtensionId;$UpdateUrl"),
            ChromeExtensionPolicyContract.entries(result.appliedValue),
        )
    }

    @Test
    fun `migrates only the legacy StringArray produced by the DEV harness`() {
        val result =
            ChromeExtensionPolicyContract.mutation(
                previousValue = arrayOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://example.test/update.xml"),
                extensionId = ExtensionId,
                updateUrl = UpdateUrl,
            )

        assertTrue(result.legacyTypeDetected)
        assertIs<String>(result.appliedValue)
        assertEquals(2, ChromeExtensionPolicyContract.entries(result.appliedValue).size)
    }

    @Test
    fun `invalid JSON fails closed`() {
        val invalidValues =
            listOf(
                "not-json",
                "{}",
                "[\"valid\", 7]",
                "[\"valid\"] trailing",
            )

        invalidValues.forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) {
                ChromeExtensionPolicyContract.mutation(invalid, ExtensionId, UpdateUrl)
            }
        }
    }

    @Test
    fun `mutation retains the exact previous value for Bundle restoration`() {
        val previous = "[  \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://example.test/update.xml\" ]"

        val result = ChromeExtensionPolicyContract.mutation(previous, ExtensionId, UpdateUrl)

        assertSame(previous, result.previousValue)
    }

    @Test
    fun `rejects non-loopback update URLs`() {
        assertFailsWith<IllegalArgumentException> {
            ChromeExtensionPolicyContract.mutation(null, ExtensionId, "https://example.test/update.xml")
        }
    }

    private companion object {
        const val ExtensionId = "hdjdhkkibdhlmmoemopmbgiklklkpofp"
        const val UpdateUrl = "http://127.0.0.1:8765/update.xml"
    }
}
