package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagV2ResourceRouterTest {
    private val sessions = DagV2DocumentSession()
    private val metrics = DagV2Metrics()
    private val neutral = DagV2NeutralImageFactory()
    private val pipeline =
        DagV2ImagePipeline(
            DagV2FailClosedImageDecisionProvider(),
            neutral,
            sessions,
            metrics,
        )
    private val router = DagV2ResourceRouter(pipeline, metrics, sessions)

    @Test
    fun `html scripts json rsc fetch xhr and fonts bypass immediately`() {
        val cases =
            listOf(
                request("https://example.com/", "text/html", "document", mainFrame = true),
                request("https://example.com/app.js", "application/javascript", "script"),
                request("https://example.com/api", "application/json", "empty"),
                request("https://example.com/rsc", "text/x-component", "empty"),
                request("https://example.com/font.woff2", "font/woff2", "font"),
            )

        cases.forEach {
            val kind = router.classify(it)
            assertEquals(DagV2ResourceRoute.Bypass, router.route(kind, it.url))
        }
    }

    @Test
    fun `webview and service worker images share the visual route`() {
        val webView = request("https://example.com/photo?id=1", "image/avif,image/webp,*/*", "image")
        val worker = webView.copy(source = DagV2ResourceSource.ServiceWorker)

        assertEquals(DagV2ResourceKind.RasterImage, router.classify(webView))
        assertEquals(router.classify(webView), router.classify(worker))
        assertEquals(
            router.route(router.classify(webView), webView.url),
            router.route(router.classify(worker), worker.url),
        )
        assertEquals(DagV2ResourceRoute.VisualPipeline, router.route(router.classify(webView), webView.url))
    }

    @Test
    fun `broad browser image accept header does not trigger svg download for raster`() {
        val request =
            request(
                "https://example.com/photo.jpg",
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*",
                "image",
            )

        assertEquals(DagV2ResourceKind.RasterImage, router.classify(request))
    }

    @Test
    fun `accept image and extension fallback detect images without relying on one signal`() {
        val acceptOnly = request("https://example.com/resource", "image/*", "")
        val extensionOnly = request("https://example.com/photo.webp", "*/*", "")

        assertEquals(DagV2ResourceKind.RasterImage, router.classify(acceptOnly))
        assertEquals(DagV2ResourceKind.RasterImage, router.classify(extensionOnly))
    }

    @Test
    fun `known fetch destination is not converted to image only because its url ends in jpg`() {
        val request = request("https://example.com/api/photo.jpg", "application/json", "empty")

        assertEquals(DagV2ResourceKind.NonVisual, router.classify(request))
        assertEquals(DagV2ResourceRoute.Bypass, router.route(router.classify(request), request.url))
    }

    @Test
    fun `url without extension is not visual without header evidence`() {
        val request = request("https://example.com/resource", "*/*", "empty")

        assertEquals(DagV2ResourceKind.NonVisual, router.classify(request))
        assertEquals(DagV2ResourceRoute.Bypass, router.route(router.classify(request), request.url))
    }

    @Test
    fun `service worker request without an active dag2 session bypasses the global client`() {
        val request =
            request("https://example.com/photo.jpg", "image/*", "image")
                .copy(source = DagV2ResourceSource.ServiceWorker)

        assertNull(router.intercept(request))
    }

    @Test
    fun `old visual request cannot alter pending counters for a new session`() {
        val first = sessions.start("https://example.com/first")
        router.onNewDocument(first)
        val oldRequest =
            request("https://example.com/old.jpg", "image/*", "image")
                .copy(sessionId = first.sessionId, navigationToken = first.navigationToken)
        sessions.cancelActive()
        val second = sessions.start("https://example.com/second")
        router.onNewDocument(second)

        router.intercept(oldRequest)

        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(0, metrics.snapshot.value.visualPendingCount)
    }

    @Test
    fun `active service worker request uses the same fail closed visual pipeline`() {
        val session = sessions.start("https://example.com/products")
        router.onNewDocument(session)
        val request =
            request("https://example.com/photo.jpg", "image/*", "image")
                .copy(
                    source = DagV2ResourceSource.ServiceWorker,
                    sessionId = session.sessionId,
                    navigationToken = session.navigationToken,
                )

        val response = router.intercept(request)

        assertNotNull(response)
        assertEquals(1, metrics.snapshot.value.serviceWorkerRequestCount)
        assertEquals(1, metrics.snapshot.value.imagePlaceholderCount)
    }

    @Test
    fun `fail closed provider has no approved image path`() {
        val provider = DagV2FailClosedImageDecisionProvider()

        assertEquals(DagV2ImageDecision.Hide, provider.decide())
    }

    @Test
    fun `neutral placeholder has no source pixels`() {
        val sourceMarker = "ORIGINAL_RASTER_SECRET".encodeToByteArray()
        val first = neutral.bytesForTest()
        val second = neutral.bytesForTest()

        assertContentEquals(first, second)
        assertFalse(first.decodeToString().contains(sourceMarker.decodeToString()))
        assertTrue(first.decodeToString().contains("#E9EDF2"))
    }

    private fun request(
        url: String,
        accept: String,
        destination: String,
        mainFrame: Boolean = false,
    ) = DagV2ResourceRequest(
        url = url,
        headers = mapOf("Accept" to accept, "Sec-Fetch-Dest" to destination),
        isForMainFrame = mainFrame,
        source = DagV2ResourceSource.WebView,
    )
}
