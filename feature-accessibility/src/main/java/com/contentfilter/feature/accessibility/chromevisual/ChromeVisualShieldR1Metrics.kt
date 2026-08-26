package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualShieldR1MetricsSnapshot(
    val eventsReceived: Long,
    val contentInvalidations: Long,
    val workSuperseded: Long,
    val inferenceStarted: Long,
    val inferenceCompleted: Long,
    val inferenceOutstanding: Long,
    val inferencePeakOutstanding: Long,
    val safeCurrent: Long,
    val blockCurrent: Long,
    val failClosedCurrent: Long,
    val staleInferenceDropped: Long,
    val inferenceCancelled: Long,
    val identityMismatchRejected: Long,
    val releaseCurrent: Long,
    val releaseRejected: Long,
    val safeDecisionAtNanos: Long,
    val releaseAtNanos: Long,
)

internal class ChromeVisualShieldR1Metrics {
    private var eventsReceived = 0L
    private var contentInvalidations = 0L
    private var workSuperseded = 0L
    private var inferenceStarted = 0L
    private var inferenceCompleted = 0L
    private var inferenceOutstanding = 0L
    private var inferencePeakOutstanding = 0L
    private var safeCurrent = 0L
    private var blockCurrent = 0L
    private var failClosedCurrent = 0L
    private var staleInferenceDropped = 0L
    private var inferenceCancelled = 0L
    private var identityMismatchRejected = 0L
    private var releaseCurrent = 0L
    private var releaseRejected = 0L
    private var safeDecisionAtNanos = 0L
    private var releaseAtNanos = 0L

    @Synchronized
    fun onEventReceived() {
        eventsReceived += 1
    }

    @Synchronized
    fun onContentInvalidation() {
        contentInvalidations += 1
    }

    @Synchronized
    fun onWorkSuperseded() {
        workSuperseded += 1
    }

    @Synchronized
    fun onInferenceStarted() {
        inferenceStarted += 1
        inferenceOutstanding += 1
        inferencePeakOutstanding = maxOf(inferencePeakOutstanding, inferenceOutstanding)
    }

    @Synchronized
    fun onInferenceCompleted() {
        check(inferenceOutstanding > 0) { "Visual Shield inference completed without a matching start" }
        inferenceCompleted += 1
        inferenceOutstanding -= 1
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
    fun onIdentityMismatchRejected() {
        identityMismatchRejected += 1
    }

    @Synchronized
    fun onSafeDecisionAccepted(atNanos: Long) {
        check(atNanos > 0) { "SAFE decision timestamp must be monotonic and positive" }
        safeDecisionAtNanos = atNanos
    }

    @Synchronized
    fun onReleaseCurrent(atNanos: Long) {
        check(safeDecisionAtNanos > 0) { "Visual Shield release recorded without an accepted SAFE" }
        check(atNanos >= safeDecisionAtNanos) { "Visual Shield release preceded its SAFE decision" }
        releaseCurrent += 1
        releaseAtNanos = atNanos
    }

    @Synchronized
    fun onReleaseRejected() {
        releaseRejected += 1
    }

    @Synchronized
    fun snapshot(): ChromeVisualShieldR1MetricsSnapshot =
        ChromeVisualShieldR1MetricsSnapshot(
            eventsReceived = eventsReceived,
            contentInvalidations = contentInvalidations,
            workSuperseded = workSuperseded,
            inferenceStarted = inferenceStarted,
            inferenceCompleted = inferenceCompleted,
            inferenceOutstanding = inferenceOutstanding,
            inferencePeakOutstanding = inferencePeakOutstanding,
            safeCurrent = safeCurrent,
            blockCurrent = blockCurrent,
            failClosedCurrent = failClosedCurrent,
            staleInferenceDropped = staleInferenceDropped,
            inferenceCancelled = inferenceCancelled,
            identityMismatchRejected = identityMismatchRejected,
            releaseCurrent = releaseCurrent,
            releaseRejected = releaseRejected,
            safeDecisionAtNanos = safeDecisionAtNanos,
            releaseAtNanos = releaseAtNanos,
        )
}
