package com.contentfilter.user.chromeguard

internal sealed interface ChromeGuardLeaseVerification {
    data object Accepted : ChromeGuardLeaseVerification

    data class Rejected(
        val reason: String,
    ) : ChromeGuardLeaseVerification
}

internal object ChromeGuardLeaseVerifier {
    fun verify(
        lease: ChromeGuardLease,
        expected: ChromeGuardExpectedSession?,
        currentBootMarker: Long,
        nowElapsed: Long,
        callerAuthorized: Boolean,
    ): ChromeGuardLeaseVerification {
        if (!callerAuthorized) return rejected("wrong_caller")
        if (expected == null) return rejected("no_current_session")
        if (lease.schemaVersion != ChromeGuardContract.SchemaVersion) return rejected("schema_stale")
        if (lease.bootMarker != currentBootMarker || lease.bootMarker != expected.bootMarker) {
            return rejected("boot_stale")
        }
        if (lease.protectionGeneration != expected.protectionGeneration) return rejected("generation_stale")
        if (lease.sessionId != expected.sessionId) return rejected("session_stale")
        if (lease.mainProcessNonce != expected.mainProcessNonce) return rejected("nonce_stale")
        if (lease.bootstrapGeneration != expected.bootstrapGeneration) return rejected("bootstrap_stale")
        if (lease.heartbeatSequence <= expected.lastHeartbeatSequence) return rejected("heartbeat_replayed")
        if (lease.issuedAtElapsedRealtime > nowElapsed) return rejected("issued_in_future")
        if (lease.expiresAtElapsedRealtime <= nowElapsed) return rejected("lease_expired")
        val lifetime = lease.expiresAtElapsedRealtime - lease.issuedAtElapsedRealtime
        if (lifetime <= 0L || lifetime > ChromeGuardContract.LeaseTtlMillis) return rejected("ttl_invalid")
        if (lease.transportGeneration <= 0L || lease.proxyGeneration <= 0L) {
            return rejected("runtime_generation_invalid")
        }
        if (!lease.health.allReady) return rejected(lease.health.firstFailureReason())
        return ChromeGuardLeaseVerification.Accepted
    }

    private fun rejected(reason: String) = ChromeGuardLeaseVerification.Rejected(reason)
}

internal fun ChromeGuardHealth.firstFailureReason(): String =
    when {
        !transportHealthy -> "transport_lost"
        !vpnHealthy -> "vpn_lost"
        !proxyHealthy -> "proxy_lost"
        !policyHealthy -> "policy_lost"
        !gloshiaHealthy -> "gloshia_lost"
        !accessibilityHealthy -> "accessibility_lost"
        !bootstrapHealthy -> "bootstrap_invalid"
        else -> "health_invalid"
    }
