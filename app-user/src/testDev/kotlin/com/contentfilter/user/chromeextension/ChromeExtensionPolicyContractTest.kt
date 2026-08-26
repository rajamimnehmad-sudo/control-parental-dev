package com.contentfilter.user.chromeextension

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ChromeExtensionPolicyContractTest {
    @Test
    fun `adds only the requested lab extension and preserves unrelated entries`() {
        val result =
            ChromeExtensionPolicyContract.forceList(
                previous = arrayOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://example.test/update.xml"),
                extensionId = ExtensionId,
                updateUrl = UpdateUrl,
            )

        assertContentEquals(
            arrayOf(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;https://example.test/update.xml",
                "$ExtensionId;$UpdateUrl",
            ),
            result,
        )
    }

    @Test
    fun `replaces only an existing entry for the same extension`() {
        val result =
            ChromeExtensionPolicyContract.forceList(
                previous = arrayOf("$ExtensionId;http://127.0.0.1:9999/old.xml"),
                extensionId = ExtensionId,
                updateUrl = UpdateUrl,
            )

        assertContentEquals(arrayOf("$ExtensionId;$UpdateUrl"), result)
    }

    @Test
    fun `rejects non-loopback update URLs`() {
        assertFailsWith<IllegalArgumentException> {
            ChromeExtensionPolicyContract.forceList(emptyArray(), ExtensionId, "https://example.test/update.xml")
        }
    }

    private companion object {
        const val ExtensionId = "hdjdhkkibdhlmmoemopmbgiklklkpofp"
        const val UpdateUrl = "http://127.0.0.1:8765/update.xml"
    }
}
