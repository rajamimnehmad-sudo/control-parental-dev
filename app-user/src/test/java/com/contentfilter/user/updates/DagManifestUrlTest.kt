package com.contentfilter.user.updates

import kotlin.test.Test
import kotlin.test.assertEquals

class DagManifestUrlTest {
    @Test
    fun `dag manifest is beside user manifest`() {
        assertEquals(
            "https://example.test/dev-updates/app-dag-browser-dev-manifest.json",
            dagManifestUrl("https://example.test/dev-updates/app-user-dev-manifest.json"),
        )
    }

    @Test
    fun `query and fragment are not copied`() {
        assertEquals(
            "https://example.test/dev-updates/app-dag-browser-dev-manifest.json",
            dagManifestUrl("https://example.test/dev-updates/app-user-dev-manifest.json?token=test#fragment"),
        )
    }

    @Test
    fun `blank manifest remains unconfigured`() {
        assertEquals("", dagManifestUrl(""))
    }
}
