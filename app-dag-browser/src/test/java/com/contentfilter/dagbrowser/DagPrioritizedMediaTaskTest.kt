package com.contentfilter.dagbrowser

import java.util.concurrent.CountDownLatch
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagPrioritizedMediaTaskTest {
    @Test
    fun `visible work jumps ahead while equal priorities remain fifo`() {
        val executed = mutableListOf<String>()
        val queue = PriorityBlockingQueue<DagPrioritizedMediaTask>()

        queue += task("background", 0, executed)
        queue += task("nearby-first", 1, executed, DagMediaAnalysisPriority.Nearby)
        queue += task("visible", 2, executed, DagMediaAnalysisPriority.Visible)
        queue += task("nearby-second", 3, executed, DagMediaAnalysisPriority.Nearby)

        while (queue.isNotEmpty()) queue.take().run()

        assertEquals(
            listOf("visible", "nearby-first", "nearby-second", "background"),
            executed,
        )
    }

    @Test
    fun `unknown wire priority stays safely in background`() {
        assertEquals(DagMediaAnalysisPriority.Visible, DagMediaAnalysisPriority.fromWire("visible"))
        assertEquals(DagMediaAnalysisPriority.Nearby, DagMediaAnalysisPriority.fromWire("nearby"))
        assertEquals(DagMediaAnalysisPriority.Background, DagMediaAnalysisPriority.fromWire("unknown"))
    }

    @Test
    fun `native priority queue rejects work beyond its hard capacity and releases slots`() {
        val executed = mutableListOf<String>()
        val queue = DagBoundedMediaTaskQueue(capacity = 2)

        assertTrue(queue.offer(task("background", 0, executed)))
        assertTrue(queue.offer(task("visible", 1, executed, DagMediaAnalysisPriority.Visible)))
        assertFalse(queue.offer(task("overflow", 2, executed)))
        assertEquals(0, queue.remainingCapacity())

        queue.poll()?.run()
        assertEquals(listOf("visible"), executed)
        assertEquals(1, queue.remainingCapacity())
        assertTrue(queue.offer(task("nearby", 3, executed, DagMediaAnalysisPriority.Nearby)))

        val discarded = mutableListOf<Runnable>()
        assertEquals(2, queue.drainTo(discarded))
        assertEquals(2, queue.remainingCapacity())
        assertNull(queue.poll())
    }

    @Test
    fun `document replacement purges only queued work from the retired document`() {
        val queue = DagBoundedMediaTaskQueue(capacity = 4)
        val discarded = mutableListOf<String>()
        val oldDocument = DagMediaDocumentIdentity(7, "document_a1")
        val newDocument = DagMediaDocumentIdentity(7, "document_a2")
        val otherTab = DagMediaDocumentIdentity(8, "document_b1")
        listOf(
            oldDocument to "old-1",
            newDocument to "new",
            oldDocument to "old-2",
            otherTab to "other",
        ).forEachIndexed { sequence, (identity, label) ->
            assertTrue(
                queue.offer(
                    DagPrioritizedMediaTask(
                        priority = DagMediaAnalysisPriority.Nearby,
                        sequence = sequence.toLong(),
                        documentIdentity = identity,
                        onDiscard = { discarded += label },
                    ) {},
                ),
            )
        }

        assertEquals(
            2,
            queue.discardMatching { task -> task.documentIdentity == oldDocument },
        )
        assertEquals(listOf("old-1", "old-2"), discarded.sorted())
        assertEquals(2, queue.size)
        assertEquals(2, queue.remainingCapacity())

        val remaining = mutableListOf<Runnable>()
        queue.drainTo(remaining)
        assertEquals(
            setOf(newDocument, otherTab),
            remaining.mapNotNull { (it as DagPrioritizedMediaTask).documentIdentity }.toSet(),
        )
        assertEquals(4, queue.remainingCapacity())
    }

    @Test
    fun `executor rejects overflow and shutdown returns every queued permit`() {
        val queue = DagBoundedMediaTaskQueue(capacity = 2)
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
            )
        try {
            executor.execute(
                DagPrioritizedMediaTask(DagMediaAnalysisPriority.Visible, 0) {
                    running.countDown()
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                },
            )
            assertTrue(running.await(TestTimeoutSeconds, TimeUnit.SECONDS))
            executor.execute(DagPrioritizedMediaTask(DagMediaAnalysisPriority.Background, 1) {})
            executor.execute(DagPrioritizedMediaTask(DagMediaAnalysisPriority.Visible, 2) {})

            assertFailsWith<RejectedExecutionException> {
                executor.execute(DagPrioritizedMediaTask(DagMediaAnalysisPriority.Nearby, 3) {})
            }

            val discarded = executor.shutdownNow()
            assertEquals(2, discarded.size)
            assertEquals(2, queue.remainingCapacity())
            assertTrue(executor.awaitTermination(TestTimeoutSeconds, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TestTimeoutSeconds, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `partial drain failure still releases each removed permit`() {
        val queue = DagBoundedMediaTaskQueue(capacity = 3)
        repeat(3) { sequence ->
            assertTrue(
                queue.offer(
                    DagPrioritizedMediaTask(DagMediaAnalysisPriority.Background, sequence.toLong()) {},
                ),
            )
        }
        val accepted = mutableListOf<Runnable>()
        val failingTarget =
            object : AbstractMutableCollection<Runnable>() {
                override val size: Int
                    get() = accepted.size

                override fun iterator(): MutableIterator<Runnable> = accepted.iterator()

                override fun add(element: Runnable): Boolean {
                    if (accepted.isNotEmpty()) error("stop after a partial drain")
                    return accepted.add(element)
                }
            }

        assertFailsWith<IllegalStateException> { queue.drainTo(failingTarget) }

        assertEquals(1, accepted.size)
        assertEquals(1, queue.size)
        assertEquals(2, queue.remainingCapacity())
        queue.clear()
        assertEquals(3, queue.remainingCapacity())
    }

    @Test
    fun `extension timestamp becomes a strict remaining native budget`() {
        assertEquals(
            1_750L,
            DagMediaAnalysisDeadline.remainingMillis(
                sentAtEpochMillis = 9_500L,
                nowEpochMillis = 10_000L,
                lifetimeMillis = 2_250L,
                allowedFutureSkewMillis = 250L,
            ),
        )
        assertNull(
            DagMediaAnalysisDeadline.remainingMillis(
                sentAtEpochMillis = 7_750L,
                nowEpochMillis = 10_000L,
                lifetimeMillis = 2_250L,
                allowedFutureSkewMillis = 250L,
            ),
        )
        assertNull(
            DagMediaAnalysisDeadline.remainingMillis(
                sentAtEpochMillis = 10_251L,
                nowEpochMillis = 10_000L,
                lifetimeMillis = 2_250L,
                allowedFutureSkewMillis = 250L,
            ),
        )
    }

    @Test
    fun `lease expires on time generation change or lifecycle close`() {
        var now = 100L
        var generation = 4L
        var accepting = true
        val lease =
            DagMediaAnalysisLease(
                generation = generation,
                deadlineElapsedRealtime = 200L,
                currentGeneration = { generation },
                elapsedRealtime = { now },
                acceptingWork = { accepting },
            )

        assertTrue(lease.canContinue())
        generation += 1
        assertFalse(lease.canContinue())
        generation = 4L
        now = 200L
        assertFalse(lease.canContinue())
        now = 100L
        accepting = false
        assertFalse(lease.canContinue())
    }

    @Test
    fun `document registry replaces old work and ignores a stale retirement`() {
        val registry = DagMediaDocumentRegistry()
        registry.markCurrent(17, "document_first")

        assertTrue(registry.isCurrent(17, "document_first"))
        registry.markCurrent(17, "document_second")
        assertFalse(registry.isCurrent(17, "document_first"))
        assertTrue(registry.isCurrent(17, "document_second"))

        assertFalse(registry.retire(17, "document_first"))
        assertTrue(registry.isCurrent(17, "document_second"))
        assertTrue(registry.retire(17, "document_second"))
        assertFalse(registry.isCurrent(17, "document_second"))
    }

    @Test
    fun `document diagnostics require an exact confirmed non private owner`() {
        val registry = DagMediaDocumentRegistry()
        val normal = DagMediaDocumentIdentity(17, "document_normal")
        val private = DagMediaDocumentIdentity(23, "document_private")
        registry.markCurrent(normal.tabId, normal.documentToken, "preview_normal")
        registry.markCurrent(private.tabId, private.documentToken, "preview_private")

        assertFalse(registry.allowsDiagnostics(normal))
        assertFalse(registry.allowsDiagnostics(private))

        registry.bindPrivacy("preview_normal", privateDocument = false)
        registry.bindPrivacy("preview_private", privateDocument = true)

        assertTrue(registry.allowsDiagnostics(normal))
        assertFalse(registry.allowsDiagnostics(private))
    }

    @Test
    fun `late private completion cannot inherit the active normal tab permission`() {
        val registry = DagMediaDocumentRegistry()
        val private = DagMediaDocumentIdentity(17, "document_private")
        val normal = DagMediaDocumentIdentity(23, "document_normal")
        registry.markCurrent(private.tabId, private.documentToken, "preview_private")
        registry.bindPrivacy("preview_private", privateDocument = true)
        registry.markCurrent(normal.tabId, normal.documentToken, "preview_normal")
        registry.bindPrivacy("preview_normal", privateDocument = false)

        assertFalse(registry.allowsDiagnostics(private))
        assertTrue(registry.allowsDiagnostics(normal))

        val replacement = DagMediaDocumentIdentity(17, "document_private_next")
        registry.markCurrent(replacement.tabId, replacement.documentToken, "preview_private_next")
        assertFalse(registry.allowsDiagnostics(private))
        assertFalse(registry.allowsDiagnostics(replacement))
    }

    @Test
    fun `lease expires as soon as its browser document changes`() {
        var currentDocument = true
        val lease =
            DagMediaAnalysisLease(
                generation = 2L,
                deadlineElapsedRealtime = 500L,
                currentGeneration = { 2L },
                elapsedRealtime = { 100L },
                acceptingWork = { true },
                documentCurrent = { currentDocument },
            )

        assertTrue(lease.canContinue())
        currentDocument = false
        assertFalse(lease.canContinue())
    }

    private fun task(
        name: String,
        sequence: Long,
        output: MutableList<String>,
        priority: DagMediaAnalysisPriority = DagMediaAnalysisPriority.Background,
    ) = DagPrioritizedMediaTask(priority, sequence) { output += name }

    private companion object {
        const val TestTimeoutSeconds = 5L
    }
}
