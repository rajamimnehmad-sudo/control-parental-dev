package com.contentfilter.feature.accessibility.chromevisual

import android.content.Context
import android.os.SystemClock
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation

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
)

internal data class ChromePhotosDataPlaneLeaseContext(
    val packageName: String,
    val windowId: Int,
    val epoch: Long,
    val viewport: ChromeVisualViewport,
)

/** An in-memory, one-session capability. It is never persisted or reused after revocation. */
internal data class ChromePhotosDataPlaneLease(
    val capabilityId: Long,
    val sessionId: String,
    val windowId: Int,
    val epoch: Long,
    val viewport: ChromeVisualViewport,
    val validUntilElapsed: Long,
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
        if (!attestation.isEligible(now) || !context.isEligible()) return null
        nextCapabilityId += 1L
        return ChromePhotosDataPlaneLease(
            capabilityId = nextCapabilityId,
            sessionId = attestation.sessionId,
            windowId = context.windowId,
            epoch = context.epoch,
            viewport = context.viewport,
            validUntilElapsed = minOf(attestation.validUntilElapsed, now + LeaseDurationMillis),
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
            attestation.isEligible(now) &&
            context.isEligible() &&
            lease.sessionId == attestation.sessionId &&
            lease.windowId == context.windowId &&
            lease.epoch == context.epoch &&
            lease.viewport == context.viewport
    }

    @Synchronized
    fun revoke() {
        activeLease = null
    }

    private fun ChromePhotosDataPlaneAttestation.isEligible(now: Long): Boolean =
        devBuild &&
            sessionId.isNotBlank() &&
            active &&
            proxyHealthy &&
            policyConfirmed &&
            vpnConfirmed &&
            vpnSessionId == sessionId &&
            (fixtureConfirmed || realWebScopeConfirmed) &&
            heartbeatElapsed in 1..now &&
            now - heartbeatElapsed <= MaximumHeartbeatAgeMillis &&
            validUntilElapsed > now

    private fun ChromePhotosDataPlaneLeaseContext.isEligible(): Boolean =
        packageName == ChromePhotosDataPlaneLabContract.ChromePackage &&
            windowId >= 0 &&
            epoch > 0L &&
            viewport.width > 0 &&
            viewport.height > 0

    internal companion object {
        const val LeaseDurationMillis = 500L
        const val MaximumHeartbeatAgeMillis = 500L
    }
}

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
        )
    }
}
