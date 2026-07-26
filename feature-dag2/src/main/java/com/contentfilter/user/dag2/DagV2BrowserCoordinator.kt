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

internal data class DagV2HistoryTarget(
    val index: Int,
    val url: String,
)

internal class DagV2NavigationHistory {
    private val entries = mutableListOf<String>()
    private var index = -1

    fun push(url: String) {
        if (index >= 0 && entries[index] == url) return
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        entries += url
        index = entries.lastIndex
    }

    fun backTarget(): DagV2HistoryTarget? =
        (index - 1)
            .takeIf { it >= 0 }
            ?.let { DagV2HistoryTarget(it, entries[it]) }

    fun forwardTarget(): DagV2HistoryTarget? =
        (index + 1)
            .takeIf { it <= entries.lastIndex }
            ?.let { DagV2HistoryTarget(it, entries[it]) }

    fun currentTarget(): DagV2HistoryTarget? =
        index
            .takeIf { it in entries.indices }
            ?.let { DagV2HistoryTarget(it, entries[it]) }

    fun commit(target: DagV2HistoryTarget) {
        require(entries.getOrNull(target.index) == target.url)
        index = target.index
    }

    fun replaceCurrent(url: String) {
        if (index in entries.indices) entries[index] = url
    }

    fun canGoBack(): Boolean = index > 0

    fun canGoForward(): Boolean = index >= 0 && index < entries.lastIndex

    fun clear() {
        entries.clear()
        index = -1
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
    val requestContext: DagV2DocumentRequestContext? = null,
)

@Singleton
class DagV2BrowserCoordinator
    @Inject
    constructor(
        private val searchOrchestrator: DagV2SearchOrchestrator,
        private val sitePolicy: DagV2SitePolicy,
        private val networkGuard: DagV2NetworkGuard,
        private val sessions: DagV2DocumentSession,
        private val callbackGate: DagV2DocumentCallbackGate,
        private val resourceRouter: DagV2ResourceRouter,
        private val metrics: DagV2Metrics,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val navigationHistory = DagV2NavigationHistory()
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

        fun goBack() {
            navigationHistory.backTarget()?.let { navigate(it.url, it) }
        }

        fun goForward() {
            navigationHistory.forwardTarget()?.let { navigate(it.url, it) }
        }

        fun refresh() {
            navigationHistory.currentTarget()?.let { navigate(it.url, it) }
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
            navigate(url, historyTarget = null)
        }

        private fun navigate(
            url: String,
            historyTarget: DagV2HistoryTarget?,
        ) {
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
                    if (historyTarget == null) {
                        navigationHistory.push(url)
                    } else {
                        navigationHistory.commit(historyTarget)
                    }
                    val session = sessions.start(url)
                    callbackGate.register(session.requestContext)
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
                            requestContext = session.requestContext,
                            canGoBack = navigationHistory.canGoBack(),
                            canGoForward = navigationHistory.canGoForward(),
                        )
                }
        }

        fun onDocumentStarted(context: DagV2DocumentRequestContext) {
            val session = sessions.snapshot() ?: return
            if (!accepts(context) || session.mainDocumentUrl != context.documentUrl) return
            metrics.event(DagV2MetricNames.DocumentStarted, session)
        }

        fun isHashOnlyNavigation(
            context: DagV2DocumentRequestContext,
            url: String,
        ): Boolean {
            if (!accepts(context) || context.documentUrl == url) return false
            return context.documentUrl.withoutDagV2Fragment() == url.withoutDagV2Fragment()
        }

        /**
         * Returns true only when this location still belongs to the WebView's
         * immutable document generation. Redirects are stopped and re-enter the
         * normal async navigation guards as a new generation.
         */
        fun onMainFrameStarted(
            context: DagV2DocumentRequestContext,
            url: String,
        ): Boolean {
            if (!accepts(context)) return false
            if (!url.isHttpsDagV2Url()) {
                block("DAG v2 bloqueó una navegación no HTTPS.")
                return false
            }
            if (
                context.documentUrl == url ||
                context.documentUrl.withoutDagV2Fragment() == url.withoutDagV2Fragment()
            ) {
                return true
            }
            navigate(url)
            return false
        }

        fun onDocumentCommitted(context: DagV2DocumentRequestContext): DagV2DocumentSessionState? {
            val session = sessions.snapshot() ?: return null
            if (!accepts(context) || session.mainDocumentUrl != context.documentUrl) return null
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
            context: DagV2DocumentRequestContext,
            url: String,
            title: String,
            visibleText: String,
        ) {
            if (!accepts(context)) return
            val decision = sitePolicy.evaluateDocument(url, title, visibleText)
            if (!accepts(context)) return
            val completed =
                sessions.completeFullAnalysis(context.sessionId, context.navigationToken)
                    ?: return
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

        fun onDocumentAnalysisFailed(context: DagV2DocumentRequestContext) {
            if (!accepts(context)) return
            block("No se pudo aprobar el documento con suficiente certeza.")
        }

        fun onSpaUrlChanged(
            context: DagV2DocumentRequestContext,
            url: String,
        ) {
            if (!accepts(context) || !callbackGate.registerSpaLocation(context, url)) return
            val session = sessions.snapshot() ?: return
            if (!sessions.isCurrent(session.sessionId, session.navigationToken)) return
            val decision = sitePolicy.evaluateSpaRoute(url)
            if (decision.decision == DagV2SiteDecision.Block) {
                block(decision.reason)
            } else {
                navigationHistory.replaceCurrent(url)
                mutableState.value =
                    mutableState.value.copy(
                        input = url,
                        statusMessage = "Ruta SPA comprobada sin repetir el análisis completo.",
                    )
            }
        }

        fun onWebViewHistoryChanged(
            context: DagV2DocumentRequestContext,
            canGoBack: Boolean,
            canGoForward: Boolean,
        ) {
            if (!accepts(context)) return
            mutableState.value =
                mutableState.value.copy(
                    canGoBack = canGoBack || navigationHistory.canGoBack(),
                    canGoForward = canGoForward || navigationHistory.canGoForward(),
                )
        }

        fun onInternalInteraction(
            context: DagV2DocumentRequestContext,
            interaction: DagV2InternalInteraction,
        ) {
            if (!accepts(context)) return
            sessions.recordInternalInteraction(interaction)
        }

        fun onRendererGone(context: DagV2DocumentRequestContext) {
            if (!accepts(context)) return
            val session = sessions.snapshot()
            metrics.event(DagV2MetricNames.RendererGone, session)
            cancelActiveSession()
            mutableState.value =
                mutableState.value.copy(
                    requestedUrl = null,
                    requestContext = null,
                    rendererGone = true,
                    documentVisible = false,
                    documentAnalyzing = false,
                    blockedMessage = "El renderer de WebView terminó. DAG v1 y el proceso principal siguen aislados.",
                )
        }

        fun onSecurityFailure(
            context: DagV2DocumentRequestContext,
            message: String,
        ) {
            if (accepts(context)) block(message)
        }

        fun authorizeBridgeMessage(
            sessionId: String,
            navigationToken: String,
            sourceOrigin: String,
            isMainFrame: Boolean,
        ): DagV2DocumentRequestContext? =
            callbackGate.authorizeBridgeMessage(
                sessionId = sessionId,
                navigationToken = navigationToken,
                sourceOrigin = sourceOrigin,
                isMainFrame = isMainFrame,
            )

        fun onRejectedDocumentCallback() = Unit

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
                            requestContext = null,
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
            callbackGate.clear()
            navigationHistory.clear()
            mutableState.value = DagV2BrowserState()
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
                    requestContext = null,
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
            sessions.cancelActive()?.let { cancelled ->
                callbackGate.cancel(cancelled.requestContext)
                metrics.sessionCancelled(cancelled)
            }
            resourceRouter.cancelVisualRequests()
        }

        internal fun accepts(context: DagV2DocumentRequestContext): Boolean = callbackGate.accepts(context)

        private companion object {
            const val StableWindowMillis = 20_000L
        }
    }

private fun String.withoutDagV2Fragment(): String? =
    runCatching {
        val uri = URI(this)
        URI(uri.scheme, uri.authority, uri.path, uri.query, null).toString()
    }.getOrNull()
