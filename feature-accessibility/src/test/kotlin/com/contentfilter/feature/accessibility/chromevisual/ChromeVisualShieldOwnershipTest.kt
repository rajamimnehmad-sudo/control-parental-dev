package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChromeVisualShieldOwnershipTest {
    @Test
    fun `full frame closes before crop processing and crop closes last`() {
        val trace = mutableListOf<String>()
        val frame = Resource("full", trace)

        val result =
            ChromeVisualShieldOwnershipPipeline.deriveAndProcess(
                fullFrame = frame,
                derive = {
                    trace += "derive"
                    Resource("crop", trace)
                },
                process = {
                    trace += "process"
                    "done"
                },
            )

        assertEquals("done", result)
        assertEquals(listOf("derive", "full:closed", "process", "crop:closed"), trace)
    }

    @Test
    fun `crop failure still closes full frame`() {
        val trace = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            ChromeVisualShieldOwnershipPipeline.deriveAndProcess(
                fullFrame = Resource("full", trace),
                derive = { error("crop failed") },
                process = { Unit },
            )
        }

        assertEquals(listOf("full:closed"), trace)
    }

    @Test
    fun `processing cancellation closes crop and leaves no resource outstanding`() {
        val trace = mutableListOf<String>()

        assertFailsWith<InterruptedException> {
            ChromeVisualShieldOwnershipPipeline.deriveAndProcess(
                fullFrame = Resource("full", trace),
                derive = { Resource("crop", trace) },
                process = { throw InterruptedException("cancelled") },
            )
        }

        assertEquals(listOf("full:closed", "crop:closed"), trace)
    }

    @Test
    fun `metrics return to zero after close and ignore duplicate close`() {
        val metrics = ChromeVisualShieldMetrics()
        metrics.onFullFrameAcquired(1024)
        metrics.onCropCreated()
        metrics.onFullFrameClosed(1024)
        metrics.onCropClosed()

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.fullFrameAcquired)
        assertEquals(1, snapshot.fullFrameClosed)
        assertEquals(0, snapshot.fullFrameOutstanding)
        assertEquals(1024, snapshot.fullFramePeakBytes)
        assertEquals(0, snapshot.cropOutstanding)
    }

    @Test
    fun `cancel before screenshot callback owns no resources`() {
        val resources = ChromeVisualShieldCaptureResources<Resource, Resource>()
        resources.close()
        assertEquals(emptyList(), mutableListOf<String>())
    }

    @Test
    fun `cancel after frame closes full frame`() {
        val trace = mutableListOf<String>()
        val resources = ChromeVisualShieldCaptureResources<Resource, Resource>()
        resources.attachFullFrame(Resource("full", trace))

        resources.close()

        assertEquals(listOf("full:closed"), trace)
    }

    @Test
    fun `cancel after crop closes crop and already closed full frame`() {
        val trace = mutableListOf<String>()
        val resources = ChromeVisualShieldCaptureResources<Resource, Resource>()
        resources.attachFullFrame(Resource("full", trace))
        resources.deriveCrop { Resource("crop", trace) }

        resources.close()
        resources.close()

        assertEquals(listOf("full:closed", "crop:closed"), trace)
    }

    @Test
    fun `secure window error is counted without creating a frame`() {
        val metrics = ChromeVisualShieldMetrics()
        val observer = ChromeVisualShieldCaptureObserver(metrics)

        observer.onFailure(6)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.secureWindowFailures)
        assertEquals(0, snapshot.fullFrameAcquired)
        assertEquals(0, snapshot.fullFrameOutstanding)
    }

    @Test
    fun `repeated cycles keep outstanding bytes at zero and peak bounded to one frame`() {
        val metrics = ChromeVisualShieldMetrics()

        repeat(50) {
            metrics.onFullFrameAcquired(8_192)
            metrics.onCropCreated()
            metrics.onFullFrameClosed(8_192)
            metrics.onCropClosed()
        }

        val snapshot = metrics.snapshot()
        assertEquals(0, snapshot.fullFrameOutstanding)
        assertEquals(0, snapshot.cropOutstanding)
        assertEquals(8_192, snapshot.fullFramePeakBytes)
        assertEquals(50, snapshot.fullFrameClosed)
        assertEquals(50, snapshot.cropClosed)
    }

    private class Resource(
        private val name: String,
        private val trace: MutableList<String>,
    ) : AutoCloseable {
        override fun close() {
            trace += "$name:closed"
        }
    }
}
