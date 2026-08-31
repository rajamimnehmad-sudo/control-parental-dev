package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ChromeMediaShieldActiveDocumentRevocationGateTest {
    @Test
    fun `submitted revoke accepts only after exact opaque commit`() {
        val results = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val admission = gate.begin(generation = 1L, alreadyOpaque = false, results::add)
        val token = assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.SubmitOpaque>(admission).token

        assertEquals(emptyList(), results)
        assertEquals(token, gate.snapshot().pendingToken)

        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Accepted,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(
            listOf(accepted(ChromeMediaShieldActiveDocumentRevocationReason.OpaqueCommitted)),
            results,
        )
        assertNull(gate.snapshot().pendingToken)
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(1, results.size)
    }

    @Test
    fun `already opaque is immediate one shot and duplicate never accepts`() {
        val first = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val duplicate = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()

        val admission = gate.begin(generation = 7L, alreadyOpaque = true, first::add)
        val token = assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.AlreadyOpaque>(admission).token

        assertEquals(
            listOf(accepted(ChromeMediaShieldActiveDocumentRevocationReason.AlreadyOpaque)),
            first,
        )
        assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.Duplicate>(
            gate.begin(generation = 7L, alreadyOpaque = true, duplicate::add),
        )
        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Duplicate)),
            duplicate,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(1, first.size)
    }

    @Test
    fun `submission failure rejects and late commit is inert`() {
        val results = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val token = submit(gate, generation = 1L, results)

        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
            gate.onSubmissionFailed(token),
        )
        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.SubmissionFailed)),
            results,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(1, results.size)
    }

    @Test
    fun `timeout rejects and late callback is inert`() {
        val results = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val token = submit(gate, generation = 1L, results)

        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
            gate.onTimedOut(token),
        )
        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.TimedOut)),
            results,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(1, results.size)
    }

    @Test
    fun `cancel rejects and late callback is inert`() {
        val results = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val token = submit(gate, generation = 1L, results)

        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Rejected,
            gate.cancel(token),
        )
        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Cancelled)),
            results,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertEquals(1, results.size)
    }

    @Test
    fun `new generation supersedes old and stale commit cannot accept`() {
        val oldResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val currentResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val old = submit(gate, generation = 21L, oldResults)
        val current = submit(gate, generation = 22L, currentResults)

        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Superseded)),
            oldResults,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(old),
        )
        assertEquals(emptyList(), currentResults)
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Accepted,
            gate.onOpaqueCommitted(current),
        )
        assertEquals(
            listOf(accepted(ChromeMediaShieldActiveDocumentRevocationReason.OpaqueCommitted)),
            currentResults,
        )
    }

    @Test
    fun `duplicate storm stays bounded and cannot create a second submit or ack`() {
        val acceptedResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val duplicateResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val token = submit(gate, generation = 33L, acceptedResults)

        repeat(100) {
            assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.Duplicate>(
                gate.begin(generation = 33L, alreadyOpaque = false, duplicateResults::add),
            )
        }

        val beforeCommit = gate.snapshot()
        assertEquals(1L, beforeCommit.nextSequence)
        assertEquals(token, beforeCommit.pendingToken)
        assertEquals(100, duplicateResults.size)
        assertEquals(
            setOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Duplicate)),
            duplicateResults.toSet(),
        )

        gate.onOpaqueCommitted(token)

        val terminal = gate.snapshot()
        assertNull(terminal.pendingToken)
        assertEquals(token, terminal.lastTerminalToken)
        assertEquals(1, acceptedResults.size)
    }

    @Test
    fun `close rejects pending once and all later signals or begins are inert`() {
        val pendingResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val closedResults = mutableListOf<ChromeMediaShieldActiveDocumentRevocationResult>()
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val token = submit(gate, generation = 1L, pendingResults)

        gate.close()
        gate.close()

        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Cancelled)),
            pendingResults,
        )
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
        assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.Closed>(
            gate.begin(generation = 2L, alreadyOpaque = true, closedResults::add),
        )
        assertEquals(
            listOf(rejected(ChromeMediaShieldActiveDocumentRevocationReason.Closed)),
            closedResults,
        )
        assertNull(gate.snapshot().pendingToken)
    }

    @Test
    fun `throwing callback cannot corrupt terminal state or allow replay`() {
        val gate = ChromeMediaShieldActiveDocumentRevocationGate()
        val admission =
            gate.begin(generation = 5L, alreadyOpaque = false) {
                error("diagnostic callback failure")
            }
        val token = assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.SubmitOpaque>(admission).token

        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Accepted,
            gate.onOpaqueCommitted(token),
        )
        assertNull(gate.snapshot().pendingToken)
        assertEquals(
            ChromeMediaShieldActiveDocumentRevocationSignalOutcome.Stale,
            gate.onOpaqueCommitted(token),
        )
    }

    private fun submit(
        gate: ChromeMediaShieldActiveDocumentRevocationGate,
        generation: Long,
        results: MutableList<ChromeMediaShieldActiveDocumentRevocationResult>,
    ): ChromeMediaShieldActiveDocumentRevocationToken =
        assertIs<ChromeMediaShieldActiveDocumentRevocationAdmission.SubmitOpaque>(
            gate.begin(generation, alreadyOpaque = false, results::add),
        ).token

    private fun accepted(reason: ChromeMediaShieldActiveDocumentRevocationReason) =
        ChromeMediaShieldActiveDocumentRevocationResult(
            ChromeMediaShieldActiveDocumentRevocationDecision.Accepted,
            reason,
        )

    private fun rejected(reason: ChromeMediaShieldActiveDocumentRevocationReason) =
        ChromeMediaShieldActiveDocumentRevocationResult(
            ChromeMediaShieldActiveDocumentRevocationDecision.Rejected,
            reason,
        )
}
