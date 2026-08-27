package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChromePhotosDataPlaneLifecycleTest {
    @Test
    fun `presentation requires listening proxy`() {
        val lifecycle = ChromePhotosDataPlaneLifecycle()

        lifecycle.begin()
        assertFailsWith<IllegalStateException> { lifecycle.presentationReady() }
        lifecycle.proxyReady()
        assertEquals(ChromePhotosDataPlanePhase.PresentationReady, lifecycle.presentationReady())
    }

    @Test
    fun `failure can restart but stopped cannot become ready`() {
        val lifecycle = ChromePhotosDataPlaneLifecycle()

        lifecycle.begin()
        lifecycle.fail()
        lifecycle.begin()
        lifecycle.proxyReady()
        lifecycle.stop()

        assertEquals(ChromePhotosDataPlanePhase.Stopped, lifecycle.current())
        assertFailsWith<IllegalStateException> { lifecycle.presentationReady() }
    }

    @Test
    fun `Chrome policy is scoped to fixed loopback proxy`() {
        assertEquals(
            """{"ProxyMode":"fixed_servers","ProxyServer":"127.0.0.1:8877","ProxyBypassList":""}""",
            ChromePhotosChromePolicy.proxySettingsJson(),
        )
    }
}
