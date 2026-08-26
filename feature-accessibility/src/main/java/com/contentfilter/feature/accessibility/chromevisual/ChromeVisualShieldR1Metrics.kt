package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualShieldR1MetricsSnapshot(
    val eventsReceived: Long,
    val eventsCoalesced: Long,
    val contentInvalidations: Long,
    val inferenceStarted: Long,
    val inferenceCompleted: Long,
    val inferenceOutstanding: Long,
    val safeCurrent: Long,
    val blockCurrent: Long,
    val failClosedCurrent: Long,
    val staleInferenceDropped: Long,
    val inferenceCancelled: Long,
    val releaseCurrent: Long,
    val releaseRejected: Long,
)

internal class ChromeVisualShieldR1Metrics {
    private var eventsReceived = 0L
    private var eventsCoalesced = 0L
    private var contentInvalidations = 0L
    private var inferenceStarted = 0L
    private var inferenceCompleted = 0L
    private var inferenceOutstanding = 0L
    private var safeCurrent = 0L
    private var blockCurrent = 0L
    private var failClosedCurrent = 0L
    private var staleInferenceDropped = 0L
    private var inferenceCancelled = 0L
    private var releaseCurrent = 0L
    private var releaseRejected = 0L

    @Synchronized
    fun onEventReceived() {
        eventsReceived += 1
    }

    @Synchronized
    fun onEventCoalesced() {
        eventsCoalesced += 1
    }

    @Synchronized
    fun onContentInvalidation() {
        contentInvalidations += 1
    }

    @Synchronized
    fun onInferenceStarted() {
        inferenceStarted += 1
        inferenceOutstanding += 1
    }

    @Synchronized
    fun onInferenceCompleted() {
        inferenceCompleted += 1
        inferenceOutstanding = (inferenceOutstanding - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun onSafeCurrent() {
        safeCurrent += 1
    }

    @Synchronized
    fun onBlockCurrent() {
        blockCurrent += 1
    }

    @Synchronized
    fun onFailClosedCurrent() {
        failClosedCurrent += 1
    }

    @Synchronized
    fun onStaleInferenceDropped() {
        staleInferenceDropped += 1
    }

    @Synchronized
    fun onInferenceCancelled() {
        inferenceCancelled += 1
    }

    @Synchronized
    fun onReleaseCurrent() {
        releaseCurrent += 1
    }

    @Synchronized
    fun onReleaseRejected() {
        releaseRejected += 1
    }

    @Synchronized
    fun snapshot(): ChromeVisualShieldR1MetricsSnapshot =
        ChromeVisualShieldR1MetricsSnapshot(
            eventsReceived = eventsReceived,
            eventsCoalesced = eventsCoalesced,
            contentInvalidations = contentInvalidations,
            inferenceStarted = inferenceStarted,
            inferenceCompleted = inferenceCompleted,
            inferenceOutstanding = inferenceOutstanding,
            safeCurrent = safeCurrent,
            blockCurrent = blockCurrent,
            failClosedCurrent = failClosedCurrent,
            staleInferenceDropped = staleInferenceDropped,
            inferenceCancelled = inferenceCancelled,
            releaseCurrent = releaseCurrent,
            releaseRejected = releaseRejected,
        )
}

internal data class ChromeVisualShieldEventFingerprint(
    val eventType: Int,
    val eventTime: Long,
    val contentChangeTypes: Int,
    val windowId: Int,
    val viewport: ChromeVisualViewport,
)

/** Only exact duplicate content-change notifications may coalesce while work is already active. */
internal class ChromeVisualShieldEventCoalescer {
    private var last: ChromeVisualShieldEventFingerprint? = null

    fun shouldCoalesce(
        fingerprint: ChromeVisualShieldEventFingerprint,
        phase: ChromeVisualShieldPhase,
        eligible: Boolean,
    ): Boolean {
        val workActive =
            phase == ChromeVisualShieldPhase.CapturePending ||
                phase == ChromeVisualShieldPhase.Processing
        val duplicate = eligible && workActive && last == fingerprint
        last = fingerprint
        return duplicate
    }

    fun reset() {
        last = null
    }
}
