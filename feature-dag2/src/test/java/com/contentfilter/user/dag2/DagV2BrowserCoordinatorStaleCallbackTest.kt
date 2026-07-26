package com.contentfilter.user.dag2

import com.contentfilter.core.domain.model.PolicyMutationReceipt
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2BrowserCoordinatorStaleCallbackTest {
    @Test
    fun `late production callback A cannot alter browser state metrics or document B`() {
        val sessions = DagV2DocumentSession()
        val contexts = DagV2DocumentContextRegistry()
        val callbackGate = DagV2DocumentCallbackGate(contexts, sessions)
        val metrics = DagV2Metrics()
        val pipeline =
            DagV2ImagePipeline(
                DagV2FailClosedImageDecisionProvider(),
                DagV2NeutralImageFactory(),
                sessions,
                metrics,
            )
        val router = DagV2ResourceRouter(pipeline, metrics, PublicNetworkDestinationGuard())
        val policy = DagV2SitePolicy(EmptyPolicyRepository, EmptyDomainList, AllowingCanonicalPolicy)
        val coordinator =
            DagV2BrowserCoordinator(
                searchOrchestrator = DagV2SearchOrchestrator(policy, EmptySearchGateway),
                sitePolicy = policy,
                networkGuard = DagV2NetworkGuard(PublicNetworkDestinationGuard()),
                sessions = sessions,
                callbackGate = callbackGate,
                resourceRouter = router,
                metrics = metrics,
            )

        val first = sessions.start("https://example.com/a")
        contexts.register(first.requestContext)
        sessions.beginFullAnalysis(first.sessionId, first.navigationToken)
        sessions.cancelActive()
        contexts.cancel(first.requestContext)
        val second = sessions.start("https://example.com/b")
        contexts.register(second.requestContext)
        router.onNewDocument(second)
        val stateBefore = coordinator.state.value
        val metricsBefore = metrics.snapshot.value
        val sessionBefore = sessions.snapshot()

        coordinator.onDocumentAnalysis(
            context = first.requestContext,
            url = first.mainDocumentUrl,
            title = "Documento A",
            visibleText = "Respuesta tardía",
        )
        coordinator.onDocumentAnalysisFailed(first.requestContext)
        coordinator.onSpaUrlChanged(first.requestContext, "https://example.com/a?late=1")
        coordinator.onInternalInteraction(first.requestContext, DagV2InternalInteraction.Button)
        coordinator.onRendererGone(first.requestContext)

        assertEquals(stateBefore, coordinator.state.value)
        assertEquals(metricsBefore, metrics.snapshot.value)
        assertEquals(sessionBefore, sessions.snapshot())
        assertEquals(second.sessionId, sessions.snapshot()?.sessionId)
        assertEquals(0, sessions.snapshot()?.fullPageAnalysisCount)
    }

    private object EmptySearchGateway : DagV2SearchGateway {
        override suspend fun search(query: String): DagV2SearchOutcome = DagV2SearchOutcome.Success(emptyList())
    }

    private object EmptyPolicyRepository : PolicyRepository {
        private val snapshot = PolicySnapshot("test", version = 1, rules = emptyList())

        override fun observeActivePolicy(deviceId: String?): Flow<PolicySnapshot> = flowOf(snapshot)

        override suspend fun getActivePolicy(deviceId: String?): PolicySnapshot = snapshot

        override suspend fun saveRule(
            rule: PolicyRule,
            deviceId: String?,
        ) = error("Not used")

        override suspend fun saveRules(
            rules: List<PolicyRule>,
            deviceId: String?,
            requestId: String?,
        ): PolicyMutationReceipt = error("Not used")

        override suspend fun deleteRule(rule: PolicyRule) = error("Not used")
    }

    private object EmptyDomainList : DynamicDomainBlocklist {
        override fun categoryFor(domain: String): String? = null
    }

    private object AllowingCanonicalPolicy : DagV2CanonicalTextPolicy {
        override fun classifyQuery(query: String) = allowed()

        override fun classifyNavigation(url: String) = allowed()

        override fun classifyResult(result: DagV2SearchResult) = allowed()

        override fun classifyPage(
            url: String,
            title: String,
            visibleText: String,
        ) = allowed()

        private fun allowed() = DagV2CanonicalTextResult(DagV2CanonicalTextDecision.Allowed, "test_allowed")
    }
}
