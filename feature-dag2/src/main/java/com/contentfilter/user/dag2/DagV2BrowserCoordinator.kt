package com.contentfilter.user.dag2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

interface DagV2SearchPolicy {
    fun evaluateQuery(query: String): DagV2PolicyResult

    suspend fun evaluateResult(result: DagV2SearchResult): DagV2PolicyResult
}

class DagV2SearchOrchestrator
    @Inject
    constructor(
        private val policy: DagV2SearchPolicy,
        private val gateway: DagV2SearchGateway,
    ) {
        suspend fun search(query: String): DagV2SearchOutcome {
            val local = policy.evaluateQuery(query)
            if (local.decision == DagV2SiteDecision.Block) {
                return DagV2SearchOutcome.Failure(local.reason)
            }
            return when (val remote = gateway.search(query)) {
                is DagV2SearchOutcome.Failure -> remote
                is DagV2SearchOutcome.Success ->
                    DagV2SearchOutcome.Success(
                        remote.results.filter { result ->
                            policy.evaluateResult(result).decision == DagV2SiteDecision.Allow
                        },
                    )
            }
        }
    }

data class DagV2BrowserState(
    val input: String = "",
    val searching: Boolean = false,
    val results: List<DagV2SearchResult> = emptyList(),
    val requestedUrl: String? = null,
    val navigationRevision: Long = 0,
    val documentAnalyzing: Boolean = false,
    val documentVisible: Boolean = false,
    val fullPageAnalysisCount: Int = 0,
    val statusMessage: String = "Laboratorio DEV: todas las imágenes se mantienen neutras.",
    val blockedMessage: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val rendererGone: Boolean = false,
    val noCacheMode: Boolean = false,
)

@Singleton
class DagV2BrowserCoordinator
    @Inject
    constructor(
        private val searchOrchestrator: DagV2SearchOrchestrator,
        private val sitePolicy: DagV2SitePolicy,
        private val networkGuard: DagV2NetworkGuard,
        private val sessions: DagV2DocumentSession,
        private val resourceRouter: DagV2ResourceRouter,
        private val metrics: DagV2Metrics,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var commandJob: Job? = null
        private val mutableState = MutableStateFlow(DagV2BrowserState())
        val state: StateFlow<DagV2BrowserState> = mutableState.asStateFlow()

        fun updateInput(value: String) {
            mutableState.value = mutableState.value.copy(input = value)
        }

        fun submit() {
            val value = mutableState.value.input.trim()
            if (value.isHttpsDagV2Url()) {
                navigate(value)
            } else {
                search(value)
            }
        }

        fun openResult(result: DagV2SearchResult) {
            updateInput(result.url)
            navigate(result.url)
        }

        fun setNoCacheMode(enabled: Boolean) {
            mutableState.value =
                mutableState.value.copy(
                    noCacheMode = enabled,
                    statusMessage =
                        if (enabled) {
                            "Modo DEV sin caché activo."
                        } else {
                            "Caché normal de WebView activa."
                        },
                )
            if (enabled) metrics.event(DagV2MetricNames.NoCacheModeEnabled)
        }

        fun navigate(url: String) {
            commandJob?.cancel()
            commandJob =
                scope.launch {
                    mutableState.value =
                        mutableState.value.copy(
                            searching = false,
                            blockedMessage = null,
                            statusMessage = "Validando navegación segura…",
                        )
                    val networkDecision = networkGuard.validate(url)
                    if (networkDecision.decision == DagV2SiteDecision.Block) {
                        block(networkDecision.reason)
                        return@launch
                    }
                    val siteDecision = sitePolicy.evaluateNavigation(url)
                    if (siteDecision.decision == DagV2SiteDecision.Block) {
                        block(siteDecision.reason)
                        return@launch
                    }
                    cancelActiveSession()
                    val session = sessions.start(url)
                    resourceRouter.onNewDocument(session)
                    mutableState.value =
                        mutableState.value.copy(
                            requestedUrl = url,
                            results = emptyList(),
                            navigationRevision = mutableState.value.navigationRevision + 1,
                            documentAnalyzing = true,
                            documentVisible = false,
                            fullPageAnalysisCount = 0,
                            statusMessage = "Analizando el documento principal una sola vez…",
                            blockedMessage = null,
                            rendererGone = false,
                        )
                }
        }

        fun onDocumentStarted(url: String) {
            val session = sessions.snapshot() ?: return
            if (session.mainDocumentUrl != url || !sessions.isCurrent(session.sessionId, session.navigationToken)) {
                metrics.staleResultDiscarded()
                return
            }
            metrics.event(DagV2MetricNames.DocumentStarted, session)
        }

        fun isExpectedDocument(url: String): Boolean =
            sessions.snapshot()?.let {
                it.mainDocumentUrl == url && sessions.isCurrent(it.sessionId, it.navigationToken)
            } == true

        fun isHashOnlyNavigation(url: String): Boolean {
            val currentUrl = sessions.snapshot()?.mainDocumentUrl ?: return false
            if (currentUrl == url) return false
            return currentUrl.withoutDagV2Fragment() == url.withoutDagV2Fragment()
        }

        fun onDocumentCommitted(url: String): DagV2DocumentSessionState? {
            val session = sessions.snapshot() ?: return null
            if (session.mainDocumentUrl != url || !sessions.isCurrent(session.sessionId, session.navigationToken)) {
                metrics.staleResultDiscarded()
                return null
            }
            metrics.event(DagV2MetricNames.DocumentCommitted, session)
            val analyzing = sessions.beginFullAnalysis(session.sessionId, session.navigationToken) ?: return null
            metrics.event(DagV2MetricNames.FullPageAnalysisStarted, analyzing)
            metrics.event(DagV2MetricNames.FullPageAnalysisCount, analyzing, analyzing.fullPageAnalysisCount)
            mutableState.value =
                mutableState.value.copy(
                    documentAnalyzing = true,
                    fullPageAnalysisCount = analyzing.fullPageAnalysisCount,
                )
            return analyzing
        }

        fun onDocumentAnalysis(
            sessionId: String,
            navigationToken: String,
            url: String,
            title: String,
            visibleText: String,
        ) {
            if (!sessions.isCurrent(sessionId, navigationToken)) {
                metrics.staleResultDiscarded()
                return
            }
            val decision = sitePolicy.evaluateDocument(url, title, visibleText)
            val completed = sessions.completeFullAnalysis(sessionId, navigationToken) ?: return
            metrics.event(DagV2MetricNames.FullPageAnalysisCompleted, completed)
            if (decision.decision == DagV2SiteDecision.Block) {
                block(decision.reason)
                return
            }
            metrics.event(DagV2MetricNames.StructureVisible, completed)
            mutableState.value =
                mutableState.value.copy(
                    documentAnalyzing = false,
                    documentVisible = true,
                    fullPageAnalysisCount = completed.fullPageAnalysisCount,
                    statusMessage = "Estructura permitida; imágenes reemplazadas por placeholders.",
                    blockedMessage = null,
                )
            scheduleStableMetric(completed)
        }

        fun onDocumentAnalysisFailed(
            sessionId: String,
            navigationToken: String,
        ) {
            if (!sessions.isCurrent(sessionId, navigationToken)) {
                metrics.staleResultDiscarded()
                return
            }
            block("No se pudo aprobar el documento con suficiente certeza.")
        }

        fun onCurrentDocumentFailure(message: String) {
            if (sessions.snapshot() == null) return
            block(message)
        }

        fun onSpaUrlChanged(url: String) {
            val session = sessions.snapshot() ?: return
            if (!sessions.isCurrent(session.sessionId, session.navigationToken)) return
            val decision = sitePolicy.evaluateSpaRoute(url)
            if (decision.decision == DagV2SiteDecision.Block) {
                block(decision.reason)
            } else {
                mutableState.value =
                    mutableState.value.copy(
                        input = url,
                        statusMessage = "Ruta SPA comprobada sin repetir el análisis completo.",
                    )
            }
        }

        fun onInternalInteraction(interaction: DagV2InternalInteraction) {
            sessions.recordInternalInteraction(interaction)
        }

        fun onNavigationState(
            canGoBack: Boolean,
            canGoForward: Boolean,
        ) {
            mutableState.value = mutableState.value.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }

        fun onRendererGone() {
            val session = sessions.snapshot()
            metrics.event(DagV2MetricNames.RendererGone, session)
            cancelActiveSession()
            mutableState.value =
                mutableState.value.copy(
                    rendererGone = true,
                    documentVisible = false,
                    documentAnalyzing = false,
                    blockedMessage = "El renderer de WebView terminó. DAG v1 y el proceso principal siguen aislados.",
                )
        }

        fun onSecurityFailure(message: String) {
            block(message)
        }

        private fun search(query: String) {
            commandJob?.cancel()
            commandJob =
                scope.launch {
                    cancelActiveSession()
                    mutableState.value =
                        mutableState.value.copy(
                            searching = true,
                            results = emptyList(),
                            requestedUrl = null,
                            documentVisible = false,
                            blockedMessage = null,
                            statusMessage = "Comprobando la consulta localmente…",
                        )
                    when (val outcome = searchOrchestrator.search(query)) {
                        is DagV2SearchOutcome.Failure -> block(outcome.message)
                        is DagV2SearchOutcome.Success ->
                            mutableState.value =
                                mutableState.value.copy(
                                    searching = false,
                                    results = outcome.results,
                                    statusMessage =
                                        if (outcome.results.isEmpty()) {
                                            "No quedaron resultados seguros para mostrar."
                                        } else {
                                            "${outcome.results.size} resultados seguros."
                                        },
                                )
                    }
                }
        }

        fun closeSession() {
            commandJob?.cancel()
            commandJob = null
            cancelActiveSession()
        }

        private fun block(message: String) {
            cancelActiveSession()
            mutableState.value =
                mutableState.value.copy(
                    searching = false,
                    requestedUrl = null,
                    documentAnalyzing = false,
                    documentVisible = false,
                    blockedMessage = message,
                    statusMessage = "Página o búsqueda bloqueada.",
                )
        }

        private fun scheduleStableMetric(session: DagV2DocumentSessionState) {
            scope.launch {
                delay(StableWindowMillis)
                if (
                    sessions.isCurrent(session.sessionId, session.navigationToken) &&
                    mutableState.value.documentVisible &&
                    !mutableState.value.rendererGone
                ) {
                    metrics.event(DagV2MetricNames.FunctionalStable20s, session)
                }
            }
        }

        private fun cancelActiveSession() {
            sessions.cancelActive()?.let(metrics::sessionCancelled)
            resourceRouter.cancelVisualRequests()
        }

        private companion object {
            const val StableWindowMillis = 20_000L
        }
    }

private fun String.withoutDagV2Fragment(): String? =
    runCatching {
        val uri = URI(this)
        URI(uri.scheme, uri.authority, uri.path, uri.query, null).toString()
    }.getOrNull()
