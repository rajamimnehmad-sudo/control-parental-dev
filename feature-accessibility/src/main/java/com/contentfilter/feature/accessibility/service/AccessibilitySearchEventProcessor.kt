package com.contentfilter.feature.accessibility.service

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

internal data class SearchProtectionTrigger(
    val generation: Long,
    val packageName: String,
    val eventLabel: String,
)

internal data class AccessibilitySearchEventMetrics(
    val submitted: Long,
    val started: Long,
    val staleBeforeStart: Long,
)

/**
 * Single-consumer, conflated processor for expensive accessibility search observations.
 *
 * Callers submit primitive data only. AccessibilityEvent/AccessibilityNodeInfo instances are never
 * retained across the callback boundary.
 */
internal class AccessibilitySearchEventProcessor(
    scope: CoroutineScope,
    workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val process: suspend (SearchProtectionTrigger) -> Unit,
) : AutoCloseable {
    private val generation = AtomicLong()
    private val submitted = AtomicLong()
    private val started = AtomicLong()
    private val staleBeforeStart = AtomicLong()
    private val requests = Channel<SearchProtectionTrigger>(Channel.CONFLATED)
    private val worker: Job =
        scope.launch(workerDispatcher) {
            for (trigger in requests) {
                if (!isCurrent(trigger.generation)) {
                    staleBeforeStart.incrementAndGet()
                    continue
                }
                started.incrementAndGet()
                process(trigger)
            }
        }

    fun submit(
        packageName: String,
        eventLabel: String,
    ): Long {
        val nextGeneration = generation.incrementAndGet()
        submitted.incrementAndGet()
        requests.trySend(
            SearchProtectionTrigger(
                generation = nextGeneration,
                packageName = packageName,
                eventLabel = eventLabel,
            ),
        )
        return nextGeneration
    }

    fun isCurrent(candidateGeneration: Long): Boolean = generation.get() == candidateGeneration

    fun metrics(): AccessibilitySearchEventMetrics =
        AccessibilitySearchEventMetrics(
            submitted = submitted.get(),
            started = started.get(),
            staleBeforeStart = staleBeforeStart.get(),
        )

    override fun close() {
        generation.incrementAndGet()
        requests.close()
        worker.cancel()
    }
}

internal data class BrowserPageObservation(
    val host: String? = null,
    val addressBarFocused: Boolean = false,
)

internal data class BrowserPageScanResult(
    val observation: BrowserPageObservation,
    val visitedNodes: Int,
    val nodeBudgetExhausted: Boolean,
    val timeBudgetExhausted: Boolean,
)

internal interface AccessibilityNodeReader {
    val viewIdResourceName: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val isFocused: Boolean
    val childCount: Int

    fun childAt(index: Int): AccessibilityNodeReader?
}

internal class AndroidAccessibilityNodeReader(
    private val node: AccessibilityNodeInfo,
) : AccessibilityNodeReader {
    override val viewIdResourceName: String?
        get() = runCatching { node.viewIdResourceName }.getOrNull()

    override val text: CharSequence?
        get() = runCatching { node.text }.getOrNull()

    override val contentDescription: CharSequence?
        get() = runCatching { node.contentDescription }.getOrNull()

    override val isFocused: Boolean
        get() = runCatching { node.isFocused }.getOrDefault(false)

    override val childCount: Int
        get() = runCatching { node.childCount }.getOrDefault(0)

    override fun childAt(index: Int): AccessibilityNodeReader? =
        runCatching { node.getChild(index) }
            .getOrNull()
            ?.let(::AndroidAccessibilityNodeReader)
}

/** Bounded breadth-first browser tree scan intended to run off the main thread. */
internal class AccessibilityBrowserPageScanner(
    private val maximumNodes: Int = DefaultMaximumNodes,
    private val maximumScanNanos: Long = DefaultMaximumScanNanos,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    init {
        require(maximumNodes > 0)
        require(maximumScanNanos > 0L)
    }

    fun scan(root: AccessibilityNodeReader?): BrowserPageScanResult {
        if (root == null) return BrowserPageScanResult(BrowserPageObservation(), 0, false, false)
        val startedAt = nanoTime()
        val pending = ArrayDeque<AccessibilityNodeReader>()
        pending.addLast(root)
        var observation = BrowserPageObservation()
        var visited = 0
        var nodeBudgetExhausted = false

        while (pending.isNotEmpty()) {
            if (visited >= maximumNodes) {
                return BrowserPageScanResult(observation, visited, nodeBudgetExhausted = true, timeBudgetExhausted = false)
            }
            if (nanoTime() - startedAt >= maximumScanNanos) {
                return BrowserPageScanResult(observation, visited, nodeBudgetExhausted, timeBudgetExhausted = true)
            }

            val node = pending.removeFirst()
            visited++
            if (node.viewIdResourceName.isAddressBarViewId()) {
                val address =
                    SearchEngineScreenDetector.addressObservationFromAddressBarText(node.text)
                        ?: SearchEngineScreenDetector.addressObservationFromAddressBarText(node.contentDescription)
                if (address != null && (observation.host == null || node.isFocused)) {
                    observation =
                        BrowserPageObservation(
                            host = address.host,
                            addressBarFocused = node.isFocused,
                        )
                    if (node.isFocused) {
                        return BrowserPageScanResult(observation, visited, nodeBudgetExhausted, false)
                    }
                }
            }

            val children = node.childCount
            for (index in 0 until children) {
                if (nanoTime() - startedAt >= maximumScanNanos) {
                    return BrowserPageScanResult(observation, visited, nodeBudgetExhausted, timeBudgetExhausted = true)
                }
                if (visited + pending.size >= maximumNodes) {
                    nodeBudgetExhausted = true
                    break
                }
                node.childAt(index)?.let(pending::addLast)
            }
        }
        return BrowserPageScanResult(observation, visited, nodeBudgetExhausted, false)
    }

    private companion object {
        const val DefaultMaximumNodes = 96
        const val DefaultMaximumScanNanos = 12_000_000L
    }
}

internal fun String?.isAddressBarViewId(): Boolean {
    val value = this?.lowercase() ?: return false
    return AccessibilityAddressBarViewIdParts.any { part ->
        value.endsWith("/id/$part") || value.endsWith(":id/$part")
    } || (("url" in value || "address" in value || "location" in value) && "bar" in value)
}

internal val AccessibilityAddressBarViewIdParts =
    setOf(
        "url_bar",
        "location_bar_edit_text",
        "address_bar",
        "mozac_browser_toolbar_url_view",
    )
