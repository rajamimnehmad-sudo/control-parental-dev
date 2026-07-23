package com.contentfilter.user.dag

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.model.dagExtraKosherEnabled
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DagBrowserViewModel
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val activationRepository: DeviceActivationRepository,
        private val accessRequestRepository: AccessRequestRepository,
        private val searchRepository: DagSearchRepository,
        private val calibrationRepository: DagCalibrationRepository,
        private val classifier: DagContentClassifier,
        private val historyStore: DagHistoryStore,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
        systemStatusRepository: SystemStatusRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DagBrowserUiState())
        val uiState: StateFlow<DagBrowserUiState> = mutableState.asStateFlow()

        @Volatile
        private var activeRules: List<PolicyRule> = emptyList()

        @Volatile
        private var activePolicyVersion: Long = 0L
        private var approvalPollingJob: Job? = null
        private var suggestionJob: Job? = null
        private var searchJob: Job? = null
        private var queryClassificationJob: Job? = null
        private var pageClassificationJob: Job? = null
        private val searchRequestTracker = DagSearchRequestTracker()
        private val pageAnalysisTracker = DagPageAnalysisTracker()
        private val reviewSubmissionsInFlight = mutableSetOf<String>()
        private var pageAnalysisStartedAtMillis = 0L

        init {
            viewModelScope.launch {
                syncScheduler.requestSync()
                withContext(Dispatchers.IO) { runCatching { syncEngine.syncOnce() } }
                withContext(Dispatchers.IO) {
                    refreshCalibrationFromDev()
                }
            }
            viewModelScope.launch {
                combine(
                    policyRepository.observeActivePolicy(),
                    activationRepository.observeActivation(),
                    systemStatusRepository.observeHealth(),
                ) { snapshot, activation, health -> Triple(snapshot, activation, health) }
                    .collect { (snapshot, activation, health) ->
                        activeRules = snapshot.rules
                        activePolicyVersion = snapshot.version
                        val enabled = activation != null && health.dagEntitled && snapshot.rules.dagEnabled()
                        val extraKosherEnabled = enabled && snapshot.rules.dagExtraKosherEnabled()
                        if (!enabled) {
                            cancelActiveSearch()
                            invalidatePageAnalysis()
                        }
                        mutableState.update { state ->
                            state.withDagAvailability(enabled).copy(dagExtraKosherEnabled = extraKosherEnabled)
                        }
                    }
            }
            viewModelScope.launch {
                historyStore.observe().collect { entries ->
                    mutableState.update { it.copy(history = entries) }
                }
            }
            viewModelScope.launch {
                accessRequestRepository.observeRequests().collect { requests ->
                    val reviews =
                        requests
                            .filter { it.requestType == AccessRequestType.DOMAIN_ACCESS }
                            .sortedByDescending(AccessRequest::createdAtEpochMillis)
                    mutableState.update { it.copy(reviewRequests = reviews) }
                }
            }
        }

        fun refreshCalibration() {
            viewModelScope.launch(Dispatchers.IO) { refreshCalibrationFromDev() }
        }

        private suspend fun refreshCalibrationFromDev() {
            val activation = activationRepository.currentActivation() ?: return
            runCatching { calibrationRepository.flushPending(activation.deviceId) }
            val result = runCatching { calibrationRepository.refresh(activation.deviceId) }.getOrNull()
            if (result is RemoteResult.Success) {
                mutableState.update { it.copy(calibrationVersion = calibrationRepository.currentVersion()) }
            }
        }

        fun onAddressChanged(value: String) {
            val address = value.take(MaxAddressCharacters)
            mutableState.update { it.copy(address = address, message = "", suggestions = emptyList()) }
            suggestionJob?.cancel()
            if (address.isBlank()) return
            suggestionJob =
                viewModelScope.launch {
                    delay(SuggestionDebounceMillis)
                    val history = mutableState.value.history
                    val suggestions =
                        withContext(Dispatchers.Default) {
                            dagSearchSuggestionCandidates(address, history)
                                .filter { classifier.classifyQuery(it).decision == DagClassification.Allowed }
                        }
                    if (mutableState.value.address == address) {
                        mutableState.update { it.copy(suggestions = suggestions) }
                    }
                }
        }

        internal fun submitDagCalibrationCandidate(
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ) {
            if (!mutableState.value.dagEnabled || classification.decision != DagImageDecision.Uncertain) return
            viewModelScope.launch(Dispatchers.IO) {
                val activation = activationRepository.currentActivation() ?: return@launch
                runCatching {
                    calibrationRepository.submitReview(activation.deviceId, thumbnail, classification)
                }.onSuccess { result ->
                    if (result == DagCalibrationDeliveryResult.Queued) {
                        Log.d("DagCalibration", "candidate_queued_for_retry")
                    }
                }.onFailure { Log.d("DagCalibration", "candidate_outbox_failed") }
            }
        }

        internal fun submitDagManualCalibrationCandidate(
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ) {
            if (!mutableState.value.dagEnabled || classification.scores.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                val activation = activationRepository.currentActivation() ?: return@launch
                runCatching {
                    calibrationRepository.submitManualBlock(activation.deviceId, thumbnail, classification)
                }.onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            message =
                                when (result) {
                                    DagCalibrationDeliveryResult.Accepted ->
                                        "Foto marcada para revisar en Calibración DAG."
                                    DagCalibrationDeliveryResult.Queued ->
                                        "Foto guardada; se enviará para revisión cuando vuelva la conexión."
                                    is DagCalibrationDeliveryResult.Rejected ->
                                        "La foto quedó difuminada, pero el servidor no la agregó a revisión."
                                },
                        )
                    }
                }.onFailure {
                    Log.d("DagCalibration", "manual_report_upload_failed")
                    mutableState.update {
                        it.copy(message = "La foto quedó difuminada, pero no se pudo enviar para revisión.")
                    }
                }
            }
        }

        internal fun submitDagManualBlurReviewCandidate(
            thumbnail: ByteArray,
            classification: DagImageClassification,
        ) {
            if (
                !mutableState.value.dagEnabled ||
                classification.scores.isEmpty()
            ) {
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                val activation = activationRepository.currentActivation() ?: return@launch
                runCatching {
                    calibrationRepository.submitManualBlurReview(activation.deviceId, thumbnail, classification)
                }.onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            message =
                                when (result) {
                                    DagCalibrationDeliveryResult.Accepted ->
                                        "Foto enviada para revisar un posible falso positivo."
                                    DagCalibrationDeliveryResult.Queued ->
                                        "Foto guardada; se enviará para revisión cuando vuelva la conexión."
                                    is DagCalibrationDeliveryResult.Rejected ->
                                        "El servidor no agregó la foto a revisión."
                                },
                        )
                    }
                }.onFailure {
                    Log.d("DagCalibration", "manual_blur_review_upload_failed")
                    mutableState.update { it.copy(message = "No se pudo enviar la foto para revisión.") }
                }
            }
        }

        fun submitAddress() {
            val input = mutableState.value.address.trim()
            if (!mutableState.value.dagEnabled || input.isBlank() || mutableState.value.loading) return
            val url = input.toHttpsUrlOrNull()
            if (url != null) {
                requestNavigation(url, title = DagContentClassifier.domainFrom(url))
            } else {
                search(input)
            }
        }

        fun search(query: String) {
            val normalizedQuery = query.trim().take(MaxAddressCharacters)
            if (!mutableState.value.dagEnabled || normalizedQuery.isBlank()) return
            cancelActiveSearch()
            invalidatePageAnalysis()
            mutableState.update {
                it.copy(
                    loading = true,
                    analysisProgress = 0.03f,
                    message = "",
                    suggestions = emptyList(),
                    reviewCandidate = null,
                )
            }
            val job =
                viewModelScope.launch {
                    val classification =
                        withContext(Dispatchers.Default) {
                            classifier.classifyQueryStaged(normalizedQuery)
                        }
                    if (!mutableState.value.dagEnabled) return@launch
                    when (dagQueryAction(classification.decision)) {
                        DagQueryAction.Block -> {
                            mutableState.update {
                                it.copy(
                                    view = DagView.Start,
                                    loading = false,
                                    analysisProgress = 0f,
                                    message = "La búsqueda fue bloqueada por la protección DAG.",
                                    results = emptyList(),
                                    suggestions = emptyList(),
                                    reviewCandidate = null,
                                )
                            }
                        }
                        DagQueryAction.Search -> performSearch(normalizedQuery)
                    }
                }
            queryClassificationJob = job
            job.invokeOnCompletion {
                if (queryClassificationJob === job) queryClassificationJob = null
            }
        }

        private fun performSearch(
            query: String,
            page: Int = 0,
            append: Boolean = false,
        ) {
            val requestId = searchRequestTracker.begin(DagSearchRequest(query, page, append)) ?: return
            searchJob?.cancel()
            val job =
                viewModelScope.launch {
                    mutableState.update {
                        it.copy(
                            loading = true,
                            analysisProgress = 0.05f,
                            message = "",
                            reviewCandidate = null,
                            pageStatus = DagPageStatus.Idle,
                            suggestions = emptyList(),
                        )
                    }
                    val activation = withContext(Dispatchers.IO) { activationRepository.currentActivation() }
                    if (activation == null) {
                        if (!searchRequestTracker.isCurrent(requestId)) return@launch
                        mutableState.update {
                            it.copy(
                                loading = false,
                                analysisProgress = 0f,
                                message = "DAG no está vinculado a este dispositivo.",
                            )
                        }
                        return@launch
                    }
                    when (
                        val response =
                            searchRepository.search(
                                deviceId = activation.deviceId,
                                query = query,
                                language = query.dagLanguage(),
                                page = page,
                            )
                    ) {
                        is RemoteResult.Failure -> {
                            if (!searchRequestTracker.isCurrent(requestId)) return@launch
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    analysisProgress = 0f,
                                    message = response.reason,
                                )
                            }
                        }
                        is RemoteResult.Success -> {
                            val classifiedWithReasons =
                                withContext(Dispatchers.Default) {
                                    val validResults =
                                        response.value.results.mapNotNull { remote ->
                                            val domain = DagContentClassifier.domainFrom(remote.url)
                                            if (domain.isBlank()) return@mapNotNull null
                                            remote to domain
                                        }
                                    val localDecisions =
                                        classifier.classifyResultsWithReasonStaged(
                                            validResults.map { (remote, _) ->
                                                DagSearchClassificationInput(
                                                    title = remote.title,
                                                    description = remote.description,
                                                    url = remote.url,
                                                )
                                            },
                                        )
                                    validResults.zip(localDecisions).map { (remoteWithDomain, classifiedLocal) ->
                                        val (remote, domain) = remoteWithDomain
                                        val decision =
                                            applyExplicitRule(
                                                domain = domain,
                                                result = classifiedLocal.classification,
                                            )
                                        val reason =
                                            if (
                                                decision.decision == DagClassification.Blocked &&
                                                decision.category == "admin_block"
                                            ) {
                                                DagSearchDecisionReason.AdminRuleBlock
                                            } else {
                                                classifiedLocal.reason
                                            }
                                        reason to
                                            DagSearchResult(
                                                title = remote.title,
                                                url = remote.url,
                                                domain = domain,
                                                description = remote.description,
                                                classification = decision,
                                            )
                                    }
                                }
                            if (!searchRequestTracker.isCurrent(requestId)) return@launch
                            val diagnostics =
                                dagSearchDiagnostics(
                                    braveReceived = response.value.braveReceived,
                                    serverRejected = response.value.serverRejected,
                                    decisions = classifiedWithReasons.map { it.first },
                                )
                            Log.i(
                                DiagnosticsLogTag,
                                "results brave=${diagnostics.braveReceived} serverRejected=${diagnostics.serverRejected} " +
                                    "domainListBlocked=${diagnostics.domainListBlocked} " +
                                    "adminRuleBlocked=${diagnostics.adminRuleBlocked} " +
                                    "platformBlocked=${diagnostics.platformBlocked} " +
                                    "localClassifierBlocked=${diagnostics.localClassifierBlocked} " +
                                    "uncertainShown=${diagnostics.uncertainShown} " +
                                    "allowedShown=${diagnostics.allowedShown} shown=${diagnostics.shown}",
                            )
                            val classified =
                                classifiedWithReasons.mapNotNull { (reason, result) ->
                                    result.takeUnless {
                                        reason == DagSearchDecisionReason.DomainListBlock ||
                                            reason == DagSearchDecisionReason.AdminRuleBlock ||
                                            reason == DagSearchDecisionReason.PlatformBlock ||
                                            reason == DagSearchDecisionReason.LocalClassifierBlock
                                    }
                                }
                            if (!append) {
                                withContext(Dispatchers.IO) { historyStore.addSearch(query) }
                            }
                            if (!searchRequestTracker.isCurrent(requestId)) return@launch
                            mutableState.update { state ->
                                val combined =
                                    if (append) {
                                        (state.results + classified).distinctBy(DagSearchResult::url)
                                    } else {
                                        classified
                                    }
                                state.copy(
                                    address = query,
                                    loading = false,
                                    analysisProgress = 1f,
                                    view = DagView.Results,
                                    results = combined,
                                    searchQuery = query,
                                    searchPage = page,
                                    canLoadMoreResults = dagCanLoadMoreResults(page, response.value.hasMoreResults),
                                    suggestions = emptyList(),
                                    message =
                                        if (combined.isEmpty()) {
                                            "DAG no encontró resultados que pueda mostrar."
                                        } else {
                                            ""
                                        },
                                )
                            }
                        }
                    }
                }
            searchJob = job
            job.invokeOnCompletion { searchRequestTracker.complete(requestId) }
        }

        fun loadMoreResults() {
            val state = mutableState.value
            if (!state.dagEnabled || state.loading || !state.canLoadMoreResults || state.searchQuery.isBlank()) return
            performSearch(state.searchQuery, page = state.searchPage + 1, append = true)
        }

        fun selectSuggestion(suggestion: String) {
            val state = mutableState.value
            if (state.loading || !state.dagEnabled) return
            val accepted =
                mutableState.compareAndSet(
                    state,
                    state.copy(
                        address = suggestion,
                        loading = true,
                        analysisProgress = 0.05f,
                        message = "",
                        suggestions = emptyList(),
                    ),
                )
            if (accepted) search(suggestion)
        }

        fun openResult(result: DagSearchResult) {
            when (result.classification.decision) {
                DagClassification.Allowed,
                DagClassification.Uncertain,
                -> requestNavigation(result.url, result.title)
                DagClassification.Blocked -> Unit
            }
        }

        fun requestNavigation(
            rawUrl: String,
            title: String = "",
        ) {
            if (!mutableState.value.dagEnabled) return
            val url = rawUrl.toHttpsUrlOrNull()
            if (url == null) {
                mutableState.update { it.copy(message = "DAG solo admite direcciones web HTTPS.") }
                return
            }
            cancelActiveSearch()
            invalidatePageAnalysis()
            val domain = DagContentClassifier.domainFrom(url)
            val classification = applyExplicitRule(domain, classifier.classifyDirectUrl(url))
            when (classification.decision) {
                DagClassification.Blocked ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            pageStatus = DagPageStatus.Blocked,
                            message = "DAG bloqueó este sitio.",
                            reviewCandidate = null,
                            suggestions = emptyList(),
                        )
                    }
                DagClassification.Uncertain ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            pageStatus = DagPageStatus.Uncertain,
                            message = "Este sitio necesita revisión del administrador.",
                            reviewCandidate =
                                DagReviewCandidate(
                                    url = url,
                                    domain = domain,
                                    title = title.ifBlank { domain },
                                    category = classification.category,
                                    modelVersion = classification.modelVersion,
                                ),
                        )
                    }
                DagClassification.Allowed ->
                    mutableState.update {
                        pageAnalysisStartedAtMillis = SystemClock.elapsedRealtime()
                        it.copy(
                            address = url,
                            loading = false,
                            view = DagView.Browser,
                            pageStatus = DagPageStatus.Loading,
                            pageAnalysisReady = false,
                            viewportImagesReady = false,
                            analysisProgress = 0.10f,
                            requestedUrl = url,
                            navigationRevision = it.navigationRevision + 1,
                            message = "Analizando la página antes de mostrarla…",
                            reviewCandidate = null,
                        )
                    }
            }
        }

        internal fun onPageTextReady(
            url: String,
            title: String,
            text: String?,
            images: DagImagePageSummary,
        ) {
            val hasMeaningfulText = !text.isNullOrBlank()
            val currentState = mutableState.value
            if (
                !currentState.dagEnabled ||
                !dagCanAnalyzePageText(
                    state = currentState,
                    url = url,
                    hasMeaningfulText = hasMeaningfulText,
                )
            ) {
                return
            }
            Log.d(
                PerformanceLogTag,
                "page_text_ready elapsed_ms=${SystemClock.elapsedRealtime() - pageAnalysisStartedAtMillis} " +
                    "chars=${text?.length ?: 0}",
            )
            val analysis = beginPageAnalysis(url)
            mutableState.update { state ->
                state.beginDagPageTextAnalysis(url, hasMeaningfulText)
            }
            if (!hasMeaningfulText) {
                showUncertainPage(
                    url = analysis.url,
                    title = title,
                    category = DAG_UNREADABLE_PAGE_CATEGORY,
                    modelVersion = DagContentClassifier.ModelVersion,
                )
                return
            }
            pageClassificationJob =
                viewModelScope.launch(Dispatchers.Default) {
                    val classificationStartedAtMillis = SystemClock.elapsedRealtime()
                    val domain = DagContentClassifier.domainFrom(url)
                    val fingerprint = dagPageFingerprint(url, title, text, images)
                    val policyVersion = activePolicyVersion
                    val cachedApproval =
                        withContext(Dispatchers.IO) {
                            historyStore.hasFreshPageApproval(
                                url = url,
                                fingerprint = fingerprint,
                                policyVersion = policyVersion,
                                modelVersion = PageApprovalModelVersion,
                            )
                        }
                    val result =
                        if (cachedApproval) {
                            applyExplicitRule(domain, classifier.classifyDirectUrl(url))
                        } else {
                            applyExplicitRule(domain, classifier.classifyPageStaged(url, title, text, images))
                        }
                    Log.d(
                        PerformanceLogTag,
                        "page_classified elapsed_ms=${SystemClock.elapsedRealtime() - classificationStartedAtMillis} " +
                            "cached=$cachedApproval decision=${result.decision} category=${result.category} " +
                            "model=${result.modelVersion}",
                    )
                    val pageDecision = dagAdaptivePageDecision(result)
                    val applied =
                        withContext(Dispatchers.Main) {
                            if (!isCurrentPageAnalysis(analysis)) {
                                return@withContext false
                            }
                            when (pageDecision) {
                                DagAdaptivePageDecision.Allowed -> {
                                    mutableState.update {
                                        it.copy(
                                            address = url,
                                            pageAnalysisReady = true,
                                            analysisProgress = maxOf(it.analysisProgress, 0.65f),
                                            message = "",
                                            reviewCandidate = null,
                                        )
                                    }
                                    revealPageIfReady(analysis)
                                }
                                DagAdaptivePageDecision.Protected ->
                                    showProtectedPage(analysis)
                                DagAdaptivePageDecision.Blocked ->
                                    mutableState.update {
                                        it.copy(
                                            pageStatus = DagPageStatus.Blocked,
                                            message = "DAG bloqueó el contenido de esta página.",
                                            reviewCandidate = null,
                                        )
                                    }
                            }
                            true
                        }
                    if (!applied || pageDecision == DagAdaptivePageDecision.Blocked) return@launch
                    withContext(Dispatchers.IO) {
                        if (!cachedApproval && pageDecision == DagAdaptivePageDecision.Allowed) {
                            historyStore.savePageApproval(
                                url = url,
                                fingerprint = fingerprint,
                                policyVersion = policyVersion,
                                modelVersion = PageApprovalModelVersion,
                            )
                        }
                        historyStore.addPage(url, title)
                    }
                }
        }

        private fun showProtectedPage(analysis: DagPageAnalysis) {
            if (!isCurrentPageAnalysis(analysis)) return
            mutableState.update {
                it.copy(
                    address = analysis.url,
                    pageAnalysisReady = true,
                    analysisProgress = maxOf(it.analysisProgress, 0.65f),
                    message = "",
                    reviewCandidate = null,
                )
            }
            revealPageIfReady(analysis)
        }

        fun onViewportImagesReady(url: String) {
            val analysis = pageAnalysisTracker.current(url) ?: return
            if (!isCurrentPageAnalysis(analysis)) return
            mutableState.update {
                it.copy(viewportImagesReady = true, analysisProgress = maxOf(it.analysisProgress, 0.95f))
            }
            revealPageIfReady(analysis)
        }

        fun onViewportImageProgress(
            url: String,
            resolved: Int,
            total: Int,
        ) {
            val analysis = pageAnalysisTracker.current(url) ?: return
            if (!isCurrentPageAnalysis(analysis)) return
            val ratio = if (total <= 0) 1f else (resolved.toFloat() / total).coerceIn(0f, 1f)
            val progress = (0.65f + ratio * 0.30f).coerceAtMost(0.95f)
            mutableState.update { it.copy(analysisProgress = maxOf(it.analysisProgress, progress)) }
        }

        private fun revealPageIfReady(analysis: DagPageAnalysis) {
            var revealed = false
            mutableState.update { state ->
                if (
                    dagPageAnalysisMatches(
                        activeUrl = state.requestedUrl,
                        activeAnalysis = pageAnalysisTracker.current(analysis.url),
                        candidate = analysis,
                        dagEnabled = state.dagEnabled,
                    ) &&
                    state.pageAnalysisReady &&
                    state.viewportImagesReady &&
                    state.pageStatus == DagPageStatus.Loading
                ) {
                    revealed = true
                    state.copy(pageStatus = DagPageStatus.Visible, analysisProgress = 1f)
                } else {
                    state
                }
            }
            if (revealed) {
                Log.d(
                    PerformanceLogTag,
                    "page_visible elapsed_ms=${SystemClock.elapsedRealtime() - pageAnalysisStartedAtMillis}",
                )
            }
        }

        fun canNavigateToPage(url: String): Boolean {
            if (!mutableState.value.dagEnabled || !url.startsWith("https://", ignoreCase = true)) return false
            val domain = DagContentClassifier.domainFrom(url)
            val result = applyExplicitRule(domain, classifier.classifyDirectUrl(url))
            if (result.decision != DagClassification.Allowed) {
                if (result.decision == DagClassification.Uncertain) {
                    invalidatePageAnalysis()
                    showUncertainPage(url, domain, result.category)
                } else {
                    onPageBlocked("DAG bloqueó este sitio.")
                }
                return false
            }
            return true
        }

        fun onPageStarted(url: String): Boolean {
            if (!canNavigateToPage(url)) return false
            beginPageAnalysis(url)
            pageAnalysisStartedAtMillis = SystemClock.elapsedRealtime()
            mutableState.update {
                it.copy(
                    address = url,
                    requestedUrl = url,
                    view = DagView.Browser,
                    pageStatus = DagPageStatus.Loading,
                    pageAnalysisReady = false,
                    viewportImagesReady = false,
                    analysisProgress = 0.15f,
                    message = "Analizando la página antes de mostrarla…",
                    reviewCandidate = null,
                )
            }
            return true
        }

        private fun showUncertainPage(
            url: String,
            title: String,
            category: String,
            modelVersion: String = DagContentClassifier.ModelVersion,
        ) {
            val domain = DagContentClassifier.domainFrom(url)
            mutableState.update {
                it.copy(
                    pageStatus = DagPageStatus.Uncertain,
                    pageAnalysisReady = false,
                    viewportImagesReady = false,
                    message = "DAG no mostró la página porque necesita revisión.",
                    reviewCandidate =
                        DagReviewCandidate(
                            url = url,
                            domain = domain,
                            title = title.ifBlank { domain },
                            category = category,
                            modelVersion = modelVersion,
                        ),
                )
            }
        }

        fun requestReview(candidate: DagReviewCandidate) {
            if (!mutableState.value.dagEnabled) return
            val domain = candidate.domain.lowercase(Locale.ROOT).removePrefix("www.").removeSuffix(".")
            if (domain.isBlank()) return
            val alreadyPending = mutableState.value.reviewRequests.any { it.isPendingDagReviewFor(domain) }
            if (alreadyPending || !reviewSubmissionsInFlight.add(domain)) {
                mutableState.update { it.copy(message = "Ya hay una solicitud pendiente para $domain.") }
                return
            }
            viewModelScope.launch {
                try {
                    val activation = withContext(Dispatchers.IO) { activationRepository.currentActivation() }
                    if (activation == null) {
                        mutableState.update { it.copy(message = "No se pudo identificar el dispositivo.") }
                        return@launch
                    }
                    val request =
                        AccessRequest(
                            id = UUID.randomUUID().toString(),
                            requestType = AccessRequestType.DOMAIN_ACCESS,
                            targetType = PolicyTargetType.Domain,
                            target = domain,
                            targetPackageName = null,
                            targetDomain = domain,
                            reason =
                                "DAG · ${candidate.title.take(100)} · ${candidate.category.take(40)} · " +
                                    candidate.modelVersion,
                            requestedMinutes = null,
                            status = RequestStatus.PendingLocal,
                            createdAtEpochMillis = System.currentTimeMillis(),
                            expiresAtEpochMillis = null,
                            deviceId = activation.deviceId,
                        )
                    accessRequestRepository.saveRequest(request)
                    syncScheduler.requestSync()
                    withContext(Dispatchers.IO) { runCatching { syncEngine.syncOnce() } }
                    mutableState.update {
                        it.copy(
                            reviewCandidate = null,
                            message = "Solicitud enviada y pendiente de revisión.",
                        )
                    }
                    waitForApproval(candidate.copy(domain = domain), activation.deviceId)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    mutableState.update { it.copy(message = "No se pudo enviar la solicitud. Intentá nuevamente.") }
                } finally {
                    reviewSubmissionsInFlight.remove(domain)
                }
            }
        }

        private fun waitForApproval(
            candidate: DagReviewCandidate,
            deviceId: String,
        ) {
            approvalPollingJob?.cancel()
            approvalPollingJob =
                viewModelScope.launch {
                    repeat(ApprovalPollingAttempts) {
                        delay(ApprovalPollingIntervalMillis)
                        val approved =
                            withContext(Dispatchers.IO) {
                                runCatching { syncEngine.syncOnce() }
                                val rules = runCatching { policyRepository.getActivePolicy(deviceId).rules }.getOrDefault(activeRules)
                                activeRules = rules
                                rules.hasAllowRule(candidate.domain)
                            }
                        if (approved) {
                            requestNavigation(candidate.url, candidate.title)
                            return@launch
                        }
                    }
                    mutableState.update {
                        it.copy(message = "La solicitud sigue pendiente. Volvé a intentar cuando el administrador la apruebe.")
                    }
                }
        }

        fun showHistory() {
            if (mutableState.value.dagEnabled) {
                cancelActiveSearch()
                invalidatePageAnalysis()
                mutableState.update {
                    it.copy(view = DagView.History, loading = false, message = "", suggestions = emptyList())
                }
            }
        }

        fun showReviewRequests() {
            if (mutableState.value.dagEnabled) {
                cancelActiveSearch()
                invalidatePageAnalysis()
                mutableState.update {
                    it.copy(view = DagView.Reviews, loading = false, message = "", suggestions = emptyList())
                }
            }
        }

        fun openApprovedReview(request: AccessRequest) {
            if (request.status != RequestStatus.Approved || request.requestType != AccessRequestType.DOMAIN_ACCESS) return
            val domain = request.targetDomain ?: request.target
            requestNavigation("https://$domain", domain)
        }

        fun clearPageApprovals() {
            viewModelScope.launch(Dispatchers.IO) { historyStore.clearPageApprovals() }
            mutableState.update { it.copy(message = "Se borraron las decisiones rápidas guardadas.") }
        }

        internal suspend fun loadTabSession(): DagSavedTabSession? =
            withContext(Dispatchers.IO) { historyStore.loadTabSession() }

        internal fun saveTabSession(session: DagSavedTabSession) {
            viewModelScope.launch(Dispatchers.IO) { historyStore.saveTabSession(session) }
        }

        fun clearTabSession() {
            viewModelScope.launch(Dispatchers.IO) { historyStore.clearTabSession() }
        }

        fun showStart() {
            cancelActiveSearch()
            invalidatePageAnalysis()
            mutableState.update(DagBrowserUiState::toDagStart)
        }

        fun backFromBrowser() {
            invalidatePageAnalysis()
            mutableState.update { state ->
                if (state.results.isNotEmpty()) state.toDagResults() else state.toDagStart()
            }
        }

        fun captureTab(): DagTabSnapshot =
            mutableState.value.let {
                DagTabSnapshot(
                    address = it.address,
                    view = it.view,
                    pageStatus = it.pageStatus,
                    results = it.results,
                    searchQuery = it.searchQuery,
                    searchPage = it.searchPage,
                    canLoadMoreResults = it.canLoadMoreResults,
                    requestedUrl = it.requestedUrl,
                    message = it.message,
                    reviewCandidate = it.reviewCandidate,
                )
            }

        fun restoreTab(tab: DagTabSnapshot) {
            cancelActiveSearch()
            invalidatePageAnalysis()
            mutableState.update {
                it.copy(
                    address = tab.address,
                    view = tab.view,
                    pageStatus = if (tab.view == DagView.Browser) DagPageStatus.Loading else tab.pageStatus,
                    pageAnalysisReady = false,
                    viewportImagesReady = false,
                    results = tab.results,
                    searchQuery = tab.searchQuery,
                    searchPage = tab.searchPage,
                    canLoadMoreResults = tab.canLoadMoreResults,
                    requestedUrl = tab.requestedUrl,
                    navigationRevision = it.navigationRevision + 1,
                    loading = false,
                    suggestions = emptyList(),
                    message = tab.message,
                    reviewCandidate = tab.reviewCandidate,
                )
            }
        }

        fun openNewTab() {
            cancelActiveSearch()
            invalidatePageAnalysis()
            mutableState.update {
                it.copy(
                    address = "",
                    view = DagView.Start,
                    pageStatus = DagPageStatus.Idle,
                    pageAnalysisReady = false,
                    viewportImagesReady = false,
                    results = emptyList(),
                    searchQuery = "",
                    searchPage = 0,
                    canLoadMoreResults = false,
                    requestedUrl = null,
                    navigationRevision = it.navigationRevision + 1,
                    loading = false,
                    suggestions = emptyList(),
                    message = "",
                    reviewCandidate = null,
                )
            }
        }

        fun openHistory(entry: DagHistoryEntry) {
            when (entry.type) {
                DagHistoryType.Search -> {
                    onAddressChanged(entry.value)
                    search(entry.value)
                }
                DagHistoryType.Page -> entry.url?.let { requestNavigation(it, entry.title.orEmpty()) }
            }
        }

        fun deleteHistory(id: String) {
            viewModelScope.launch(Dispatchers.IO) { historyStore.delete(id) }
        }

        fun clearHistory() {
            viewModelScope.launch(Dispatchers.IO) { historyStore.clear() }
        }

        fun onBrowserBlockedAction(message: String) {
            mutableState.update { it.copy(message = message) }
        }

        fun onPageBlocked(message: String) {
            invalidatePageAnalysis()
            mutableState.update {
                it.copy(
                    pageStatus = DagPageStatus.Blocked,
                    message = message,
                    reviewCandidate = null,
                )
            }
        }

        fun onBrowserRendererGone() {
            cancelActiveSearch()
            invalidatePageAnalysis()
            mutableState.update {
                it.copy(
                    address = "",
                    view = DagView.Start,
                    pageStatus = DagPageStatus.Idle,
                    requestedUrl = null,
                    navigationRevision = it.navigationRevision + 1,
                    loading = false,
                    message = "La pestaña se cerró para proteger la estabilidad. Podés volver a intentarlo.",
                    reviewCandidate = null,
                )
            }
        }

        private fun cancelActiveSearch() {
            searchRequestTracker.cancel()
            queryClassificationJob?.cancel()
            queryClassificationJob = null
            searchJob?.cancel()
            searchJob = null
        }

        private fun beginPageAnalysis(url: String): DagPageAnalysis {
            pageClassificationJob?.cancel()
            pageClassificationJob = null
            return pageAnalysisTracker.begin(url)
        }

        private fun invalidatePageAnalysis() {
            pageClassificationJob?.cancel()
            pageClassificationJob = null
            pageAnalysisTracker.cancel()
        }

        private fun isCurrentPageAnalysis(analysis: DagPageAnalysis): Boolean {
            val state = mutableState.value
            return dagPageAnalysisMatches(
                activeUrl = state.requestedUrl,
                activeAnalysis = pageAnalysisTracker.current(analysis.url),
                candidate = analysis,
                dagEnabled = state.dagEnabled,
            )
        }

        private fun applyExplicitRule(
            domain: String,
            result: DagClassificationResult,
        ): DagClassificationResult {
            val rule =
                activeRules
                    .asSequence()
                    .filter { it.enabled && it.scope == RuleScope.Domain && !it.target.startsWith("__") }
                    .filter { domain.matchesDomain(it.target) }
                    .sortedByDescending { it.priority }
                    .firstOrNull()
            return when (rule?.action) {
                RuleAction.Block ->
                    result.copy(
                        decision = DagClassification.Blocked,
                        category = "admin_block",
                        confidence = 1f,
                    )
                RuleAction.Allow ->
                    if (result.category !in DagContentClassifier.NonOverridableCategories) {
                        result.copy(decision = DagClassification.Allowed, category = "admin_allow", confidence = 1f)
                    } else {
                        result
                    }
                else -> result
            }
        }

        private fun String.matchesDomain(target: String): Boolean {
            val normalized = target.lowercase(Locale.ROOT).removePrefix("www.").removeSuffix(".")
            return normalized == "*" || this == normalized || endsWith(".$normalized")
        }

        private fun String.toHttpsUrlOrNull(): String? {
            val trimmed = trim()
            val candidate =
                when {
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                    trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substringAfter("://")}"
                    ' ' !in trimmed && '.' in trimmed -> "https://$trimmed"
                    else -> return null
                }
            return runCatching {
                val uri = URI(candidate)
                candidate.takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() }
            }.getOrNull()
        }

        private fun String.dagLanguage(): String {
            val hasHebrew = any { it.code in 0x0590..0x05FF }
            if (hasHebrew) return "he"
            val englishMarkers = setOf(" the ", " how ", " what ", " where ", " near ", " guide ")
            val padded = " ${lowercase(Locale.ROOT)} "
            return if (englishMarkers.any(padded::contains)) "en" else "es"
        }

        private companion object {
            const val MaxAddressCharacters = 400
            const val ApprovalPollingIntervalMillis = 5_000L
            const val ApprovalPollingAttempts = 24
            const val SuggestionDebounceMillis = 120L
            const val DiagnosticsLogTag = "DagSearchDiagnostics"
            const val PerformanceLogTag = "DagPerformance"
            const val PageApprovalModelVersion =
                "dag-page-approval-3:${DagContentClassifier.ModelVersion}:" +
                    DagProfessionalImageClassifier.ModelVersion
        }
    }

internal data class DagPageAnalysis(
    val url: String,
    val revision: Long,
)

internal class DagPageAnalysisTracker {
    private var revision = 0L
    private var activeAnalysis: DagPageAnalysis? = null

    fun begin(url: String): DagPageAnalysis =
        DagPageAnalysis(url = url, revision = ++revision)
            .also { activeAnalysis = it }

    fun current(url: String): DagPageAnalysis? = activeAnalysis?.takeIf { it.url == url }

    fun cancel() {
        revision += 1
        activeAnalysis = null
    }
}

internal fun dagPageAnalysisMatches(
    activeUrl: String?,
    activeAnalysis: DagPageAnalysis?,
    candidate: DagPageAnalysis,
    dagEnabled: Boolean,
): Boolean =
    dagEnabled &&
        activeUrl == candidate.url &&
        activeAnalysis == candidate

internal const val DAG_UNREADABLE_PAGE_CATEGORY = "unreadable_page"

internal fun dagCanAnalyzePageText(
    state: DagBrowserUiState,
    url: String,
    hasMeaningfulText: Boolean,
): Boolean {
    if (state.requestedUrl != url) return false
    return when {
        !hasMeaningfulText -> state.pageStatus == DagPageStatus.Loading
        state.pageStatus == DagPageStatus.Loading || state.pageStatus == DagPageStatus.Visible -> true
        else -> state.isRecoverableUnreadablePage(url)
    }
}

internal fun DagBrowserUiState.isRecoverableUnreadablePage(url: String): Boolean =
    requestedUrl == url &&
        dagIsRecoverableUnreadablePage(
            pageStatus = pageStatus,
            reviewCandidate = reviewCandidate,
            url = url,
        )

internal fun dagIsRecoverableUnreadablePage(
    pageStatus: DagPageStatus,
    reviewCandidate: DagReviewCandidate?,
    url: String,
): Boolean =
    pageStatus == DagPageStatus.Uncertain &&
        reviewCandidate?.url == url &&
        reviewCandidate.category == DAG_UNREADABLE_PAGE_CATEGORY

internal fun DagBrowserUiState.beginDagPageTextAnalysis(
    url: String,
    hasMeaningfulText: Boolean,
): DagBrowserUiState =
    when {
        hasMeaningfulText && isRecoverableUnreadablePage(url) ->
            copy(
                pageStatus = DagPageStatus.Loading,
                pageAnalysisReady = false,
                viewportImagesReady = false,
                analysisProgress = maxOf(analysisProgress, 0.15f),
                message = "Analizando la página antes de mostrarla…",
                reviewCandidate = null,
            )
        pageStatus == DagPageStatus.Loading -> copy(pageAnalysisReady = false)
        else -> this
    }

internal fun dagCanLoadMoreResults(
    page: Int,
    providerHasMoreResults: Boolean,
): Boolean = page == 0 && providerHasMoreResults

internal enum class DagQueryAction {
    Block,
    Search,
}

internal fun dagQueryAction(decision: DagClassification): DagQueryAction =
    if (decision == DagClassification.Blocked) DagQueryAction.Block else DagQueryAction.Search

internal fun AccessRequest.isPendingDagReviewFor(domain: String): Boolean {
    val requestDomain = (targetDomain ?: target).lowercase(Locale.ROOT).removePrefix("www.").removeSuffix(".")
    return requestType == AccessRequestType.DOMAIN_ACCESS &&
        requestDomain == domain &&
        (status == RequestStatus.PendingLocal || status == RequestStatus.PendingRemote)
}

private fun List<PolicyRule>.hasAllowRule(domain: String): Boolean =
    asSequence()
        .filter { it.enabled && it.scope == RuleScope.Domain && it.action == RuleAction.Allow }
        .map { it.target.lowercase(Locale.ROOT).removePrefix("www.").removeSuffix(".") }
        .any { target -> target == "*" || domain == target || domain.endsWith(".$target") }

internal fun DagBrowserUiState.withDagAvailability(enabled: Boolean): DagBrowserUiState =
    when {
        !enabled ->
            copy(
                dagAvailabilityKnown = true,
                dagEnabled = false,
                dagExtraKosherEnabled = false,
                address = "",
                view = DagView.Start,
                pageStatus = DagPageStatus.Idle,
                pageAnalysisReady = false,
                viewportImagesReady = false,
                results = emptyList(),
                searchQuery = "",
                searchPage = 0,
                canLoadMoreResults = false,
                suggestions = emptyList(),
                requestedUrl = null,
                loading = false,
                message = "El administrador mantiene DAG cerrado.",
                reviewCandidate = null,
            )
        dagEnabled -> copy(dagAvailabilityKnown = true)
        else ->
            copy(
                dagAvailabilityKnown = true,
                dagEnabled = true,
                address = "",
                view = DagView.Start,
                pageStatus = DagPageStatus.Idle,
                pageAnalysisReady = false,
                viewportImagesReady = false,
                results = emptyList(),
                searchQuery = "",
                searchPage = 0,
                canLoadMoreResults = false,
                suggestions = emptyList(),
                requestedUrl = null,
                loading = false,
                message = "",
                reviewCandidate = null,
            )
    }

internal fun DagBrowserUiState.toDagStart(): DagBrowserUiState =
    copy(
        address = "",
        view = DagView.Start,
        pageStatus = DagPageStatus.Idle,
        pageAnalysisReady = false,
        viewportImagesReady = false,
        results = emptyList(),
        searchQuery = "",
        searchPage = 0,
        canLoadMoreResults = false,
        suggestions = emptyList(),
        requestedUrl = null,
        navigationRevision = navigationRevision + 1,
        loading = false,
        message = "",
        reviewCandidate = null,
    )

internal fun DagBrowserUiState.toDagResults(): DagBrowserUiState =
    copy(
        address = "",
        view = DagView.Results,
        pageStatus = DagPageStatus.Idle,
        pageAnalysisReady = false,
        viewportImagesReady = false,
        suggestions = emptyList(),
        requestedUrl = null,
        navigationRevision = navigationRevision + 1,
        loading = false,
        message = "",
        reviewCandidate = null,
    )
