package com.contentfilter.user.chromeguard

import android.os.Bundle

internal object ChromeGuardBundleCodec {
    fun sessionRequest(bundle: Bundle): ChromeGuardSessionRequest =
        ChromeGuardSessionRequest(
            sessionId = bundle.getString(ChromeGuardContract.KeySessionId).orEmpty(),
            mainProcessNonce = bundle.getString(ChromeGuardContract.KeyMainProcessNonce).orEmpty(),
            bootMarker = bundle.getLong(ChromeGuardContract.KeyBootMarker, -1L),
            bootstrapGeneration = bundle.getInt(ChromeGuardContract.KeyBootstrapGeneration, 0),
        )

    fun lease(bundle: Bundle): ChromeGuardLease =
        ChromeGuardLease(
            protectionGeneration = bundle.getLong(ChromeGuardContract.KeyProtectionGeneration),
            sessionId = bundle.getString(ChromeGuardContract.KeySessionId).orEmpty(),
            mainProcessNonce = bundle.getString(ChromeGuardContract.KeyMainProcessNonce).orEmpty(),
            bootMarker = bundle.getLong(ChromeGuardContract.KeyBootMarker, -1L),
            heartbeatSequence = bundle.getLong(ChromeGuardContract.KeyHeartbeatSequence),
            issuedAtElapsedRealtime = bundle.getLong(ChromeGuardContract.KeyIssuedAtElapsed),
            expiresAtElapsedRealtime = bundle.getLong(ChromeGuardContract.KeyExpiresAtElapsed),
            transportGeneration = bundle.getLong(ChromeGuardContract.KeyTransportGeneration),
            proxyGeneration = bundle.getLong(ChromeGuardContract.KeyProxyGeneration),
            bootstrapGeneration = bundle.getInt(ChromeGuardContract.KeyBootstrapGeneration),
            health =
                ChromeGuardHealth(
                    vpnHealthy = bundle.getBoolean(ChromeGuardContract.KeyVpnHealthy),
                    transportHealthy = bundle.getBoolean(ChromeGuardContract.KeyTransportHealthy),
                    proxyHealthy = bundle.getBoolean(ChromeGuardContract.KeyProxyHealthy),
                    policyHealthy = bundle.getBoolean(ChromeGuardContract.KeyPolicyHealthy),
                    gloshiaHealthy = bundle.getBoolean(ChromeGuardContract.KeyGloshiaHealthy),
                    accessibilityHealthy = bundle.getBoolean(ChromeGuardContract.KeyAccessibilityHealthy),
                    bootstrapHealthy = bundle.getBoolean(ChromeGuardContract.KeyBootstrapHealthy),
                ),
        )

    fun session(request: ChromeGuardSessionRequest): Bundle =
        Bundle().apply {
            putString(ChromeGuardContract.KeySessionId, request.sessionId)
            putString(ChromeGuardContract.KeyMainProcessNonce, request.mainProcessNonce)
            putLong(ChromeGuardContract.KeyBootMarker, request.bootMarker)
            putInt(ChromeGuardContract.KeyBootstrapGeneration, request.bootstrapGeneration)
        }

    fun lease(lease: ChromeGuardLease): Bundle =
        Bundle().apply {
            putLong(ChromeGuardContract.KeyProtectionGeneration, lease.protectionGeneration)
            putString(ChromeGuardContract.KeySessionId, lease.sessionId)
            putString(ChromeGuardContract.KeyMainProcessNonce, lease.mainProcessNonce)
            putLong(ChromeGuardContract.KeyBootMarker, lease.bootMarker)
            putLong(ChromeGuardContract.KeyHeartbeatSequence, lease.heartbeatSequence)
            putLong(ChromeGuardContract.KeyIssuedAtElapsed, lease.issuedAtElapsedRealtime)
            putLong(ChromeGuardContract.KeyExpiresAtElapsed, lease.expiresAtElapsedRealtime)
            putLong(ChromeGuardContract.KeyTransportGeneration, lease.transportGeneration)
            putLong(ChromeGuardContract.KeyProxyGeneration, lease.proxyGeneration)
            putInt(ChromeGuardContract.KeyBootstrapGeneration, lease.bootstrapGeneration)
            putBoolean(ChromeGuardContract.KeyVpnHealthy, lease.health.vpnHealthy)
            putBoolean(ChromeGuardContract.KeyTransportHealthy, lease.health.transportHealthy)
            putBoolean(ChromeGuardContract.KeyProxyHealthy, lease.health.proxyHealthy)
            putBoolean(ChromeGuardContract.KeyPolicyHealthy, lease.health.policyHealthy)
            putBoolean(ChromeGuardContract.KeyGloshiaHealthy, lease.health.gloshiaHealthy)
            putBoolean(ChromeGuardContract.KeyAccessibilityHealthy, lease.health.accessibilityHealthy)
            putBoolean(ChromeGuardContract.KeyBootstrapHealthy, lease.health.bootstrapHealthy)
        }
}
