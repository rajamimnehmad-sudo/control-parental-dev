package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromePixelProvenanceRoutingTest {
    private val origin =
        ChromePhotosFixtureOrigin(
            safeImageOverride = "safe".toByteArray(),
            sentinelImageOverride = "block".toByteArray(),
            placeholderImageOverride = "placeholder".toByteArray(),
        )

    @Test
    fun `controlled origin routes 13A without changing 11A or 11B`() {
        val provenance = origin.responseFor(ChromePhotosProxyRequest("GET", "/web13a/"))
        val web11a = origin.responseFor(ChromePhotosProxyRequest("GET", "/web11a"))
        val web11b = origin.responseFor(ChromePhotosProxyRequest("GET", "/web11b"))

        assertEquals("web13a-runner", provenance.resourceId)
        assertTrue(provenance.originalBytes.toString(Charsets.UTF_8).contains("CHROME-PROVENANCE-GAP-13A"))
        assertEquals("web11a-runner", web11a.resourceId)
        assertEquals("web11b-runner", web11b.resourceId)
    }

    @Test
    fun `13A state remains isolated from 11B report state`() {
        origin.responseFor(
            ChromePhotosProxyRequest(
                method = "POST",
                target = "/web13a/report",
                body = "DATA_URL:RENDERED,BLOB_URL:RENDERED".toByteArray(),
            ),
        )
        val provenanceState =
            origin.responseFor(ChromePhotosProxyRequest("GET", "/web13a/state"))
                .originalBytes.toString(Charsets.UTF_8)

        assertTrue(provenanceState.contains("PAGE=DATA_URL:RENDERED,BLOB_URL:RENDERED"))
        assertEquals("not_run", origin.imageAuthorityReport())
    }
}
