package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong

/** Owns the R2A capture envelope, pixel buffer, regional crops, and per-region inference. */
internal class ChromeVisualShieldRegionDiscoveryWorkProcessor(
    private val capture: ChromeWindowCapture,
    private val frameProcessor: ChromeVisualShieldFrameProcessor,
    private val planner: ChromeVisualShieldRegionDiscoveryPlanner,
    private val analyzer: ChromeVisualRegionAnalyzer,
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldR1Metrics,
    private val rasterProvenanceObserver: ChromeVisualShieldRasterProvenanceObserver,
    private val deliver: (ChromeVisualShieldRegionDiscoveryDelivery) -> Unit,
    private val log: (String) -> Unit,
    private val onCycleEnded: () -> Unit,
) {
    private val discoverySequence = AtomicLong(0)

    suspend fun execute(work: ChromeVisualShieldWork) {
        try {
            captureAndProcess(work)
        } catch (cancelled: CancellationException) {
            identityGate.failClosed(work.identity)
            throw cancelled
        } finally {
            onCycleEnded()
        }
    }

    private suspend fun captureAndProcess(work: ChromeVisualShieldWork) {
        when (val result = capture.capture(work.identity.windowId)) {
            is ChromeWindowCaptureResult.Failed -> {
                identityGate.failClosed(work.identity)
                log("phase=region_discovery_capture errorCode=${result.errorCode} result=fail_close")
            }
            is ChromeWindowCaptureResult.Captured ->
                ChromeVisualShieldCaptureResources<ChromeWindowFrame, ChromeVisualShieldCrop>()
                    .use { resources ->
                        resources.attachFullFrame(result.frame)
                        rasterProvenanceObserver.observeFullFrame(result.frame.bitmap, work.identity)
                        val crop = resources.deriveCrop { frameProcessor.crop(it, work.identity) }
                        if (crop == null) {
                            identityGate.failClosed(work.identity)
                            log("phase=region_discovery_crop result=fail_close")
                            return
                        }
                        resources.processCrop { ownedCrop -> processEnvelope(work, ownedCrop) }
                    }
        }
    }

    private suspend fun processEnvelope(
        work: ChromeVisualShieldWork,
        crop: ChromeVisualShieldCrop,
    ) {
        if (identityGate.beginProcessing(work.identity) is ChromeVisualShieldResult.Stale) {
            metrics.onStaleInferenceDropped()
            return
        }
        rasterProvenanceObserver.onPlannerEntry(work.identity, identityGate.snapshot().context)
        rasterProvenanceObserver.observeCrop(crop.bitmap, work.identity)
        val pixels = crop.bitmap.copyPixels()
        val coroutineContext = currentCoroutineContext()
        val discovery =
            try {
                planner.discover(
                    raster = ChromeVisualShieldDiscoveryRaster(crop.bitmap.width, crop.bitmap.height, pixels),
                    identity = work.identity,
                    discoverySequence = discoverySequence.incrementAndGet(),
                    isIdentityCurrent = { identityGate.isCurrentProcessing(work.identity) },
                    isCancelled = { !coroutineContext.isActive },
                )
            } finally {
                pixels.fill(0)
            }
        currentCoroutineContext().ensureActive()
        val decisions = analyzeCompleteRegions(work, crop.bitmap, discovery)
        currentCoroutineContext().ensureActive()
        deliver(
            ChromeVisualShieldRegionDiscoveryDelivery(
                work = work,
                searchEnvelope = work.identity.region,
                cropEvidence = ChromeVisualShieldCropEvidenceFactory.from(crop.bitmap),
                discovery = discovery,
                decisions = decisions,
            ),
        )
    }

    private suspend fun analyzeCompleteRegions(
        work: ChromeVisualShieldWork,
        envelope: Bitmap,
        discovery: ChromeVisualShieldRegionDiscoveryResult,
    ): List<ChromeVisualShieldRegionDecision> {
        val complete = discovery as? ChromeVisualShieldRegionDiscoveryResult.Complete ?: return emptyList()
        val batchIdentity = ChromeVisualShieldRegionSetBatchIdentity.from(work.identity, complete)
        return complete.regions.map { region ->
            currentCoroutineContext().ensureActive()
            if (!identityGate.isCurrentProcessing(work.identity)) throw CancellationException("stale discovery")
            metrics.onInferenceStarted()
            val decision =
                try {
                    analyzer.analyze(envelope, region.bounds)
                } catch (cancelled: CancellationException) {
                    metrics.onInferenceCancelled()
                    throw cancelled
                } finally {
                    metrics.onInferenceCompleted()
                }
            currentCoroutineContext().ensureActive()
            ChromeVisualShieldRegionDecision(region, decision, batchIdentity)
        }
    }

    private fun Bitmap.copyPixels(): IntArray =
        IntArray(width * height).also { pixels ->
            getPixels(pixels, 0, width, 0, 0, width, height)
        }
}
