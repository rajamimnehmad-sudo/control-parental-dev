package com.contentfilter.feature.accessibility.chromevisual

internal const val ChallengeBytes = 32
internal const val DigestPrefixLength = 12
internal const val HoldTimeoutMillis = 4_000L
internal const val LeaseWatchdogMillis = 100L
internal const val LeaseRenewalLeadMillis = 150L
internal const val HoldHelloAccepted = "hello_accepted"
internal const val HoldChallengeIssued = "challenge_issued"
internal const val HoldProofAccepted = "proof_accepted"
internal const val LogTag = "ChromeMediaShieldActiveDocument"
internal const val ActiveDocumentChromePackageName = "com.android.chrome"

/** DEV protocol formatting kept outside the serialized presentation authority. */
internal object ChromeMediaShieldActiveDocumentProtocolDiagnostics {
    val zeroDigest: String = "0".repeat(64)

    fun status(
        metrics: String,
        foreground: ChromeMediaShieldActiveDocumentNativeBinding?,
        owned: ChromeMediaShieldActiveDocumentNativeBinding?,
        pendingHandshake: Int,
        holdPhase: String,
    ): String =
        "protocol=active_document_v3 phase=active_document_status $metrics " +
            "foregroundWindowId=${foreground?.windowId ?: -1} " +
            "foregroundRootDigest=${foreground?.nativeRootDigest ?: zeroDigest} " +
            "attemptWindowId=${owned?.windowId ?: -1} " +
            "attemptRootDigest=${owned?.nativeRootDigest ?: zeroDigest} " +
            "pendingHandshake=$pendingHandshake holdPhase=$holdPhase"

    fun canonicalReason(
        reason: String,
        closed: Boolean,
    ): String =
        when (reason) {
            "hello_claim_invalid",
            "hello_claim_stale",
            "hello_superseded",
            "hello_foreground_ambiguous",
            "foreground_window_unavailable",
            "foreground_root_unavailable",
            "foreground_root_package_mismatch",
            "foreground_root_window_mismatch",
            "foreground_root_identity_unavailable",
            "foreground_viewport_invalid",
            "context_read_exception",
            "hello_context_stale",
            "hello_surface_failed",
            "prove_challenge_invalid",
            "prove_replay",
            "prove_context_changed",
            "prove_health_stale",
            "present_not_proved",
            "present_replay",
            "present_context_changed",
            "present_surface_not_opaque",
            "present_commit_failed",
            "present_postcommit_context_changed",
            "invalidated_hidden",
            "invalidated_pagehide",
            "invalidated_navigation",
            "invalidated_root",
            "invalidated_window",
            "invalidated_surface",
            "invalidated_session",
            "invalidated_stop",
            "invalidated_health",
            "hold_timeout",
            "hold_cancelled",
            "handshake_transport_cancelled",
            "handshake_closed",
            -> reason
            else -> if (closed) "invalidated_stop" else "invalidated_surface"
        }
}
