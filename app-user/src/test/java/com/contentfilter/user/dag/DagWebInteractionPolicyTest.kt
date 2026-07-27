package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagWebInteractionPolicyTest {
    @Test
    fun `service worker functional resources bypass the visual pipeline`() {
        val page = "https://shop.example/products"

        assertEquals(
            DagServiceWorkerResourceAction.Bypass,
            dagServiceWorkerResourceAction(
                "https://shop.example/app.js",
                mapOf("Sec-Fetch-Dest" to "script", "Accept" to "application/javascript"),
                page,
            ),
        )
    }

    @Test
    fun `service worker current images are filtered and unattributed images fail closed`() {
        val page = "https://shop.example/products"

        assertEquals(
            DagServiceWorkerResourceAction.FilterVisual,
            dagServiceWorkerResourceAction(
                "https://cdn.example/photo.jpg",
                mapOf("Sec-Fetch-Dest" to "image", "Referer" to page),
                page,
            ),
        )
        assertEquals(
            DagServiceWorkerResourceAction.BlockVisual,
            dagServiceWorkerResourceAction(
                "https://old-cdn.example/photo.jpg",
                mapOf("Sec-Fetch-Dest" to "image", "Referer" to "https://old.example/page"),
                page,
            ),
        )
        assertEquals(
            DagServiceWorkerResourceAction.BlockVisual,
            dagServiceWorkerResourceAction(
                "https://cdn.example/photo.jpg",
                mapOf("Sec-Fetch-Dest" to "image"),
                null,
            ),
        )
    }

    @Test
    fun `service worker media remains blocked`() {
        assertEquals(
            DagServiceWorkerResourceAction.BlockMedia,
            dagServiceWorkerResourceAction(
                "https://shop.example/video.mp4",
                mapOf("Sec-Fetch-Dest" to "video"),
                "https://shop.example/",
            ),
        )
    }
}
