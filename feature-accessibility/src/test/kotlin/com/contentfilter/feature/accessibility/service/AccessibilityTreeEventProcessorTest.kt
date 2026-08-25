package com.contentfilter.feature.accessibility.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessibilityTreeEventProcessorTest {
    @Test
    fun `same package burst supersedes old result and converges on latest search`() {
        val harness = ProcessorHarness()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val latestFinished = CountDownLatch(1)
        val processed = CopyOnWriteArrayList<Long>()
        val firstStillLatest = AtomicBoolean(true)
        lateinit var processor: AccessibilityTreeEventProcessor
        processor =
            harness.processor(
                processSearch = { trigger ->
                    processed += trigger.sequence
                    if (processed.size == 1) {
                        firstStarted.countDown()
                        releaseFirst.await(1, TimeUnit.SECONDS)
                        firstStillLatest.set(processor.isSearchContextCurrent(trigger))
                    } else {
                        assertTrue(processor.isSearchContextCurrent(trigger))
                        latestFinished.countDown()
                    }
                },
            )

        try {
            val first = processor.submitSearch(Chrome, "FIRST")
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            var latest = first
            repeat(100) { index -> latest = processor.submitSearch(Chrome, "BURST_$index") }
            releaseFirst.countDown()
            assertTrue(latestFinished.await(1, TimeUnit.SECONDS))

            assertEquals(listOf(first, latest), processed.toList())
            assertFalse(firstStillLatest.get())
            assertEquals(101L, processor.metrics().submittedSearch)
            assertTrue(processor.metrics().coalescedSearch >= 99L)
            assertTrue(processor.metrics().startedSearch <= 2L)
        } finally {
            processor.close()
            harness.close()
        }
    }

    @Test
    fun `package switch invalidates running search context`() {
        val harness = ProcessorHarness()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val currency = CopyOnWriteArrayList<Boolean>()
        lateinit var processor: AccessibilityTreeEventProcessor
        processor =
            harness.processor(
                processSearch = { trigger ->
                    if (trigger.packageName == Chrome) {
                        firstStarted.countDown()
                        releaseFirst.await(1, TimeUnit.SECONDS)
                    }
                    currency += processor.isSearchContextCurrent(trigger)
                    finished.countDown()
                },
            )

        try {
            processor.submitSearch(Chrome, "FIRST")
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            processor.submitSearch("org.mozilla.firefox", "SWITCH")
            releaseFirst.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))

            assertEquals(listOf(false, true), currency.toList())
        } finally {
            processor.close()
            harness.close()
        }
    }

    @Test
    fun `settings submission invalidates search from prior package surface`() {
        val harness = ProcessorHarness()
        val searchStarted = CountDownLatch(1)
        val releaseSearch = CountDownLatch(1)
        val searchCurrent = AtomicBoolean(true)
        val settingsFinished = CountDownLatch(1)
        lateinit var processor: AccessibilityTreeEventProcessor
        processor =
            harness.processor(
                processSearch = { trigger ->
                    searchStarted.countDown()
                    releaseSearch.await(1, TimeUnit.SECONDS)
                    searchCurrent.set(processor.isSearchContextCurrent(trigger))
                },
                processSettings = { settingsFinished.countDown() },
            )

        try {
            processor.submitSearch(Chrome, "SEARCH")
            assertTrue(searchStarted.await(1, TimeUnit.SECONDS))
            processor.submitSettings(Settings, "SETTINGS", 1, null, SettingsEventSignals())
            releaseSearch.countDown()
            assertTrue(settingsFinished.await(1, TimeUnit.SECONDS))
            assertFalse(searchCurrent.get())
        } finally {
            processor.close()
            harness.close()
        }
    }

    @Test
    fun `policy invalidation makes older same package search stale`() {
        val harness = ProcessorHarness()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val currency = CopyOnWriteArrayList<Boolean>()
        lateinit var processor: AccessibilityTreeEventProcessor
        processor =
            harness.processor(
                processSearch = { trigger ->
                    if (trigger.eventLabel == "FIRST") {
                        firstStarted.countDown()
                        releaseFirst.await(1, TimeUnit.SECONDS)
                    }
                    currency += processor.isSearchContextCurrent(trigger)
                    finished.countDown()
                },
            )

        try {
            processor.submitSearch(Chrome, "FIRST")
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            processor.submitSearch(Chrome, "POLICY_CHANGED", invalidateContext = true)
            releaseFirst.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))

            assertEquals(listOf(false, true), currency.toList())
        } finally {
            processor.close()
            harness.close()
        }
    }

    @Test
    fun `settings burst remains bounded and does not starve pending search`() {
        val harness = ProcessorHarness()
        val firstSettingsStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val searchFinished = CountDownLatch(1)
        val settingsSequences = CopyOnWriteArrayList<Long>()
        val searchSequences = CopyOnWriteArrayList<Long>()
        val processor =
            harness.processor(
                processSearch = { trigger ->
                    searchSequences += trigger.sequence
                    searchFinished.countDown()
                },
                processSettings = { trigger ->
                    settingsSequences += trigger.sequence
                    if (settingsSequences.size == 1) {
                        firstSettingsStarted.countDown()
                        releaseFirst.await(1, TimeUnit.SECONDS)
                    }
                },
            )

        try {
            processor.submitSettings(Settings, "FIRST", 1, null, SettingsEventSignals())
            assertTrue(firstSettingsStarted.await(1, TimeUnit.SECONDS))
            var latestSettings = 0L
            repeat(100) { index ->
                latestSettings =
                    processor.submitSettings(
                        Settings,
                        "BURST_$index",
                        1,
                        null,
                        SettingsEventSignals(),
                    )
            }
            val search = processor.submitSearch(Chrome, "SEARCH")
            releaseFirst.countDown()
            assertTrue(searchFinished.await(1, TimeUnit.SECONDS))

            assertEquals(2, settingsSequences.size)
            assertEquals(latestSettings, settingsSequences.last())
            assertEquals(listOf(search), searchSequences.toList())
            assertEquals(101L, processor.metrics().submittedSettings)
            assertTrue(processor.metrics().coalescedSettings >= 99L)
        } finally {
            processor.close()
            harness.close()
        }
    }

    @Test
    fun `explicit context invalidation clears pending settings work`() {
        val harness = ProcessorHarness()
        val searchStarted = CountDownLatch(1)
        val releaseSearch = CountDownLatch(1)
        val settingsCalls = CopyOnWriteArrayList<Long>()
        val processor =
            harness.processor(
                processSearch = {
                    searchStarted.countDown()
                    releaseSearch.await(1, TimeUnit.SECONDS)
                },
                processSettings = { trigger -> settingsCalls += trigger.sequence },
            )

        try {
            processor.submitSearch(Chrome, "HOLD")
            assertTrue(searchStarted.await(1, TimeUnit.SECONDS))
            processor.submitSettings(Settings, "PENDING", 1, null, SettingsEventSignals())
            processor.invalidateSettingsContext()
            releaseSearch.countDown()
            Thread.sleep(50)
            assertTrue(settingsCalls.isEmpty())
        } finally {
            processor.close()
            harness.close()
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
    fun `browser scanner enforces node and time budgets`() {
        val nodeBounded = AccessibilityBrowserPageScanner(maximumNodes = 4, maximumScanNanos = 1_000L, nanoTime = { 0L })
        val byNodes = nodeBounded.scan(deepTree(20))
        assertEquals(4, byNodes.visitedNodes)
        assertTrue(byNodes.nodeBudgetExhausted)
        assertNull(byNodes.observation.host)

        var now = 0L
        val timeBounded =
            AccessibilityBrowserPageScanner(
                maximumNodes = 50,
                maximumScanNanos = 25L,
                nanoTime = {
                    val current = now
                    now += 10L
                    current
                },
            )
        val byTime = timeBounded.scan(deepTree(20))
        assertTrue(byTime.visitedNodes in 1..3)
        assertTrue(byTime.timeBudgetExhausted)
    }

    @Test
    fun `settings scanner gathers all signals in one bounded traversal`() {
        val own = FakeNode(textValue = "com.contentfilter.user.dev")
        val admin = FakeNode(description = "Content Filter Admin")
        val dangerous = FakeNode(viewId = "com.android.settings:id/uninstall_button", clickable = true)
        val installSource = FakeNode(textValue = "Permitir desde esta fuente")
        val root = FakeNode(children = listOf(own, admin, dangerous, installSource, deepTree(20)))
        val scanner = AccessibilitySettingsPageScanner(maximumNodes = 32, maximumScanNanos = 1_000L, nanoTime = { 0L })

        val result = scanner.scan(root, "com.contentfilter.user.dev", "Content Filter")

        assertTrue(result.signals.ownAppIdentityVisible)
        assertTrue(result.signals.adminAppIdentityVisible)
        assertTrue(result.signals.dangerousSettingsActionVisible)
        assertTrue(result.signals.installSourceSettingsVisible)
        assertTrue(result.visitedNodes <= 5)
        assertFalse(result.timeBudgetExhausted)
    }

    @Test
    fun `settings scanner enforces one bounded walk instead of four independent walks`() {
        val scanner = AccessibilitySettingsPageScanner(maximumNodes = 4, maximumScanNanos = 1_000L, nanoTime = { 0L })

        val result = scanner.scan(deepTree(30), "com.contentfilter.user.dev", "Content Filter")

        assertEquals(4, result.visitedNodes)
        assertTrue(result.nodeBudgetExhausted)
    }

    private class ProcessorHarness : AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor()
        private val dispatcher = executor.asCoroutineDispatcher()
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        fun processor(
            processSearch: suspend (SearchProtectionTrigger) -> Unit = {},
            processSettings: suspend (SettingsProtectionTrigger) -> Unit = {},
        ) = AccessibilityTreeEventProcessor(scope, dispatcher, processSearch, processSettings)

        override fun close() {
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
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
        val clickable: Boolean = false,
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
        override val isClickable: Boolean
            get() = clickable
        override val childCount: Int
            get() = children.size

        override fun childAt(index: Int): AccessibilityNodeReader? = children.getOrNull(index)
    }

    private companion object {
        const val Chrome = "com.android.chrome"
        const val Settings = "com.android.settings"
    }
}
