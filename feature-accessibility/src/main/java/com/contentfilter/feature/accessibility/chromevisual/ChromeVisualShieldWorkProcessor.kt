package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ChromeVisualShieldDecisionDelivery(
    val work: ChromeVisualShieldWork,
    val decision: ChromeVisualShieldGloshiaDecision,
    val sentinelMatches: Boolean,
)

/** Owns one capture/crop/inference cycle; the coordinator serializes these cycles. */
internal class ChromeVisualShieldWorkProcessor(
    private val capture: ChromeWindowCapture,
    private val frameProcessor: ChromeVisualShieldFrameProcessor,
    private val analyzer: ChromeVisualShieldGloshiaAnalyzer,
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val metrics: ChromeVisualShieldR1Metrics,
    private val deliverDecision: (ChromeVisualShieldDecisionDelivery) -> Unit,
    private val log: (String) -> Unit,
    private val onCycleEnded: () -> Unit,
) {
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
        val identity = work.identity
        when (val result = capture.capture(identity.windowId)) {
            is ChromeWindowCaptureResult.Failed -> {
                identityGate.failClosed(identity)
                log("phase=capture trigger=${work.trigger} errorCode=${result.errorCode} result=fail_close")
            }
            is ChromeWindowCaptureResult.Captured ->
                ChromeVisualShieldCaptureResources<ChromeWindowFrame, ChromeVisualShieldCrop>()
                    .use { resources ->
                        resources.attachFullFrame(result.frame)
                        val crop = resources.deriveCrop { frameProcessor.crop(it, identity) }
                        if (crop == null) {
                            identityGate.failClosed(identity)
                            log("phase=crop trigger=${work.trigger} result=fail_close")
                            return
                        }
                        resources.processCrop { ownedCrop -> processCrop(work, ownedCrop) }
                    }
        }
    }

    private suspend fun processCrop(
        work: ChromeVisualShieldWork,
        crop: ChromeVisualShieldCrop,
    ) {
        val identity = work.identity
        if (identityGate.beginProcessing(identity) is ChromeVisualShieldResult.Stale) {
            metrics.onStaleInferenceDropped()
            return
        }
        val matches = sentinelMatches(crop.bitmap)
        metrics.onInferenceStarted()
        val decision =
            try {
                analyzer.analyze(
                    bitmap = crop.bitmap,
                    identity = identity,
                    canContinue = { identityGate.isCurrentProcessing(identity) },
                ).also { currentCoroutineContext().ensureActive() }
            } catch (cancelled: CancellationException) {
                metrics.onInferenceCancelled()
                throw cancelled
            } finally {
                metrics.onInferenceCompleted()
            }
        currentCoroutineContext().ensureActive()
        deliverDecision(
            ChromeVisualShieldDecisionDelivery(
                work = work,
                decision = decision,
                sentinelMatches = matches,
            ),
        )
    }

    private fun sentinelMatches(bitmap: android.graphics.Bitmap): Boolean {
        if (bitmap.width < 4 || bitmap.height < 2) return false
        val redCandidate = bitmap.getPixel(bitmap.width / 4, bitmap.height / 2)
        val blackCandidate = bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 2)
        return ChromeVisualShieldExposureProbe.isSentinelPair(
            ChromeVisualShieldRgb(
                Color.red(redCandidate),
                Color.green(redCandidate),
                Color.blue(redCandidate),
            ),
            ChromeVisualShieldRgb(
                Color.red(blackCandidate),
                Color.green(blackCandidate),
                Color.blue(blackCandidate),
            ),
        )
    }
}
