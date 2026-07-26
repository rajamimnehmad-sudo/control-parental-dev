package com.contentfilter.user.dag2

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DagV2SearchOrchestratorTest {
    @Test
    fun `adult query is blocked before Brave`() =
        runBlocking {
            val gateway = RecordingGateway()
            val orchestrator = DagV2SearchOrchestrator(AdultBlockingPolicy, gateway)

            val outcome = orchestrator.search("consulta porno")

            assertIs<DagV2SearchOutcome.Failure>(outcome)
            assertEquals(0, gateway.calls)
        }

    @Test
    fun `each remote result is filtered locally`() =
        runBlocking {
            val gateway =
                RecordingGateway(
                    DagV2SearchOutcome.Success(
                        listOf(
                            DagV2SearchResult("Tienda", "https://safe.example", "Productos"),
                            DagV2SearchResult("Bloqueado", "https://adult.test", "Fixture"),
                        ),
                    ),
                )
            val orchestrator = DagV2SearchOrchestrator(AdultBlockingPolicy, gateway)

            val outcome = assertIs<DagV2SearchOutcome.Success>(orchestrator.search("electrónica"))

            assertEquals(1, gateway.calls)
            assertEquals(listOf("https://safe.example"), outcome.results.map(DagV2SearchResult::url))
        }

    private class RecordingGateway(
        private val outcome: DagV2SearchOutcome = DagV2SearchOutcome.Success(emptyList()),
    ) : DagV2SearchGateway {
        var calls = 0

        override suspend fun search(query: String): DagV2SearchOutcome {
            calls += 1
            return outcome
        }
    }

    private object AdultBlockingPolicy : DagV2SearchPolicy {
        override fun evaluateQuery(query: String): DagV2PolicyResult =
            if (query.contains("porno", ignoreCase = true)) {
                DagV2PolicyResult(DagV2SiteDecision.Block, "blocked")
            } else {
                DagV2PolicyResult(DagV2SiteDecision.Allow, "allowed")
            }

        override suspend fun evaluateResult(result: DagV2SearchResult): DagV2PolicyResult =
            if (result.url.contains("adult.test")) {
                DagV2PolicyResult(DagV2SiteDecision.Block, "blocked")
            } else {
                DagV2PolicyResult(DagV2SiteDecision.Allow, "allowed")
            }
    }
}
