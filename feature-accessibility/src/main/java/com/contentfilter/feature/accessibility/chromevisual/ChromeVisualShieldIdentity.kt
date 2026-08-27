package com.contentfilter.feature.accessibility.chromevisual

internal enum class ChromeVisualShieldPhase {
    Inactive,
    Protected,
    CapturePending,
    Processing,
    LabReleased,
}

internal enum class ChromeVisualShieldInvalidation {
    Navigation,
    Scroll,
    Viewport,
    Rotation,
    WindowReplaced,
    Suspension,
    SessionReplaced,
}

/** A region compiled into the DEV fixture; it is never inferred from Accessibility nodes. */
internal data class ChromeVisualShieldRegionContract(
    val id: String,
    val leftBasisPoints: Int,
    val topBasisPoints: Int,
    val rightBasisPoints: Int,
    val bottomBasisPoints: Int,
    val fixtureSignature: String,
    val verticalOffsetPixels: Int = 0,
    val edgeInsetPixels: Int = 0,
) {
    fun resolve(viewport: ChromeVisualViewport): ChromeVisualRegion? {
        if (
            fixtureSignature.isBlank() ||
            leftBasisPoints !in 0 until rightBasisPoints ||
            rightBasisPoints > BasisPointScale ||
            topBasisPoints !in 0 until bottomBasisPoints ||
            bottomBasisPoints > BasisPointScale
        ) {
            return null
        }
        return ChromeVisualRegion(
            id = id,
            left = viewport.left + scale(viewport.width, leftBasisPoints) + edgeInsetPixels,
            top = viewport.top + scale(viewport.height, topBasisPoints) + verticalOffsetPixels + edgeInsetPixels,
            right = viewport.left + scale(viewport.width, rightBasisPoints) - edgeInsetPixels,
            bottom =
                viewport.top + scale(viewport.height, bottomBasisPoints) + verticalOffsetPixels - edgeInsetPixels,
        ).takeIf { it.width > 0 && it.height > 0 }
    }

    private fun scale(
        size: Int,
        basisPoints: Int,
    ): Int = (size.toLong() * basisPoints / BasisPointScale).toInt()

    private companion object {
        const val BasisPointScale = 10_000
    }
}

internal data class ChromeVisualShieldIdentity(
    val protectionSessionId: Long,
    val windowId: Int,
    val contentEpoch: Long,
    val viewport: ChromeVisualViewport,
    val viewportEpoch: Long,
    val captureSequence: Long,
    val regionId: String,
    val regionSequence: Long,
    val region: ChromeVisualRegion,
)

internal data class ChromeVisualShieldContext(
    val protectionSessionId: Long,
    val windowId: Int,
    val contentEpoch: Long,
    val viewport: ChromeVisualViewport,
    val viewportEpoch: Long,
    val regionId: String,
    val regionSequence: Long,
    val region: ChromeVisualRegion,
)

internal data class ChromeVisualShieldStateSnapshot(
    val phase: ChromeVisualShieldPhase,
    val context: ChromeVisualShieldContext?,
    val nextCaptureSequence: Long,
    val protectionTransitions: Long,
    val labReleaseCount: Long,
    val staleReleaseRejected: Long,
) {
    val isFailClosed: Boolean
        get() = phase != ChromeVisualShieldPhase.Inactive && phase != ChromeVisualShieldPhase.LabReleased
}

internal sealed interface ChromeVisualShieldResult {
    data object Current : ChromeVisualShieldResult

    data object Stale : ChromeVisualShieldResult
}

/**
 * Process-local authority for the DEV shield. Counters never move backwards and a capture identity
 * is accepted only while every context field still equals the current protected epoch.
 */
internal class ChromeVisualShieldIdentityGate(
    private val onStaleDropped: () -> Unit = {},
) {
    private var phase = ChromeVisualShieldPhase.Inactive
    private var context: ChromeVisualShieldContext? = null
    private var protectionSessionId = 0L
    private var contentEpoch = 0L
    private var viewportEpoch = 0L
    private var captureSequence = 0L
    private var regionSequence = 0L
    private var protectionTransitions = 0L
    private var labReleaseCount = 0L
    private var staleReleaseRejected = 0L

    @Synchronized
    fun start(
        windowId: Int,
        viewport: ChromeVisualViewport,
        regionContract: ChromeVisualShieldRegionContract,
    ): ChromeVisualShieldStateSnapshot? {
        val region = regionContract.resolve(viewport) ?: return null
        protectionSessionId += 1
        contentEpoch += 1
        viewportEpoch += 1
        regionSequence += 1
        context =
            ChromeVisualShieldContext(
                protectionSessionId = protectionSessionId,
                windowId = windowId,
                contentEpoch = contentEpoch,
                viewport = viewport,
                viewportEpoch = viewportEpoch,
                regionId = regionContract.id,
                regionSequence = regionSequence,
                region = region,
            )
        protect()
        return snapshot()
    }

    @Synchronized
    fun invalidate(
        windowId: Int,
        viewport: ChromeVisualViewport,
        regionContract: ChromeVisualShieldRegionContract,
        reason: ChromeVisualShieldInvalidation,
    ): ChromeVisualShieldStateSnapshot? {
        val current = context ?: return null
        val region = regionContract.resolve(viewport) ?: return null
        contentEpoch += 1
        regionSequence += 1
        if (
            current.windowId != windowId ||
            current.viewport != viewport ||
            reason == ChromeVisualShieldInvalidation.Viewport ||
            reason == ChromeVisualShieldInvalidation.Rotation
        ) {
            viewportEpoch += 1
        }
        context =
            current.copy(
                windowId = windowId,
                contentEpoch = contentEpoch,
                viewport = viewport,
                viewportEpoch = viewportEpoch,
                regionId = regionContract.id,
                regionSequence = regionSequence,
                region = region,
            )
        protect()
        return snapshot()
    }

    @Synchronized
    fun beginCapture(): ChromeVisualShieldIdentity? {
        val current = context ?: return null
        if (phase != ChromeVisualShieldPhase.Protected) return null
        captureSequence += 1
        phase = ChromeVisualShieldPhase.CapturePending
        return current.toIdentity(captureSequence)
    }

    @Synchronized
    fun beginProcessing(identity: ChromeVisualShieldIdentity): ChromeVisualShieldResult {
        if (!isCurrent(identity) || phase != ChromeVisualShieldPhase.CapturePending) return stale()
        phase = ChromeVisualShieldPhase.Processing
        return ChromeVisualShieldResult.Current
    }

    @Synchronized
    fun completeProcessing(identity: ChromeVisualShieldIdentity): ChromeVisualShieldResult {
        if (!isCurrent(identity) || phase != ChromeVisualShieldPhase.Processing) return stale()
        phase = ChromeVisualShieldPhase.Protected
        protectionTransitions += 1
        return ChromeVisualShieldResult.Current
    }

    @Synchronized
    fun isCurrentProcessing(identity: ChromeVisualShieldIdentity): Boolean =
        phase == ChromeVisualShieldPhase.Processing && isCurrent(identity)

    @Synchronized
    fun failClosed(identity: ChromeVisualShieldIdentity?) {
        if (identity == null || isCurrent(identity)) protect()
    }

    @Synchronized
    fun releaseForExplicitLabGate(expected: ChromeVisualShieldContext): Boolean {
        if (context != expected || phase != ChromeVisualShieldPhase.Protected) {
            staleReleaseRejected += 1
            return false
        }
        phase = ChromeVisualShieldPhase.LabReleased
        labReleaseCount += 1
        return true
    }

    @Synchronized
    fun stop() {
        contentEpoch += 1
        regionSequence += 1
        phase = ChromeVisualShieldPhase.Inactive
        context = null
    }

    @Synchronized
    fun snapshot(): ChromeVisualShieldStateSnapshot =
        ChromeVisualShieldStateSnapshot(
            phase = phase,
            context = context,
            nextCaptureSequence = captureSequence + 1,
            protectionTransitions = protectionTransitions,
            labReleaseCount = labReleaseCount,
            staleReleaseRejected = staleReleaseRejected,
        )

    private fun protect() {
        phase = ChromeVisualShieldPhase.Protected
        protectionTransitions += 1
    }

    private fun isCurrent(identity: ChromeVisualShieldIdentity): Boolean =
        context?.toIdentity(identity.captureSequence) == identity &&
            identity.captureSequence == captureSequence

    private fun stale(): ChromeVisualShieldResult.Stale {
        onStaleDropped()
        return ChromeVisualShieldResult.Stale
    }

    private fun ChromeVisualShieldContext.toIdentity(captureSequence: Long) =
        ChromeVisualShieldIdentity(
            protectionSessionId = protectionSessionId,
            windowId = windowId,
            contentEpoch = contentEpoch,
            viewport = viewport,
            viewportEpoch = viewportEpoch,
            captureSequence = captureSequence,
            regionId = regionId,
            regionSequence = regionSequence,
            region = region,
        )
}

internal class ChromeVisualShieldCycleCoordinator(
    private val identityGate: ChromeVisualShieldIdentityGate,
) {
    fun invalidateProtectThenSchedule(
        windowId: Int,
        viewport: ChromeVisualViewport,
        regionContract: ChromeVisualShieldRegionContract,
        reason: ChromeVisualShieldInvalidation,
        protect: (ChromeVisualShieldStateSnapshot) -> Boolean,
        schedule: (ChromeVisualShieldStateSnapshot) -> Unit,
    ): Boolean {
        val protected = identityGate.invalidate(windowId, viewport, regionContract, reason) ?: return false
        if (!protect(protected)) return false
        schedule(protected)
        return true
    }
}
