package com.contentfilter.user.dag2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2CalibrationController
    @Inject
    constructor(
        private val queue: DagV2CalibrationCandidateQueue,
        private val sessions: DagV2DocumentSession,
        private val fetcher: DagV2CalibrationImageFetcher,
        private val normalizer: DagV2CalibrationImageNormalizer,
        private val fingerprint: DagV2CalibrationFingerprint,
        private val localDeduplicator: DagV2CalibrationLocalDeduplicator,
        private val outbox: DagV2CalibrationOutboxStore,
        private val flusher: DagV2CalibrationOutboxFlusher,
        private val metrics: DagV2Metrics,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var previewJob: Job? = null
        private val mutableState = MutableStateFlow(DagV2CalibrationReviewState())
        val state: StateFlow<DagV2CalibrationReviewState> = mutableState.asStateFlow()

        fun resetLabSession() {
            previewJob?.cancel()
            previewJob = null
            fetcher.cancelAll()
            releasePreview()
            queue.resetLabSession()
            outbox.clearTemporaryFiles()
            outbox.expirePending()
            mutableState.value = DagV2CalibrationReviewState()
            scope.launch { flushOutbox() }
        }

        fun setEnabled(enabled: Boolean) {
            if (mutableState.value.enabled == enabled) return
            if (!enabled) {
                previewJob?.cancel()
                previewJob = null
                fetcher.cancelAll()
                releasePreview()
                queue.setEnabled(false)
                mutableState.value = DagV2CalibrationReviewState()
                metrics.event(DagV2MetricNames.CalibrationDisabled)
            } else {
                queue.setEnabled(true)
                sessions.snapshot()?.let(queue::onDocument)
                mutableState.value =
                    mutableState.value.copy(
                        enabled = true,
                        candidates = queue.snapshot(),
                        statusMessage = null,
                    )
                metrics.event(DagV2MetricNames.CalibrationEnabled)
            }
        }

        fun onNewDocument(session: DagV2DocumentSessionState) {
            previewJob?.cancel()
            previewJob = null
            fetcher.cancelAll()
            releasePreview()
            queue.onDocument(session)
            mutableState.value =
                mutableState.value.copy(
                    candidates = emptyList(),
                    reviewOpen = false,
                    loadingCandidateId = null,
                    previewCandidate = null,
                    preview = null,
                    previewFingerprint = null,
                )
        }

        fun observeCandidate(
            request: DagV2ResourceRequest,
            kind: DagV2ResourceKind,
            session: DagV2DocumentSessionState,
        ) {
            if (!mutableState.value.enabled || kind != DagV2ResourceKind.RasterImage) return
            val result = queue.offer(DagV2CalibrationCandidate.from(request, session, kind))
            when (result) {
                DagV2CalibrationQueueResult.Queued -> {
                    metrics.event(DagV2MetricNames.CandidateQueued)
                    mutableState.value = mutableState.value.copy(candidates = queue.snapshot())
                }
                DagV2CalibrationQueueResult.Deduplicated ->
                    metrics.event(DagV2MetricNames.CandidateDeduplicated)
                DagV2CalibrationQueueResult.Stale ->
                    metrics.event(DagV2MetricNames.StaleCandidateDiscarded)
                DagV2CalibrationQueueResult.Disabled -> Unit
            }
        }

        fun openReview() {
            if (!mutableState.value.enabled) return
            mutableState.value = mutableState.value.copy(reviewOpen = true, statusMessage = null)
            metrics.event(DagV2MetricNames.ReviewOpened)
        }

        fun closeReview() {
            previewJob?.cancel()
            previewJob = null
            fetcher.cancelAll()
            releasePreview()
            mutableState.value =
                mutableState.value.copy(
                    reviewOpen = false,
                    loadingCandidateId = null,
                    previewCandidate = null,
                    preview = null,
                    previewFingerprint = null,
                    sending = false,
                )
        }

        fun openCandidate(candidateId: String) {
            if (!mutableState.value.enabled || mutableState.value.loadingCandidateId != null) return
            val candidate = queue.candidate(candidateId) ?: return
            if (
                !sessions.isCurrent(candidate.sessionId, candidate.navigationToken) ||
                candidate.attribution != DagV2RequestAttribution.Current
            ) {
                queue.remove(candidateId)
                mutableState.value = mutableState.value.copy(candidates = queue.snapshot())
                metrics.event(DagV2MetricNames.StaleCandidateDiscarded)
                return
            }
            previewJob?.cancel()
            releasePreview()
            mutableState.value =
                mutableState.value.copy(
                    loadingCandidateId = candidateId,
                    statusMessage = "Descargando preview segura…",
                )
            metrics.event(DagV2MetricNames.PreviewDownloadStarted)
            previewJob =
                scope.launch {
                    when (val fetched = fetcher.fetch(candidate)) {
                        is DagV2CalibrationFetchResult.Rejected -> rejectPreview(fetched.reason)
                        is DagV2CalibrationFetchResult.Success -> {
                            val sourceBytes = fetched.bytes
                            try {
                                if (!sessions.isCurrent(candidate.sessionId, candidate.navigationToken)) {
                                    rejectPreview("candidate_stale")
                                    metrics.event(DagV2MetricNames.StaleCandidateDiscarded)
                                    return@launch
                                }
                                when (val normalized = normalizer.normalize(sourceBytes)) {
                                    is DagV2CalibrationNormalizeResult.Rejected ->
                                        rejectPreview(normalized.reason)
                                    is DagV2CalibrationNormalizeResult.Success -> {
                                        val result = fingerprint.calculate(normalized.image.jpegBytes)
                                        if (!sessions.isCurrent(candidate.sessionId, candidate.navigationToken)) {
                                            normalized.image.jpegBytes.fill(0)
                                            rejectPreview("candidate_stale")
                                            return@launch
                                        }
                                        mutableState.value =
                                            mutableState.value.copy(
                                                loadingCandidateId = null,
                                                previewCandidate = candidate,
                                                preview = normalized.image,
                                                previewFingerprint = result,
                                                statusMessage = null,
                                            )
                                        metrics.event(DagV2MetricNames.PreviewReady)
                                    }
                                }
                            } finally {
                                sourceBytes.fill(0)
                            }
                        }
                    }
                }
        }

        fun label(decision: DagV2CalibrationDecision) {
            val current = mutableState.value
            val candidate = current.previewCandidate ?: return
            val image = current.preview ?: return
            val imageFingerprint = current.previewFingerprint ?: return
            if (!sessions.isCurrent(candidate.sessionId, candidate.navigationToken)) {
                closeReview()
                metrics.event(DagV2MetricNames.StaleCandidateDiscarded)
                return
            }
            val equivalentSha = localDeduplicator.equivalent(imageFingerprint)
            val submission =
                DagV2CalibrationSubmission(
                    contentSha256 = imageFingerprint.contentSha256,
                    perceptualHash = imageFingerprint.perceptualHash,
                    jpegBytes = if (equivalentSha == null) image.jpegBytes.copyOf() else null,
                    existingContentSha256 = equivalentSha,
                    width = image.width,
                    height = image.height,
                    sourceKind = candidate.resourceKind.name.lowercase(),
                    sourceHost = candidate.sanitizedResourceHost,
                    documentHost = candidate.documentOrigin.normalizedDagV2Host(),
                    sourceUrlHash = candidate.resourceUrl.dagV2Sha256(),
                    decision = decision,
                )
            val enqueueResult = outbox.enqueue(submission)
            submission.jpegBytes?.fill(0)
            if (
                enqueueResult != DagV2CalibrationEnqueueResult.Queued &&
                enqueueResult != DagV2CalibrationEnqueueResult.Duplicate
            ) {
                mutableState.value = mutableState.value.withEnqueueFailure(enqueueResult)
                return
            }
            localDeduplicator.remember(imageFingerprint)
            metrics.event(
                when (decision) {
                    DagV2CalibrationDecision.Show -> DagV2MetricNames.LabelShow
                    DagV2CalibrationDecision.Hide -> DagV2MetricNames.LabelHide
                    DagV2CalibrationDecision.Unsure -> DagV2MetricNames.LabelUnsure
                },
            )
            if (enqueueResult == DagV2CalibrationEnqueueResult.Queued) {
                metrics.event(DagV2MetricNames.LabelQueued)
            }
            queue.remove(candidate.candidateId)
            releasePreview()
            mutableState.value =
                mutableState.value.copy(
                    candidates = queue.snapshot(),
                    loadingCandidateId = null,
                    previewCandidate = null,
                    preview = null,
                    previewFingerprint = null,
                    sending = true,
                    statusMessage =
                        if (enqueueResult == DagV2CalibrationEnqueueResult.Queued) {
                            "Etiqueta cifrada y pendiente de entrega."
                        } else {
                            "La misma etiqueta ya estaba pendiente; se reintentará su entrega."
                        },
                )
            scope.launch { flushOutbox() }
        }

        fun closeLab() {
            previewJob?.cancel()
            previewJob = null
            fetcher.cancelAll()
            releasePreview()
            queue.clearSensitiveState()
            mutableState.value = DagV2CalibrationReviewState()
        }

        private suspend fun flushOutbox() {
            when (val result = flusher.flush()) {
                is DagV2CalibrationDeliveryResult.Accepted ->
                    mutableState.value =
                        mutableState.value.copy(
                            sending = false,
                            statusMessage =
                                if (result.deduplicated) {
                                    "Muestra equivalente deduplicada; etiqueta y auditoría registradas."
                                } else {
                                    "Muestra privada, etiqueta y auditoría registradas."
                                },
                        )
                is DagV2CalibrationDeliveryResult.PermanentFailure ->
                    mutableState.value =
                        mutableState.value.copy(
                            sending = false,
                            statusMessage = "Entrega rechazada: ${result.reason}.",
                        )
                is DagV2CalibrationDeliveryResult.TemporaryFailure ->
                    mutableState.value =
                        mutableState.value.copy(
                            sending = false,
                            statusMessage =
                                "Entrega pendiente (${result.reason}); se reintentará al abrir el Lab.",
                        )
                null -> mutableState.value = mutableState.value.copy(sending = false)
            }
        }

        private fun rejectPreview(reason: String) {
            releasePreview()
            mutableState.value =
                mutableState.value.copy(
                    loadingCandidateId = null,
                    previewCandidate = null,
                    preview = null,
                    previewFingerprint = null,
                    statusMessage = "Preview rechazada: $reason.",
                )
            metrics.event(DagV2MetricNames.PreviewRejected)
        }

        private fun releasePreview() {
            mutableState.value.preview?.jpegBytes?.fill(0)
        }
    }
