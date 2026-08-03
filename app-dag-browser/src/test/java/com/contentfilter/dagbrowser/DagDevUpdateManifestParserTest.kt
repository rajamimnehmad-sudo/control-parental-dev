package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DagDevUpdateManifestParserTest {
    @Test
    fun `accepts the published DAG manifest contract`() {
        val manifest =
            DagDevUpdateManifestParser.parse(
                """
                {
                  "versionCode": 96,
                  "versionName": "0.69.0-dev",
                  "apkUrl": "https://example.test/app-dag-browser-dev-96-debug.apk",
                  "apkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "releaseNotes": "GloshIA Visual R3 Canary"
                }
                """.trimIndent(),
            )

        requireNotNull(manifest)
        assertEquals(96, manifest.versionCode)
        assertEquals("0.69.0-dev", manifest.versionName)
    }

    @Test
    fun `rejects insecure or malformed manifests`() {
        val insecure =
            """
            {
              "versionCode": 96,
              "versionName": "0.69.0-dev",
              "apkUrl": "http://example.test/dag.apk",
              "apkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent()
        val malformedHash = insecure.replace("http://", "https://").replace("aaaaaaaa", "not-a-hash")

        assertNull(DagDevUpdateManifestParser.parse(insecure))
        assertNull(DagDevUpdateManifestParser.parse(malformedHash))
        assertNull(DagDevUpdateManifestParser.parse("{}"))
    }
}
