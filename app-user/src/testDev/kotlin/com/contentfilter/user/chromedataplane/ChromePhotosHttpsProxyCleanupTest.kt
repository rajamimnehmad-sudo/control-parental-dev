package com.contentfilter.user.chromedataplane

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChromePhotosHttpsProxyCleanupTest {
    @Test
    fun `fatal then close clears sensitive resources exactly once and cannot reopen`() {
        val fixture = FakeFixtureSource()
        val transformer = chromePhotosDeterministicTransformer(fixture)
        val tls = ChromePhotosEphemeralTls.create()
        val upstream = TrackingUpstream()
        val fatalReasons = mutableListOf<String>()
        transformer.transform("image/png", fixture.safeImageBytes)
        tls.serverMaterialFor(ChromePhotosRealWebLabConfig.HttpBingoHost)
        val proxy = proxy(fixture, tls, transformer, upstream, fatalReasons::add)

        proxy.fatal(IllegalStateException("terminal"))
        proxy.close()
        proxy.close()

        assertEquals(listOf("IllegalStateException"), fatalReasons)
        assertEquals(0, transformer.cacheSize())
        assertEquals(0, tls.cachedLeafCount())
        assertEquals(1, upstream.closeCalls.get())
        assertTrue(proxy.cleanupCompleted())
        assertFailsWith<IllegalStateException> { proxy.start() }
    }

    @Test
    fun `normal close is idempotent and clears resources even before start`() {
        val fixture = FakeFixtureSource()
        val transformer = chromePhotosDeterministicTransformer(fixture)
        val tls = ChromePhotosEphemeralTls.create()
        val upstream = TrackingUpstream()
        transformer.transform("image/png", fixture.sentinelImageBytes)
        tls.serverMaterialFor(ChromePhotosRealWebLabConfig.GoogleStaticHost)
        val proxy = proxy(fixture, tls, transformer, upstream)

        proxy.close()
        proxy.close()

        assertEquals(0, transformer.cacheSize())
        assertEquals(0, tls.cachedLeafCount())
        assertEquals(1, upstream.closeCalls.get())
        assertTrue(proxy.cleanupCompleted())
    }

    @Test
    fun `cleanup continues through an individual resource close failure`() {
        val fixture = FakeFixtureSource()
        val transformer = chromePhotosDeterministicTransformer(fixture)
        val tls = ChromePhotosEphemeralTls.create()
        val upstream = TrackingUpstream(throwOnClose = true)
        transformer.transform("image/png", fixture.safeImageBytes)
        tls.serverMaterialFor(ChromePhotosRealWebLabConfig.GitHubHost)
        val proxy = proxy(fixture, tls, transformer, upstream)

        proxy.close()

        assertEquals(0, transformer.cacheSize())
        assertEquals(0, tls.cachedLeafCount())
        assertEquals(1, upstream.closeCalls.get())
        assertTrue(proxy.cleanupCompleted())
    }

    private fun proxy(
        fixture: ChromePhotosFixtureSource,
        tls: ChromePhotosEphemeralTlsMaterial,
        transformer: ChromePhotosResourceTransformer,
        upstream: ChromePhotosUpstream,
        onFatal: (String) -> Unit = {},
    ): ChromePhotosHttpsProxy =
        ChromePhotosHttpsProxy(
            tls = tls,
            origin = fixture,
            onFixtureHeartbeat = {},
            onFatalFailure = onFatal,
            upstream = upstream,
            transformer = transformer,
            lifecycleLog = { _, _ -> },
        )

    private class TrackingUpstream(
        private val throwOnClose: Boolean = false,
    ) : ChromePhotosUpstream {
        val closeCalls = AtomicInteger()

        override fun execute(
            host: String,
            request: ChromePhotosProxyRequest,
        ): ChromePhotosUpstreamResponse = throw IOException("not used")

        override fun close() {
            closeCalls.incrementAndGet()
            if (throwOnClose) throw IOException("close failed")
        }
    }

    private class FakeFixtureSource : ChromePhotosFixtureSource {
        override val safeImageBytes = "safe".toByteArray()
        override val sentinelImageBytes = "block".toByteArray()
        override val placeholderImageBytes = "placeholder".toByteArray()

        override fun responseFor(requestTarget: String): ChromePhotosFixtureResponse =
            ChromePhotosFixtureResponse(
                resourceId = "unused",
                contentType = "text/plain",
                originalBytes = ByteArray(0),
            )
    }
}
