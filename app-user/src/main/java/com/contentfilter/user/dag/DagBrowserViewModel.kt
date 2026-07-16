package com.contentfilter.user.dag

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
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val classifier: DagContentClassifier,
        private val historyStore: DagHistoryStore,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DagBrowserUiState())
        val uiState: StateFlow<DagBrowserUiState> = mutableState.asStateFlow()

        @Volatile
        private var activeRules: List<PolicyRule> = emptyList()

        @Volatile
        private var activePolicyVersion: Long = 0L
        private var approvalPollingJob: Job? = null
        private var suggestionJob: Job? = null

        init {
            viewModelScope.launch {
                syncScheduler.requestSync()
                withContext(Dispatchers.IO) { runCatching { syncEngine.syncOnce() } }
            }
            viewModelScope.launch {
                combine(
                    policyRepository.observeActivePolicy(),
                    activationRepository.observeActivation(),
                ) { snapshot, activation -> snapshot to activation }
                    .collect { (snapshot, activation) ->
                        activeRules = snapshot.rules
                        activePolicyVersion = snapshot.version
                        val enabled = activation != null && snapshot.rules.dagEnabled()
                        mutableState.update { state -> state.withDagAvailability(enabled) }
                    }
            }
            viewModelScope.launch {
                historyStore.observe().collect { entries ->
                    mutableState.update { it.copy(history = entries) }
                }
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
            if (!mutableState.value.dagEnabled || query.isBlank()) return
            val classification = classifier.classifyQuery(query)
            when (classification.decision) {
                DagClassification.Blocked -> {
                    mutableState.update {
                        it.copy(
                            view = DagView.Start,
                            message = "La búsqueda fue bloqueada por la protección DAG.",
                            results = emptyList(),
                            suggestions = emptyList(),
                            reviewCandidate = null,
                        )
                    }
                }
                DagClassification.Uncertain -> {
                    mutableState.update {
                        it.copy(
                            view = DagView.Start,
                            message = "DAG no tiene suficiente certeza. Reformulá la búsqueda con más contexto.",
                            results = emptyList(),
                            suggestions = emptyList(),
                            reviewCandidate = null,
                        )
                    }
                }
                DagClassification.Allowed -> performSearch(query)
            }
        }

        private fun performSearch(query: String) {
            viewModelScope.launch {
                mutableState.update {
                    it.copy(
                        loading = true,
                        message = "",
                        reviewCandidate = null,
                        pageStatus = DagPageStatus.Idle,
                        suggestions = emptyList(),
                    )
                }
                val activation = withContext(Dispatchers.IO) { activationRepository.currentActivation() }
                if (activation == null) {
                    mutableState.update {
                        it.copy(
                            loading = false,
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
                        )
                ) {
                    is RemoteResult.Failure -> {
                        mutableState.update { it.copy(loading = false, message = response.reason) }
                    }
                    is RemoteResult.Success -> {
                        val classifiedWithReasons =
                            withContext(Dispatchers.Default) {
                                response.value.results.mapNotNull { remote ->
                                    val domain = DagContentClassifier.domainFrom(remote.url)
                                    if (domain.isBlank()) return@mapNotNull null
                                    val local =
                                        classifier.classifyResultWithReason(
                                            remote.title,
                                            remote.description,
                                            remote.url,
                                        )
                                    val decision = applyExplicitRule(domain = domain, result = local.classification)
                                    val reason =
                                        if (
                                            decision.decision == DagClassification.Blocked &&
                                            decision.category == "admin_block"
                                        ) {
                                            DagSearchDecisionReason.AdminRuleBlock
                                        } else {
                                            local.reason
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
                        withContext(Dispatchers.IO) { historyStore.addSearch(query) }
                        mutableState.update {
                            it.copy(
                                address = query,
                                loading = false,
                                view = DagView.Results,
                                results = classified,
                                suggestions = emptyList(),
                                message = if (classified.isEmpty()) "DAG no encontró resultados que pueda mostrar." else "",
                            )
                        }
                    }
                }
            }
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
            val domain = DagContentClassifier.domainFrom(url)
            val classification = applyExplicitRule(domain, classifier.classifyDirectUrl(url))
            when (classification.decision) {
                DagClassification.Blocked ->
                    mutableState.update {
                        it.copy(
                            pageStatus = DagPageStatus.Blocked,
                            message = "DAG bloqueó este sitio.",
                            reviewCandidate = null,
                            suggestions = emptyList(),
                        )
                    }
                DagClassification.Uncertain ->
                    mutableState.update {
                        it.copy(
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
                        it.copy(
                            address = url,
                            view = DagView.Browser,
                            pageStatus = DagPageStatus.Loading,
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
            if (!mutableState.value.dagEnabled || url != mutableState.value.requestedUrl) return
            if (text == null || text.isBlank()) {
                showUncertainPage(url, title, "unreadable")
                return
            }
            viewModelScope.launch(Dispatchers.Default) {
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
                        applyExplicitRule(domain, classifier.classifyPage(url, title, text, images))
                    }
                if (result.decision == DagClassification.Allowed) {
                    withContext(Dispatchers.IO) {
                        if (!cachedApproval) {
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
                withContext(Dispatchers.Main) {
                    if (url != mutableState.value.requestedUrl || !mutableState.value.dagEnabled) return@withContext
                    when (result.decision) {
                        DagClassification.Allowed -> {
                            mutableState.update {
                                it.copy(
                                    address = url,
                                    pageStatus = DagPageStatus.Visible,
                                    message = "",
                                    reviewCandidate = null,
                                )
                            }
                        }
                        DagClassification.Blocked ->
                            mutableState.update {
                                it.copy(
                                    pageStatus = DagPageStatus.Blocked,
                                    message = "DAG bloqueó el contenido de esta página.",
                                    reviewCandidate = null,
                                )
                            }
                        DagClassification.Uncertain -> showUncertainPage(url, title, result.category)
                    }
                }
            }
        }

        fun onPageStarted(url: String): Boolean {
            if (!mutableState.value.dagEnabled || !url.startsWith("https://", ignoreCase = true)) return false
            val domain = DagContentClassifier.domainFrom(url)
            val result = applyExplicitRule(domain, classifier.classifyDirectUrl(url))
            if (result.decision != DagClassification.Allowed) {
                if (result.decision == DagClassification.Uncertain) {
                    showUncertainPage(url, domain, result.category)
                } else {
                    onPageBlocked("DAG bloqueó este sitio.")
                }
                return false
            }
            mutableState.update {
                it.copy(
                    address = url,
                    requestedUrl = url,
                    view = DagView.Browser,
                    pageStatus = DagPageStatus.Loading,
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
        ) {
            val domain = DagContentClassifier.domainFrom(url)
            mutableState.update {
                it.copy(
                    pageStatus = DagPageStatus.Uncertain,
                    message = "DAG no mostró la página porque necesita revisión.",
                    reviewCandidate =
                        DagReviewCandidate(
                            url = url,
                            domain = domain,
                            title = title.ifBlank { domain },
                            category = category,
                            modelVersion = DagContentClassifier.ModelVersion,
                        ),
                )
            }
        }

        fun requestReview(candidate: DagReviewCandidate) {
            if (!mutableState.value.dagEnabled) return
            viewModelScope.launch {
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
                        target = candidate.domain,
                        targetPackageName = null,
                        targetDomain = candidate.domain,
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
                        message = "Solicitud enviada. DAG abrirá el sitio cuando el administrador lo apruebe.",
                    )
                }
                waitForApproval(candidate, activation.deviceId)
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
                mutableState.update { it.copy(view = DagView.History, message = "", suggestions = emptyList()) }
            }
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
            mutableState.update(DagBrowserUiState::toDagStart)
        }

        fun backFromBrowser() {
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
                    requestedUrl = it.requestedUrl,
                    message = it.message,
                    reviewCandidate = it.reviewCandidate,
                )
            }

        fun restoreTab(tab: DagTabSnapshot) {
            mutableState.update {
                it.copy(
                    address = tab.address,
                    view = tab.view,
                    pageStatus = if (tab.view == DagView.Browser) DagPageStatus.Loading else tab.pageStatus,
                    results = tab.results,
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
            mutableState.update {
                it.copy(
                    address = "",
                    view = DagView.Start,
                    pageStatus = DagPageStatus.Idle,
                    results = emptyList(),
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
            mutableState.update {
                it.copy(
                    pageStatus = DagPageStatus.Blocked,
                    message = message,
                    reviewCandidate = null,
                )
            }
        }

        fun onBrowserRendererGone() {
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
            const val PageApprovalModelVersion =
                "dag-page-approval-1:${DagContentClassifier.ModelVersion}:${DagNeuralTextClassifier.ModelVersion}:" +
                    DagProfessionalImageClassifier.ModelVersion
        }
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
                address = "",
                view = DagView.Start,
                pageStatus = DagPageStatus.Idle,
                results = emptyList(),
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
                results = emptyList(),
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
        results = emptyList(),
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
        suggestions = emptyList(),
        requestedUrl = null,
        navigationRevision = navigationRevision + 1,
        loading = false,
        message = "",
        reviewCandidate = null,
    )
