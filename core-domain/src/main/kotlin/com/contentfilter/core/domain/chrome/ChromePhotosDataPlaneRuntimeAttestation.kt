package com.contentfilter.core.domain.chrome

/**
 * Process-local health attestation for the DEV lab. A process restart clears it fail-closed.
 * This is evidence used to mint a scoped lease; it is not itself a presentation capability.
 */
data class ChromePhotosDataPlaneRuntimeSnapshot(
    val sessionId: String = "",
    val proxyHealthy: Boolean = false,
    val policyConfirmed: Boolean = false,
    val vpnConfirmed: Boolean = false,
    val vpnSessionId: String = "",
    val fixtureConfirmed: Boolean = false,
    val fixtureHeartbeatElapsed: Long = 0L,
    val realWebScopeConfirmed: Boolean = false,
    val realWebScopeHeartbeatElapsed: Long = 0L,
    val heartbeatElapsed: Long = 0L,
    val validUntilElapsed: Long = 0L,
    val accessibilityBound: Boolean = false,
    val mediaAuthorityEnabled: Boolean = false,
    val mediaPolicyEpoch: Long = 0L,
    val documentSelfShieldEnabled: Boolean = false,
)

object ChromePhotosDataPlaneRuntimeAttestation {
    private var accessibilityBound = false
    private var state = ChromePhotosDataPlaneRuntimeSnapshot()

    @Synchronized
    fun beginSession(
        sessionId: String,
        mediaAuthorityEnabled: Boolean = false,
        mediaPolicyEpoch: Long = 0L,
        documentSelfShieldEnabled: Boolean = false,
    ) {
        state =
            ChromePhotosDataPlaneRuntimeSnapshot(
                sessionId = sessionId,
                accessibilityBound = accessibilityBound,
                mediaAuthorityEnabled = mediaAuthorityEnabled,
                mediaPolicyEpoch = if (mediaAuthorityEnabled) mediaPolicyEpoch else 0L,
                documentSelfShieldEnabled = mediaAuthorityEnabled && documentSelfShieldEnabled,
            )
    }

    @Synchronized
    fun markAccessibilityBound(bound: Boolean) {
        accessibilityBound = bound
        state = state.copy(accessibilityBound = bound)
    }

    @Synchronized
    fun markProxyHealthy(
        sessionId: String,
        healthy: Boolean,
    ) = update(sessionId) { copy(proxyHealthy = healthy) }

    @Synchronized
    fun markPolicyConfirmed(
        sessionId: String,
        confirmed: Boolean,
    ) = update(sessionId) { copy(policyConfirmed = confirmed) }

    @Synchronized
    fun markVpnConfirmed(
        sessionId: String,
        confirmed: Boolean,
    ) = update(sessionId) {
        copy(
            vpnConfirmed = confirmed,
            vpnSessionId = if (confirmed) sessionId else "",
        )
    }

    @Synchronized
    fun markFixtureConfirmed(
        sessionId: String,
        confirmed: Boolean,
        heartbeatElapsed: Long = 0L,
    ) = update(sessionId) {
        copy(
            fixtureConfirmed = confirmed,
            fixtureHeartbeatElapsed = if (confirmed) heartbeatElapsed else 0L,
        )
    }

    @Synchronized
    fun markRealWebScopeConfirmed(
        sessionId: String,
        confirmed: Boolean,
        heartbeatElapsed: Long = 0L,
    ) = update(sessionId) {
        copy(
            realWebScopeConfirmed = confirmed,
            realWebScopeHeartbeatElapsed = if (confirmed) heartbeatElapsed else 0L,
        )
    }

    @Synchronized
    fun publishHeartbeat(
        sessionId: String,
        elapsed: Long,
        validUntilElapsed: Long,
    ) = update(sessionId) {
        copy(
            heartbeatElapsed = elapsed,
            validUntilElapsed = validUntilElapsed,
        )
    }

    @Synchronized
    fun failClosed(sessionId: String) =
        update(sessionId) {
            copy(
                proxyHealthy = false,
                policyConfirmed = false,
                vpnConfirmed = false,
                vpnSessionId = "",
                fixtureConfirmed = false,
                fixtureHeartbeatElapsed = 0L,
                realWebScopeConfirmed = false,
                realWebScopeHeartbeatElapsed = 0L,
                heartbeatElapsed = 0L,
                validUntilElapsed = 0L,
            )
        }

    @Synchronized
    fun snapshot(): ChromePhotosDataPlaneRuntimeSnapshot = state

    @Synchronized
    fun clear() {
        state = ChromePhotosDataPlaneRuntimeSnapshot(accessibilityBound = accessibilityBound)
    }

    private inline fun update(
        sessionId: String,
        transform: ChromePhotosDataPlaneRuntimeSnapshot.() -> ChromePhotosDataPlaneRuntimeSnapshot,
    ) {
        if (sessionId.isBlank() || state.sessionId != sessionId) return
        state = state.transform()
    }
}
