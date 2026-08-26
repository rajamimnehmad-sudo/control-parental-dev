package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualModelInfo

internal object ChromeVisualShieldStatusFormatter {
    @Suppress("LongParameterList")
    fun format(
        active: Boolean,
        state: ChromeVisualShieldStateSnapshot,
        metrics: ChromeVisualShieldMetricsSnapshot,
        r1: ChromeVisualShieldR1MetricsSnapshot,
        captureCycles: Long,
        opaqueCommitted: Long,
        sentinelCropMatches: Long,
        workIdle: Boolean,
        probeActive: Boolean,
        probeCompleted: Boolean,
        probe: ChromeVisualShieldRenderProbeObservation?,
    ): String =
        "active=$active phase=${state.phase} session=${state.context?.protectionSessionId ?: 0} " +
            "windowId=${state.context?.windowId ?: -1} contentEpoch=${state.context?.contentEpoch ?: 0} " +
            "viewportEpoch=${state.context?.viewportEpoch ?: 0} regionSequence=${state.context?.regionSequence ?: 0} " +
            "fullFrameAcquired=${metrics.fullFrameAcquired} fullFrameClosed=${metrics.fullFrameClosed} " +
            "fullFrameOutstanding=${metrics.fullFrameOutstanding} fullFramePeakBytes=${metrics.fullFramePeakBytes} " +
            "cropCreated=${metrics.cropCreated} cropClosed=${metrics.cropClosed} cropOutstanding=${metrics.cropOutstanding} " +
            "staleDropped=${metrics.staleDropped} captureCancelled=${metrics.captureCancelled} " +
            "secureWindowFailures=${metrics.secureWindowFailures} captureCycles=$captureCycles " +
            "opaqueCommitted=$opaqueCommitted sentinelCropMatches=$sentinelCropMatches " +
            "labReleaseCount=${state.labReleaseCount} staleReleaseRejected=${state.staleReleaseRejected} " +
            "eventsReceived=${r1.eventsReceived} contentInvalidations=${r1.contentInvalidations} " +
            "workSuperseded=${r1.workSuperseded} inferenceStarted=${r1.inferenceStarted} " +
            "inferenceCompleted=${r1.inferenceCompleted} inferenceOutstanding=${r1.inferenceOutstanding} " +
            "inferencePeakOutstanding=${r1.inferencePeakOutstanding} " +
            "safeCurrent=${r1.safeCurrent} blockCurrent=${r1.blockCurrent} " +
            "failClosedCurrent=${r1.failClosedCurrent} staleInferenceDropped=${r1.staleInferenceDropped} " +
            "inferenceCancelled=${r1.inferenceCancelled} identityMismatchRejected=${r1.identityMismatchRejected} " +
            "releaseCurrent=${r1.releaseCurrent} releaseRejected=${r1.releaseRejected} " +
            "safeDecisionAtNanos=${r1.safeDecisionAtNanos} releaseAtNanos=${r1.releaseAtNanos} " +
            "workIdle=$workIdle model=${GloshiaVisualModelInfo.FunctionalVersion} " +
            "modelSha=${GloshiaVisualModelInfo.ModelSha256} rawPersisted=0 rawUploaded=0 " +
            "probeMode=$probeActive probeCompleted=$probeCompleted " +
            "probeSample=${probe?.request?.sampleId ?: "none"} probeSourceSha=${probe?.request?.sourceSha256 ?: "none"} " +
            "probeRenderContract=${probe?.request?.renderContract ?: "none"} " +
            "probeCrop=${probe?.crop?.width ?: 0}x${probe?.crop?.height ?: 0} " +
            "probeCropSha=${probe?.crop?.rgbaSha256 ?: "none"} probeAction=${probe?.action ?: "none"} " +
            "probeReason=${probe?.reason ?: "none"} probeProbability=${probe?.filterProbability ?: "none"} " +
            "probeInferenceCount=${probe?.inferenceCount ?: 0}"
}
