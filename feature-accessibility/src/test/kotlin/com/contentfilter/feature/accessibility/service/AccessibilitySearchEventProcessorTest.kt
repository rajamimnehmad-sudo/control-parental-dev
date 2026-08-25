package com.contentfilter.feature.accessibility.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessibilitySearchEventProcessorTest {
    @Test
    fun `burst is conflated to running item plus latest generation`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val processed = CopyOnWriteArrayList<Long>()
        val processor =
            AccessibilitySearchEventProcessor(scope, dispatcher) { trigger ->
                processed += trigger.generation
                if (processed.size == 1) {
                    firstStarted.countDown()
                    releaseFirst.await(1, TimeUnit.SECONDS)
                } else {
                    secondFinished.countDown()
                }
            }

        try {
            val first = processor.submit("com.android.chrome", "FIRST")
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            var latest = first
            repeat(100) { index ->
                latest = processor.submit("com.android.chrome", "BURST_$index")
            }
            releaseFirst.countDown()
            assertTrue(secondFinished.await(1, TimeUnit.SECONDS))

            assertEquals(listOf(first, latest), processed.toList())
            assertEquals(101L, processor.metrics().submitted)
            assertTrue(processor.metrics().started <= 2L)
        } finally {
            processor.close()
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `new generation makes running result stale before it can act`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val currency = CopyOnWriteArrayList<Boolean>()
        lateinit var processor: AccessibilitySearchEventProcessor
        processor =
            AccessibilitySearchEventProcessor(scope, dispatcher) { trigger ->
                if (trigger.eventLabel == "FIRST") {
                    firstStarted.countDown()
                    releaseFirst.await(1, TimeUnit.SECONDS)
                }
                currency += processor.isCurrent(trigger.generation)
                finished.countDown()
            }

        try {
            processor.submit("com.android.chrome", "FIRST")
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            processor.submit("com.android.chrome", "LATEST")
            releaseFirst.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))

            assertEquals(listOf(false, true), currency.toList())
        } finally {
            processor.close()
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `browser scanner stops on focused address bar without walking remaining tree`() {
        val address =
            FakeNode(
                viewId = "com.android.chrome:id/url_bar",
                textValue = "https://www.google.com/search?q=test",
                focused = true,
            )
        val root = FakeNode(children = listOf(address, deepTree(40)))
        val scanner = AccessibilityBrowserPageScanner(maximumNodes = 96, maximumScanNanos = 1_000L, nanoTime = { 0L })

        val result = scanner.scan(root)

        assertEquals("google.com", result.observation.host)
        assertTrue(result.observation.addressBarFocused)
        assertEquals(2, result.visitedNodes)
        assertFalse(result.nodeBudgetExhausted)
        assertFalse(result.timeBudgetExhausted)
    }

    @Test
    fun `browser scanner enforces node budget`() {
        val scanner = AccessibilityBrowserPageScanner(maximumNodes = 4, maximumScanNanos = 1_000L, nanoTime = { 0L })

        val result = scanner.scan(deepTree(20))

        assertEquals(4, result.visitedNodes)
        assertTrue(result.nodeBudgetExhausted)
        assertFalse(result.timeBudgetExhausted)
        assertNull(result.observation.host)
    }

    @Test
    fun `browser scanner enforces time budget`() {
        var now = 0L
        val scanner =
            AccessibilityBrowserPageScanner(
                maximumNodes = 50,
                maximumScanNanos = 25L,
                nanoTime = {
                    val current = now
                    now += 10L
                    current
                },
            )

        val result = scanner.scan(deepTree(20))

        assertTrue(result.visitedNodes in 1..3)
        assertTrue(result.timeBudgetExhausted)
        assertFalse(result.nodeBudgetExhausted)
    }

    private fun deepTree(depth: Int): FakeNode {
        var current = FakeNode()
        repeat(depth) { current = FakeNode(children = listOf(current)) }
        return current
    }

    private data class FakeNode(
        val viewId: String? = null,
        val textValue: CharSequence? = null,
        val description: CharSequence? = null,
        val focused: Boolean = false,
        val children: List<FakeNode> = emptyList(),
    ) : AccessibilityNodeReader {
        override val viewIdResourceName: String?
            get() = viewId
        override val text: CharSequence?
            get() = textValue
        override val contentDescription: CharSequence?
            get() = description
        override val isFocused: Boolean
            get() = focused
        override val childCount: Int
            get() = children.size

        override fun childAt(index: Int): AccessibilityNodeReader? = children.getOrNull(index)
    }
}
