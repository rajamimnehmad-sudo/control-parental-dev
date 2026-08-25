package com.contentfilter.user.chromeguard

internal object ChromeGuardContract {
    const val SchemaVersion = 1
    const val LeaseTtlMillis = 1_500L
    const val HeartbeatIntervalMillis = 500L
    const val IpcTimeoutMillis = 5_000L
    const val ChromePackage = "com.android.chrome"
    const val GuardProcessSuffix = ":chrome_guard"

    const val MessageBeginSession = 1
    const val MessageHeartbeat = 2
    const val MessageRevoke = 3
    const val MessageStatus = 4
    const val MessageStop = 5
    const val MessageDevKillGuard = 6
    const val MessageSessionOpened = 101
    const val MessageRejected = 102

    const val KeyRequestId = "request_id"
    const val KeySessionId = "session_id"
    const val KeyMainProcessNonce = "main_process_nonce"
    const val KeyBootMarker = "boot_marker"
    const val KeyBootstrapGeneration = "bootstrap_generation"
    const val KeyProtectionGeneration = "protection_generation"
    const val KeyHeartbeatSequence = "heartbeat_sequence"
    const val KeyIssuedAtElapsed = "issued_at_elapsed"
    const val KeyExpiresAtElapsed = "expires_at_elapsed"
    const val KeyTransportGeneration = "transport_generation"
    const val KeyProxyGeneration = "proxy_generation"
    const val KeyVpnHealthy = "vpn_healthy"
    const val KeyTransportHealthy = "transport_healthy"
    const val KeyProxyHealthy = "proxy_healthy"
    const val KeyPolicyHealthy = "policy_healthy"
    const val KeyGloshiaHealthy = "gloshia_healthy"
    const val KeyAccessibilityHealthy = "accessibility_healthy"
    const val KeyBootstrapHealthy = "bootstrap_healthy"
    const val KeyReason = "reason"
}

internal data class ChromeGuardHealth(
    val vpnHealthy: Boolean,
    val transportHealthy: Boolean,
    val proxyHealthy: Boolean,
    val policyHealthy: Boolean,
    val gloshiaHealthy: Boolean,
    val accessibilityHealthy: Boolean,
    val bootstrapHealthy: Boolean,
) {
    val allReady: Boolean
        get() =
            vpnHealthy &&
                transportHealthy &&
                proxyHealthy &&
                policyHealthy &&
                gloshiaHealthy &&
                accessibilityHealthy &&
                bootstrapHealthy
}

internal data class ChromeGuardSessionRequest(
    val sessionId: String,
    val mainProcessNonce: String,
    val bootMarker: Long,
    val bootstrapGeneration: Int,
)

internal data class ChromeGuardExpectedSession(
    val protectionGeneration: Long,
    val sessionId: String,
    val mainProcessNonce: String,
    val bootMarker: Long,
    val bootstrapGeneration: Int,
    val lastHeartbeatSequence: Long = 0L,
)

internal data class ChromeGuardLease(
    val schemaVersion: Int = ChromeGuardContract.SchemaVersion,
    val protectionGeneration: Long,
    val sessionId: String,
    val mainProcessNonce: String,
    val bootMarker: Long,
    val heartbeatSequence: Long,
    val issuedAtElapsedRealtime: Long,
    val expiresAtElapsedRealtime: Long,
    val transportGeneration: Long,
    val proxyGeneration: Long,
    val bootstrapGeneration: Int,
    val health: ChromeGuardHealth,
)

internal enum class ChromeGuardState {
    Suspended,
    Released,
    Unverified,
}

internal data class ChromeGuardSnapshot(
    val state: ChromeGuardState,
    val protectionGeneration: Long,
    val sessionId: String,
    val leaseExpiresAtElapsed: Long,
    val lastReason: String,
    val acceptedHeartbeats: Long,
    val staleRejects: Long,
    val wrongCallerRejects: Long,
    val guardRestarts: Long,
)

internal fun chromeGuardDeadlineDelayMillis(
    expiresAtElapsed: Long,
    nowElapsed: Long,
): Long = (expiresAtElapsed - nowElapsed).coerceIn(0L, ChromeGuardContract.LeaseTtlMillis)
