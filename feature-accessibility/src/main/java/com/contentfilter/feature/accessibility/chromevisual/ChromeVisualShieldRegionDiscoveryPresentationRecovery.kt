package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent

/** State-driven, bounded R2A replacement policy after a raster-level presentation rejection. */
internal class ChromeVisualShieldRegionDiscoveryPresentationRecovery(
    private val lab: ChromeVisualShieldRegionDiscoveryLab,
    private val identityGate: ChromeVisualShieldIdentityGate,
    private val replaceGeneration: (ChromeVisualShieldContext) -> Unit,
    private val log: (String) -> Unit,
) {
    fun onRejected(
        work: ChromeVisualShieldWork,
        rejection: ChromeVisualShieldRegionDiscoveryPresentationResult.Rejected,
    ) {
        val mode = work.mode as? ChromeVisualShieldWorkMode.RegionDiscoveryProbe ?: return
        when (lab.presentationRejected(mode.binding, work.identity, rejection.reason)) {
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.ReplaceGeneration -> {
                val current = identityGate.snapshot().context
                if (current == null || !mode.binding.matches(current)) {
                    log("phase=presentation_recovery result=stale_drop reason=${rejection.reason}")
                    return
                }
                log("phase=presentation_recovery result=new_generation_required reason=${rejection.reason}")
                replaceGeneration(current)
            }
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.ExhaustedFailClosed ->
                log("phase=presentation_recovery result=bounded_fail_close reason=${rejection.reason}")
            ChromeVisualShieldRegionDiscoveryLab.PresentationRecovery.StaleDropped ->
                log("phase=presentation_recovery result=stale_drop reason=${rejection.reason}")
        }
    }
}

internal fun ChromeVisualShieldContext.toProbeIdentity(nextCaptureSequence: Long): ChromeVisualShieldIdentity =
    ChromeVisualShieldIdentity(
        protectionSessionId = protectionSessionId,
        windowId = windowId,
        contentEpoch = contentEpoch,
        viewport = viewport,
        viewportEpoch = viewportEpoch,
        captureSequence = nextCaptureSequence,
        regionId = regionId,
        regionSequence = regionSequence,
        region = region,
    )

internal fun ChromeVisualShieldRegionContract.forRegionDiscovery(
    active: Boolean,
    navigationInsets: ChromeVisualShieldNavigationInsets,
): ChromeVisualShieldRegionContract =
    if (active) {
        copy(
            verticalOffsetPixels = -navigationInsets.bottom,
            edgeInsetPixels = ChromeVisualShieldLabControl.RegionDiscoverySearchEnvelopeInsetPixels,
        )
    } else {
        this
    }

internal object ChromeVisualShieldEventInvalidationResolver {
    fun resolve(
        event: AccessibilityEvent,
        current: ChromeVisualShieldContext,
        windowId: Int,
        viewport: ChromeVisualViewport,
    ): ChromeVisualShieldInvalidation =
        when {
            windowId != current.windowId -> ChromeVisualShieldInvalidation.WindowReplaced
            viewport != current.viewport -> ChromeVisualShieldInvalidation.Viewport
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED -> ChromeVisualShieldInvalidation.Scroll
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED -> ChromeVisualShieldInvalidation.WindowReplaced
            else -> ChromeVisualShieldInvalidation.Navigation
        }
}
