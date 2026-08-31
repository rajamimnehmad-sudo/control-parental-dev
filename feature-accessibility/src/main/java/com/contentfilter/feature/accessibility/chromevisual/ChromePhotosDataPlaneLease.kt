package com.contentfilter.feature.accessibility.chromevisual

import android.content.Context
import android.os.SystemClock
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation

/** Non-raster H19 authority bound to one claim and one browser-owned native context. */
internal data class ChromeMediaShieldActiveDocumentAuthority(
    val claim: ChromeMediaShieldReadyClaim,
    val windowId: Int,
    val nativeRootDigest: String,
)

internal data class ChromePhotosDataPlaneAttestation(
    val devBuild: Boolean,
    val sessionId: String,
    val active: Boolean,
    val proxyHealthy: Boolean,
    val policyConfirmed: Boolean,
    val vpnConfirmed: Boolean,
    val vpnSessionId: String,
    val fixtureConfirmed: Boolean,
    val realWebScopeConfirmed: Boolean,
    val heartbeatElapsed: Long,
    val validUntilElapsed: Long,
    val accessibilityBound: Boolean = false,
    val mediaAuthorityEnabled: Boolean = false,
    val mediaPolicyEpoch: Long = 0L,
)

internal data class ChromePhotosDataPlaneLeaseContext(
    val packageName: String,
    val windowId: Int,
    val epoch: Long,
    val viewport: ChromeVisualViewport,
    val foregroundDocument: ChromeMediaShieldForegroundDocument? = null,
    val activeDocument: ChromeMediaShieldActiveDocumentAuthority? = null,
)

/** An in-memory, one-session capability. It is never persisted or reused after revocation. */
internal data class ChromePhotosDataPlaneLease(
    val capabilityId: Long,
    val sessionId: String,
    val windowId: Int,
    val epoch: Long,
    val viewport: ChromeVisualViewport,
    val validUntilElapsed: Long,
    val foregroundDocument: ChromeMediaShieldForegroundDocument? = null,
    val activeDocument: ChromeMediaShieldActiveDocumentAuthority? = null,
)

internal class ChromePhotosDataPlanePresentationPolicy {
    private var authorityEpoch = 0L
    private var opaqueCommittedEpoch = 0L
    var isTransparent: Boolean = false
        private set

    fun cover(epoch: Long) {
        authorityEpoch = maxOf(authorityEpoch, epoch)
        isTransparent = false
    }

    fun markOpaqueCommitted(epoch: Long): Boolean {
        if (epoch != authorityEpoch) return false
        opaqueCommittedEpoch = epoch
        return true
    }

    fun canPresent(lease: ChromePhotosDataPlaneLease?): Boolean =
        lease != null &&
            lease.epoch == authorityEpoch &&
            opaqueCommittedEpoch == authorityEpoch

    fun markTransparent(lease: ChromePhotosDataPlaneLease): Boolean {
        if (!canPresent(lease)) return false
        isTransparent = true
        return true
    }

    fun revoke() {
        isTransparent = false
    }

    fun reset() {
        authorityEpoch = 0L
        opaqueCommittedEpoch = 0L
        isTransparent = false
    }
}

internal class ChromePhotosDataPlaneLeaseAuthority(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private var nextCapabilityId = 0L
    private var activeLease: ChromePhotosDataPlaneLease? = null

    @Synchronized
    fun mint(
        attestation: ChromePhotosDataPlaneAttestation,
        context: ChromePhotosDataPlaneLeaseContext,
    ): ChromePhotosDataPlaneLease? {
        activeLease = null
        val now = elapsedRealtime()
        if (
            !attestation.isPresentationEligible(now) ||
            !context.isEligible() ||
            !context.hasRequiredForegroundAuthority(attestation)
        ) {
            return null
        }
        nextCapabilityId += 1L
        return ChromePhotosDataPlaneLease(
            capabilityId = nextCapabilityId,
            sessionId = attestation.sessionId,
            windowId = context.windowId,
            epoch = context.epoch,
            viewport = context.viewport,
            validUntilElapsed = minOf(attestation.validUntilElapsed, now + LeaseDurationMillis),
            foregroundDocument = context.foregroundDocument,
            activeDocument = context.activeDocument,
        ).also { activeLease = it }
    }

    @Synchronized
    fun isValid(
        lease: ChromePhotosDataPlaneLease,
        attestation: ChromePhotosDataPlaneAttestation,
        context: ChromePhotosDataPlaneLeaseContext,
    ): Boolean {
        val now = elapsedRealtime()
        return activeLease == lease &&
            now < lease.validUntilElapsed &&
            attestation.isPresentationEligible(now) &&
            context.isEligible() &&
            lease.sessionId == attestation.sessionId &&
            lease.windowId == context.windowId &&
            lease.epoch == context.epoch &&
            lease.viewport == context.viewport &&
            lease.foregroundDocument == context.foregroundDocument &&
            lease.activeDocument == context.activeDocument &&
            context.hasRequiredForegroundAuthority(attestation)
    }

    @Synchronized
    fun revoke() {
        activeLease = null
    }

    private fun ChromePhotosDataPlaneLeaseContext.isEligible(): Boolean =
        packageName == ChromePhotosDataPlaneLabContract.ChromePackage &&
            windowId >= 0 &&
            epoch > 0L &&
            viewport.width > 0 &&
            viewport.height > 0

    private fun ChromePhotosDataPlaneLeaseContext.hasRequiredForegroundAuthority(
        attestation: ChromePhotosDataPlaneAttestation,
    ): Boolean {
        if (!attestation.mediaAuthorityEnabled) {
            return foregroundDocument == null && activeDocument == null
        }
        if ((foregroundDocument == null) == (activeDocument == null)) return false
        val identity = foregroundDocument?.identity ?: checkNotNull(activeDocument).claim.identity
        val authorityWindowId = foregroundDocument?.windowId ?: checkNotNull(activeDocument).windowId
        val activeBindingValid =
            activeDocument?.let { active ->
                active.nativeRootDigest.matches(Sha256Pattern) &&
                    active.claim.lifecycleSequence > 0L
            } ?: true
        return activeBindingValid &&
            attestation.mediaPolicyEpoch > 0L &&
            authorityWindowId == windowId &&
            identity.topLevel &&
            identity.protectionSessionId == attestation.sessionId &&
            identity.policyEpoch == attestation.mediaPolicyEpoch
    }

    internal companion object {
        const val LeaseDurationMillis = 500L
        const val MaximumHeartbeatAgeMillis = 500L
        val Sha256Pattern = Regex("[0-9a-f]{64}")
    }
}

internal fun ChromePhotosDataPlaneAttestation.isPresentationEligible(now: Long): Boolean =
    devBuild &&
        sessionId.isNotBlank() &&
        active &&
        proxyHealthy &&
        policyConfirmed &&
        vpnConfirmed &&
        vpnSessionId == sessionId &&
        accessibilityBound &&
        (fixtureConfirmed || realWebScopeConfirmed) &&
        heartbeatElapsed in 1..now &&
        now - heartbeatElapsed <= ChromePhotosDataPlaneLeaseAuthority.MaximumHeartbeatAgeMillis &&
        validUntilElapsed > now

internal class ChromePhotosDataPlaneAttestationReader(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            ChromePhotosDataPlaneLabContract.PreferencesName,
            Context.MODE_PRIVATE,
        )

    fun read(): ChromePhotosDataPlaneAttestation {
        val runtime = ChromePhotosDataPlaneRuntimeAttestation.snapshot()
        return ChromePhotosDataPlaneAttestation(
            devBuild = appContext.packageName.endsWith(".dev"),
            sessionId = runtime.sessionId,
            active = preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false),
            proxyHealthy = runtime.proxyHealthy,
            policyConfirmed = runtime.policyConfirmed,
            vpnConfirmed = runtime.vpnConfirmed,
            vpnSessionId = runtime.vpnSessionId,
            fixtureConfirmed = runtime.fixtureConfirmed,
            realWebScopeConfirmed = runtime.realWebScopeConfirmed,
            heartbeatElapsed = runtime.heartbeatElapsed,
            validUntilElapsed = runtime.validUntilElapsed,
            accessibilityBound = runtime.accessibilityBound,
            mediaAuthorityEnabled = runtime.mediaAuthorityEnabled,
            mediaPolicyEpoch = runtime.mediaPolicyEpoch,
        )
    }
}
