package com.contentfilter.feature.accessibility.chromevisual

import android.view.accessibility.AccessibilityEvent

internal enum class ChromePhotosPresentationAction {
    Ignore,
    PreserveVerifiedDataPlane,
    FailClosedAndRearm,
}

/**
 * Separates compositor presentation from the legacy screenshot probe. A verified data-plane may
 * preserve transparency only for ordinary visual activity inside the exact current context.
 */
internal object ChromePhotosPresentationIndependencePolicy {
    fun decide(
        contextChanged: Boolean,
        chromeEvent: Boolean,
        eventType: Int,
        contentChangeTypes: Int,
        verifiedDataPlanePresentation: Boolean,
    ): ChromePhotosPresentationAction {
        if (contextChanged) return ChromePhotosPresentationAction.FailClosedAndRearm
        if (
            !chromeEvent ||
            !ChromePhotosProtectedSurfaceEventPolicy.requiresInvalidation(
                eventType,
                contentChangeTypes,
            )
        ) {
            return ChromePhotosPresentationAction.Ignore
        }
        return if (
            verifiedDataPlanePresentation &&
            eventType in SameContextDataPlaneEvents
        ) {
            ChromePhotosPresentationAction.PreserveVerifiedDataPlane
        } else {
            ChromePhotosPresentationAction.FailClosedAndRearm
        }
    }

    fun captureRequiredAfterOpaqueCommit(verifiedDataPlanePresentation: Boolean): Boolean =
        !verifiedDataPlanePresentation

    private val SameContextDataPlaneEvents =
        setOf(
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        )
}

internal object ChromePhotosProtectedSurfaceEventPolicy {
    fun requiresInvalidation(
        eventType: Int,
        contentChangeTypes: Int,
    ): Boolean =
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED ||
                    contentChangeTypes and VisualContentChangeMask != 0
            else -> false
        }

    fun label(eventType: Int): String =
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            else -> "TYPE_$eventType"
        }

    private val VisualContentChangeMask =
        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
}

internal data class ChromePhotosCaptureMetricsSnapshot(
    val captureRequests: Long,
    val captureSuccess: Long,
    val captureFailures: Long,
    val errorCode3: Long,
    val captureRequestsSincePresentationReady: Long,
) {
    fun logValue(): String =
        "captureRequests=$captureRequests captureSuccess=$captureSuccess " +
            "captureFailures=$captureFailures errorCode3=$errorCode3 " +
            "captureRequestsSincePresentationReady=$captureRequestsSincePresentationReady"
}

internal class ChromePhotosCaptureMetrics {
    private var captureRequests = 0L
    private var captureSuccess = 0L
    private var captureFailures = 0L
    private var errorCode3 = 0L
    private var presentationReadyBaseline = 0L

    @Synchronized
    fun onRequest() {
        captureRequests += 1L
    }

    @Synchronized
    fun onSuccess(): ChromePhotosCaptureMetricsSnapshot {
        captureSuccess += 1L
        return snapshotLocked()
    }

    @Synchronized
    fun onFailure(errorCode: Int): ChromePhotosCaptureMetricsSnapshot {
        captureFailures += 1L
        if (errorCode == ScreenshotIntervalErrorCode) errorCode3 += 1L
        return snapshotLocked()
    }

    @Synchronized
    fun markPresentationReady() {
        presentationReadyBaseline = captureRequests
    }

    @Synchronized
    fun snapshot(): ChromePhotosCaptureMetricsSnapshot = snapshotLocked()

    private fun snapshotLocked() =
        ChromePhotosCaptureMetricsSnapshot(
            captureRequests = captureRequests,
            captureSuccess = captureSuccess,
            captureFailures = captureFailures,
            errorCode3 = errorCode3,
            captureRequestsSincePresentationReady = captureRequests - presentationReadyBaseline,
        )

    private companion object {
        const val ScreenshotIntervalErrorCode = 3
    }
}
