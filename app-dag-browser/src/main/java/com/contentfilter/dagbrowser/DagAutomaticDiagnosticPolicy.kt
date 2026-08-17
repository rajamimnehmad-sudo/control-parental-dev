package com.contentfilter.dagbrowser

/** Limits automatic reports to failures that can leave a protected video without a usable frame. */
internal object DagAutomaticDiagnosticPolicy {
    private val BlackVideoFailureReasons =
        setOf(
            "cover_timeout",
            "frame_ready_timeout",
            "revoke_timeout",
            "revoke_request_not_delivered",
        )

    const val CooldownMillis = 15 * 60 * 1_000L

    fun shouldReport(
        reason: String,
        privateTab: Boolean,
        uploaderConfigured: Boolean,
        nowMillis: Long,
        lastAttemptMillis: Long,
    ): Boolean {
        if (reason !in BlackVideoFailureReasons || privateTab || !uploaderConfigured) return false
        if (lastAttemptMillis <= 0L || nowMillis < lastAttemptMillis) return true
        return nowMillis - lastAttemptMillis >= CooldownMillis
    }
}
