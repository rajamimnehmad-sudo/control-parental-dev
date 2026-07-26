package com.contentfilter.user.dag2

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class DagV2MetricSnapshot(
    val nonImageBypassCount: Int = 0,
    val imageRequestCount: Int = 0,
    val imagePlaceholderCount: Int = 0,
    val imageCancelledCount: Int = 0,
    val serviceWorkerRequestCount: Int = 0,
    val visualPendingCount: Int = 0,
)

internal object DagV2MetricNames {
    const val DocumentStarted = "document_started"
    const val DocumentCommitted = "document_committed"
    const val FullPageAnalysisStarted = "full_page_analysis_started"
    const val FullPageAnalysisCompleted = "full_page_analysis_completed"
    const val FullPageAnalysisCount = "full_page_analysis_count"
    const val StructureVisible = "structure_visible"
    const val VisualPlaceholderReady = "visual_placeholder_ready"
    const val StaleResultDiscarded = "stale_result_discarded"
    const val SessionCancelled = "session_cancelled"
    const val FunctionalStable20s = "functional_stable_20s"
    const val NoCacheModeEnabled = "no_cache_mode_enabled"
    const val RendererGone = "renderer_gone"
    const val ConsoleError = "console_error"
    const val CalibrationEnabled = "calibration_enabled"
    const val CalibrationDisabled = "calibration_disabled"
    const val CandidateQueued = "candidate_queued"
    const val CandidateDeduplicated = "candidate_deduplicated"
    const val ReviewOpened = "review_opened"
    const val PreviewDownloadStarted = "preview_download_started"
    const val PreviewReady = "preview_ready"
    const val PreviewRejected = "preview_rejected"
    const val LabelShow = "label_show"
    const val LabelHide = "label_hide"
    const val LabelUnsure = "label_unsure"
    const val LabelQueued = "label_queued"
    const val LabelDelivered = "label_delivered"
    const val LabelRejected = "label_rejected"
    const val OutboxFlushed = "outbox_flushed"
    const val StaleCandidateDiscarded = "stale_candidate_discarded"

    val RequiredFoundationEvents =
        setOf(
            DocumentStarted,
            DocumentCommitted,
            FullPageAnalysisStarted,
            FullPageAnalysisCompleted,
            FullPageAnalysisCount,
            StructureVisible,
            VisualPlaceholderReady,
            StaleResultDiscarded,
            SessionCancelled,
            FunctionalStable20s,
        )
}

@Singleton
class DagV2Metrics
    @Inject
    constructor() {
        private val nonImageBypass = AtomicInteger()
        private val imageRequests = AtomicInteger()
        private val imagePlaceholders = AtomicInteger()
        private val imageCancelled = AtomicInteger()
        private val serviceWorkerRequests = AtomicInteger()
        private val visualPending = AtomicInteger()
        private val activeNavigationToken = AtomicReference<String?>(null)
        private val mutableSnapshot = MutableStateFlow(DagV2MetricSnapshot())

        val snapshot: StateFlow<DagV2MetricSnapshot> = mutableSnapshot.asStateFlow()

        fun event(
            name: String,
            session: DagV2DocumentSessionState? = null,
            value: Int? = null,
        ) {
            val fields =
                buildString {
                    append("event=")
                    append(name)
                    session?.let {
                        append(" session=")
                        append(it.sessionId.take(8))
                        append(" token=")
                        append(it.navigationToken.take(8))
                    }
                    value?.let {
                        append(" value=")
                        append(it)
                    }
                }
            Log.i(LogTag, fields)
        }

        fun nonImageBypass() {
            nonImageBypass.incrementAndGet()
            publish()
        }

        fun beginDocument(session: DagV2DocumentSessionState) {
            activeNavigationToken.set(session.navigationToken)
            visualPending.set(0)
            publish()
        }

        fun imageRequest(
            source: DagV2ResourceSource,
            session: DagV2DocumentSessionState?,
        ) {
            imageRequests.incrementAndGet()
            if (session?.navigationToken == activeNavigationToken.get()) visualPending.incrementAndGet()
            @Suppress("UNUSED_VARIABLE")
            val recordedSource = source
            publish()
        }

        fun serviceWorkerRequest() {
            serviceWorkerRequests.incrementAndGet()
            publish()
        }

        fun imagePlaceholder(session: DagV2DocumentSessionState?) {
            imagePlaceholders.incrementAndGet()
            if (session?.navigationToken == activeNavigationToken.get()) {
                visualPending.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
            publish()
        }

        fun visualPlaceholderReady(session: DagV2DocumentSessionState?) {
            imagePlaceholder(session)
            event(DagV2MetricNames.VisualPlaceholderReady, session)
        }

        fun imageCancelled() {
            imageCancelled.incrementAndGet()
            publish()
        }

        fun imageCompleted(session: DagV2DocumentSessionState) {
            if (session.navigationToken == activeNavigationToken.get()) {
                visualPending.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
            publish()
        }

        fun staleResultDiscarded() {
            event(DagV2MetricNames.StaleResultDiscarded)
        }

        fun sessionCancelled(session: DagV2DocumentSessionState) {
            event(DagV2MetricNames.SessionCancelled, session)
        }

        private fun publish() {
            mutableSnapshot.value =
                DagV2MetricSnapshot(
                    nonImageBypassCount = nonImageBypass.get(),
                    imageRequestCount = imageRequests.get(),
                    imagePlaceholderCount = imagePlaceholders.get(),
                    imageCancelledCount = imageCancelled.get(),
                    serviceWorkerRequestCount = serviceWorkerRequests.get(),
                    visualPendingCount = visualPending.get(),
                )
        }

        private companion object {
            const val LogTag = "DagV2Metrics"
        }
    }
