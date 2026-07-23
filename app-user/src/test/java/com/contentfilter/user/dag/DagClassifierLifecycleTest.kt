package com.contentfilter.user.dag

import kotlinx.coroutines.CancellationException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DagClassifierLifecycleTest {
    @Test
    fun `cancelled neural batch stops before the next inference`() {
        var active = true
        val classified = mutableListOf<String>()

        assertFailsWith<CancellationException> {
            dagMapNeuralBatch(
                values = listOf("first", "second", "third"),
                ensureActive = {
                    if (!active) throw CancellationException("cancelled")
                },
            ) { text ->
                classified += text
                active = false
                text
            }
        }

        assertEquals(listOf("first"), classified)
    }

    @Test
    fun `shared image preparation isolates each model failure`() {
        val attempts = mutableListOf<String>()

        prepareDagSharedImageClassifiers(
            prepareProfessional = {
                attempts += "professional"
                error("professional model unavailable")
            },
            prepareModesty = {
                attempts += "modesty"
                error("modesty model unavailable")
            },
        )

        assertEquals(listOf("professional", "modesty"), attempts)
    }

    @Test
    fun `classifier disposal is scheduled and closes shared resources last`() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val firstCloseStarted = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        var workerTask: (() -> Unit)? = null

        scheduleDagClassifierDisposal(
            cancelLoader = { order += "loader-cancel" },
            schedule = { task -> workerTask = task },
            classifierClosers =
                listOf(
                    {
                        order += "classifier-1"
                        firstCloseStarted.countDown()
                        check(releaseInference.await(1, TimeUnit.SECONDS))
                    },
                    {
                        order += "classifier-2"
                        error("second classifier close failed")
                    },
                ),
            sharedCloser = { order += "shared" },
        )

        assertEquals(listOf("loader-cancel"), order.toList())
        val cleanup = assertNotNull(workerTask)
        val worker = thread(name = "dag-classifier-disposal-test") { cleanup() }
        assertTrue(firstCloseStarted.await(1, TimeUnit.SECONDS))
        assertEquals(listOf("loader-cancel", "classifier-1"), order.toList())

        releaseInference.countDown()
        worker.join(1_000)

        assertFalse(worker.isAlive)
        assertEquals(listOf("loader-cancel", "classifier-1", "classifier-2", "shared"), order.toList())
    }
}
