package com.contentfilter.user.dag2

import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagV2ResourceRouterTest {
    private val sessions = DagV2DocumentSession()
    private val contexts = DagV2DocumentContextRegistry()
    private val metrics = DagV2Metrics()
    private val neutral = DagV2NeutralImageFactory()
    private val pipeline =
        DagV2ImagePipeline(
            DagV2FailClosedImageDecisionProvider(),
            neutral,
            sessions,
            metrics,
        )
    private val router =
        DagV2ResourceRouter(
            pipeline,
            metrics,
            PublicNetworkDestinationGuard(),
        )
    private val interceptor = DagV2ResourceInterceptor(router, contexts)

    @Test
    fun `html scripts json rsc fetch xhr and fonts bypass immediately`() {
        val cases =
            listOf(
                request("https://example.com/", "text/html", "document", mainFrame = true),
                request("https://example.com/site.css", "text/css", "style"),
                request("https://example.com/app.js", "application/javascript", "script"),
                request("https://example.com/api", "application/json", "empty"),
                request("https://example.com/rsc", "text/x-component", "empty"),
                request("https://example.com/font.woff2", "font/woff2", "font"),
                request("https://example.com/site.webmanifest", "application/manifest+json", "manifest"),
                request("https://example.com/sw.js", "application/javascript", "serviceworker"),
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
    fun `sec fetch destination takes precedence over conflicting accept and extension hints`() {
        val script = request("https://example.com/app.jpg", "image/*", "script")
        val image = request("https://example.com/image-endpoint", "application/octet-stream", "image")

        assertEquals(DagV2ResourceKind.NonVisual, router.classify(script))
        assertEquals(DagV2ResourceKind.RasterImage, router.classify(image))
    }

    @Test
    fun `service worker without active dag2 document is left untouched`() {
        val visual = evidence("https://example.com/photo.jpg", "image/*", "image")
        val functional = evidence("https://example.com/app.js", "application/javascript", "script")

        assertNull(interceptor.interceptServiceWorker(visual))
        assertNull(interceptor.interceptServiceWorker(functional))
        assertEquals(DagV2MetricSnapshot(), metrics.snapshot.value)
    }

    @Test
    fun `unattributed functional resource bypasses without changing current metrics`() {
        start("https://current.example/products")
        val before = metrics.snapshot.value

        val response =
            interceptor.intercept(
                evidence(
                    url = "https://unrelated.example.com/app.js",
                    accept = "application/javascript",
                    destination = "script",
                ),
            )

        assertNull(response)
        assertEquals(before, metrics.snapshot.value)
    }

    @Test
    fun `unattributed visual resource fails closed without changing current metrics`() {
        start("https://current.example/products")
        val before = metrics.snapshot.value

        val response =
            interceptor.intercept(
                evidence(
                    url = "https://unrelated.example.com/photo.jpg",
                    accept = "image/*",
                    destination = "image",
                ),
            )

        assertNotNull(response)
        assertEquals(before, metrics.snapshot.value)
    }

    @Test
    fun `production interceptor rejects old webview work after a new document starts`() {
        val first = start("https://example.com/a")
        val oldEvidence =
            evidence(
                url = "https://example.com/old.jpg",
                accept = "image/*",
                destination = "image",
                referer = first.documentUrl,
            )
        cancel(first)
        val second = start("https://example.com/b")
        val before = metrics.snapshot.value

        val response = interceptor.interceptBound(oldEvidence, first)

        assertNotNull(response)
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(before, metrics.snapshot.value)
    }

    @Test
    fun `old service worker request cannot mutate the new document`() {
        val first = start("https://example.com/a")
        val oldEvidence =
            evidence(
                url = "https://example.com/old.jpg",
                accept = "image/*",
                destination = "image",
                source = DagV2ResourceSource.ServiceWorker,
                referer = first.documentUrl,
                origin = first.documentOrigin,
            )
        cancel(first)
        val second = start("https://example.com/b")
        val before = metrics.snapshot.value

        val response = interceptor.interceptServiceWorker(oldEvidence)

        assertNotNull(response)
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(before, metrics.snapshot.value)
    }

    @Test
    fun `old functional service worker request bypasses without attribution to new document`() {
        val first = start("https://example.com/a")
        val oldEvidence =
            evidence(
                url = "https://example.com/old.js",
                accept = "application/javascript",
                destination = "script",
                source = DagV2ResourceSource.ServiceWorker,
                referer = first.documentUrl,
                origin = first.documentOrigin,
            )
        cancel(first)
        val second = start("https://example.com/b")
        val before = metrics.snapshot.value

        assertNull(interceptor.interceptServiceWorker(oldEvidence))
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(before, metrics.snapshot.value)
    }

    @Test
    fun `active service worker request uses the same fail closed visual pipeline`() {
        val context = start("https://example.com/products")

        val response =
            interceptor.interceptServiceWorker(
                evidence(
                    url = "https://example.com/photo.jpg",
                    accept = "image/*",
                    destination = "image",
                    source = DagV2ResourceSource.ServiceWorker,
                    referer = context.documentUrl,
                    origin = context.documentOrigin,
                ),
            )

        assertNotNull(response)
        assertEquals(1, metrics.snapshot.value.serviceWorkerRequestCount)
        assertEquals(1, metrics.snapshot.value.imagePlaceholderCount)
    }

    @Test
    fun `private and reserved literals are blocked for webview and service worker resources`() {
        val context = start("https://example.com/products")
        val literals =
            listOf(
                "127.0.0.1",
                "127.1",
                "10.0.0.1",
                "192.0.2.1",
                "[2001:db8::1]",
                "localhost",
                "device.local",
            )

        literals.forEach { host ->
            val headers = mapOf("Referer" to context.documentUrl, "Origin" to context.documentOrigin)
            val webView =
                evidence(
                    url = "https://$host/private.js",
                    accept = "application/javascript",
                    destination = "script",
                    headers = headers,
                )
            val worker = webView.copy(source = DagV2ResourceSource.ServiceWorker)

            assertNotNull(interceptor.intercept(webView), host)
            assertNotNull(interceptor.interceptServiceWorker(worker), host)
        }
    }

    @Test
    fun `fail closed provider has no approved image path`() {
        assertEquals(DagV2ImageDecision.Hide, DagV2FailClosedImageDecisionProvider().decide())
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

    private fun start(url: String): DagV2DocumentRequestContext {
        val session = sessions.start(url)
        contexts.register(session.requestContext)
        router.onNewDocument(session)
        return session.requestContext
    }

    private fun cancel(context: DagV2DocumentRequestContext) {
        sessions.cancelActive()
        contexts.cancel(context)
    }

    private fun evidence(
        url: String,
        accept: String,
        destination: String,
        source: DagV2ResourceSource = DagV2ResourceSource.WebView,
        referer: String? = null,
        origin: String? = null,
        headers: Map<String, String> = emptyMap(),
    ) = DagV2ResourceEvidence(
        url = url,
        headers =
            buildMap {
                put("Accept", accept)
                put("Sec-Fetch-Dest", destination)
                referer?.let { put("Referer", it) }
                origin?.let { put("Origin", it) }
                putAll(headers)
            },
        isForMainFrame = destination == "document",
        source = source,
    )

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
