package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChromePhotosProxyRequestTest {
    @Test
    fun `parses fixture origin-form request without decoding sensitive headers`() {
        assertEquals(
            ChromePhotosProxyRequest("GET", "/safe-a.png?copy=2"),
            ChromePhotosProxyRequest.parse("GET /safe-a.png?copy=2 HTTP/1.1"),
        )
    }

    @Test
    fun `rejects absolute-form and malformed requests inside tunnel`() {
        assertNull(ChromePhotosProxyRequest.parse("GET https://other.example/image.png HTTP/1.1"))
        assertNull(ChromePhotosProxyRequest.parse("garbage"))
    }
}
