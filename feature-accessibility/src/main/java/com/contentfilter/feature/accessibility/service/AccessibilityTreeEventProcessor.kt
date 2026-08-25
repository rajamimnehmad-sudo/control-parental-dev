package com.contentfilter.feature.accessibility.service

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class SearchProtectionTrigger(
    val sequence: Long,
    val contextGeneration: Long,
    val packageName: String,
    val eventLabel: String,
)

internal data class SettingsEventSignals(
    val ownAppIdentityVisible: Boolean = false,
    val adminAppIdentityVisible: Boolean = false,
    val dangerousSettingsActionVisible: Boolean = false,
    val installSourceSettingsVisible: Boolean = false,
)

internal data class SettingsProtectionTrigger(
    val sequence: Long,
    val contextGeneration: Long,
    val packageName: String,
    val eventLabel: String,
    val eventType: Int,
    val className: String?,
    val eventSignals: SettingsEventSignals,
    val fallbackAttempt: Int? = null,
    val urgent: Boolean = false,
)

internal data class AccessibilityTreeEventMetrics(
    val submittedSearch: Long,
    val submittedSettings: Long,
    val startedSearch: Long,
    val startedSettings: Long,
    val coalescedSearch: Long,
    val coalescedSettings: Long,
    val staleBeforeStart: Long,
)

/**
 * Bounded single-consumer lane for expensive Accessibility tree work.
 *
 * At most one pending Search and one pending Settings request are retained. Repeated events replace
 * the pending request of the same kind instead of growing a queue. Context generations change when
 * the logical surface/package changes or when policy explicitly invalidates a result. Each result is
 * also checked against the latest submitted sequence before it may diagnose or act, so a same-package
 * navigation supersedes an older tree snapshot without permitting an old action on a new screen.
 * AccessibilityEvent/AccessibilityNodeInfo instances are never stored across the callback boundary.
 */
internal class AccessibilityTreeEventProcessor(
    scope: CoroutineScope,
    workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val processSearch: suspend (SearchProtectionTrigger) -> Unit,
    private val processSettings: suspend (SettingsProtectionTrigger) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val sequence = AtomicLong()
    private val searchContextGeneration = AtomicLong()
    private val settingsContextGeneration = AtomicLong()
    private val latestSearchSequence = AtomicLong()
    private val latestSettingsSequence = AtomicLong()
    private val submittedSearch = AtomicLong()
    private val submittedSettings = AtomicLong()
    private val startedSearch = AtomicLong()
    private val startedSettings = AtomicLong()
    private val coalescedSearch = AtomicLong()
    private val coalescedSettings = AtomicLong()
    private val staleBeforeStart = AtomicLong()
    private val pendingSearch = AtomicReference<SearchProtectionTrigger?>(null)
    private val pendingSettings = AtomicReference<SettingsProtectionTrigger?>(null)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private var lastSearchPackageName: String? = null
    private var lastSettingsPackageName: String? = null
    private var closed = false

    private val worker: Job =
        scope.launch(workerDispatcher) {
            for (@Suppress("UNUSED_VARIABLE") wakeup in wakeups) {
                while (true) {
                    var didWork = false

                    pendingSettings.getAndSet(null)?.let { trigger ->
                        didWork = true
                        if (isSettingsContextCurrent(trigger)) {
                            startedSettings.incrementAndGet()
                            processSettings(trigger)
                        } else {
                            staleBeforeStart.incrementAndGet()
                        }
                    }

                    pendingSearch.getAndSet(null)?.let { trigger ->
                        didWork = true
                        if (isSearchContextCurrent(trigger)) {
                            startedSearch.incrementAndGet()
                            processSearch(trigger)
                        } else {
                            staleBeforeStart.incrementAndGet()
                        }
                    }

                    if (!didWork) break
                }
            }
        }

    fun submitSearch(
        packageName: String,
        eventLabel: String,
        invalidateContext: Boolean = false,
    ): Long =
        synchronized(lock) {
            if (closed) return@synchronized 0L
            if (lastSettingsPackageName != null && lastSettingsPackageName != packageName) {
                invalidateSettingsContextLocked()
            }
            if (invalidateContext || lastSearchPackageName != packageName) {
                searchContextGeneration.incrementAndGet()
            }
            lastSearchPackageName = packageName
            val nextSequence = sequence.incrementAndGet()
            latestSearchSequence.set(nextSequence)
            val replaced =
                pendingSearch.getAndSet(
                    SearchProtectionTrigger(
                        sequence = nextSequence,
                        contextGeneration = searchContextGeneration.get(),
                        packageName = packageName,
                        eventLabel = eventLabel,
                    ),
                )
            if (replaced != null) coalescedSearch.incrementAndGet()
            submittedSearch.incrementAndGet()
            wakeups.trySend(Unit)
            nextSequence
        }

    fun submitSettings(
        packageName: String,
        eventLabel: String,
        eventType: Int,
        className: String?,
        eventSignals: SettingsEventSignals,
        fallbackAttempt: Int? = null,
        urgent: Boolean = false,
        invalidateContext: Boolean = false,
    ): Long =
        synchronized(lock) {
            if (closed) return@synchronized 0L
            if (lastSearchPackageName != null && lastSearchPackageName != packageName) {
                invalidateSearchContextLocked()
            }
            if (invalidateContext || lastSettingsPackageName != packageName) {
                settingsContextGeneration.incrementAndGet()
            }
            lastSettingsPackageName = packageName
            val nextSequence = sequence.incrementAndGet()
            latestSettingsSequence.set(nextSequence)
            val replaced =
                pendingSettings.getAndSet(
                    SettingsProtectionTrigger(
                        sequence = nextSequence,
                        contextGeneration = settingsContextGeneration.get(),
                        packageName = packageName,
                        eventLabel = eventLabel,
                        eventType = eventType,
                        className = className,
                        eventSignals = eventSignals,
                        fallbackAttempt = fallbackAttempt,
                        urgent = urgent,
                    ),
                )
            if (replaced != null) coalescedSettings.incrementAndGet()
            submittedSettings.incrementAndGet()
            wakeups.trySend(Unit)
            nextSequence
        }

    fun invalidateSearchContext() {
        synchronized(lock) {
            if (closed || (lastSearchPackageName == null && pendingSearch.get() == null)) return
            invalidateSearchContextLocked()
        }
    }

    fun invalidateSettingsContext() {
        synchronized(lock) {
            if (closed || (lastSettingsPackageName == null && pendingSettings.get() == null)) return
            invalidateSettingsContextLocked()
        }
    }

    fun isSearchContextCurrent(trigger: SearchProtectionTrigger): Boolean =
        synchronized(lock) {
            !closed &&
                trigger.contextGeneration == searchContextGeneration.get() &&
                trigger.packageName == lastSearchPackageName &&
                trigger.sequence == latestSearchSequence.get()
        }

    fun isSettingsContextCurrent(trigger: SettingsProtectionTrigger): Boolean =
        synchronized(lock) {
            !closed &&
                trigger.contextGeneration == settingsContextGeneration.get() &&
                trigger.packageName == lastSettingsPackageName &&
                trigger.sequence == latestSettingsSequence.get()
        }

    fun metrics(): AccessibilityTreeEventMetrics =
        AccessibilityTreeEventMetrics(
            submittedSearch = submittedSearch.get(),
            submittedSettings = submittedSettings.get(),
            startedSearch = startedSearch.get(),
            startedSettings = startedSettings.get(),
            coalescedSearch = coalescedSearch.get(),
            coalescedSettings = coalescedSettings.get(),
            staleBeforeStart = staleBeforeStart.get(),
        )

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            invalidateSearchContextLocked()
            invalidateSettingsContextLocked()
            wakeups.close()
        }
        worker.cancel()
    }

    private fun invalidateSearchContextLocked() {
        searchContextGeneration.incrementAndGet()
        lastSearchPackageName = null
        latestSearchSequence.set(0L)
        pendingSearch.set(null)
    }

    private fun invalidateSettingsContextLocked() {
        settingsContextGeneration.incrementAndGet()
        lastSettingsPackageName = null
        latestSettingsSequence.set(0L)
        pendingSettings.set(null)
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

internal data class SettingsPageObservation(
    val signals: SettingsEventSignals = SettingsEventSignals(),
    val visitedNodes: Int = 0,
    val nodeBudgetExhausted: Boolean = false,
    val timeBudgetExhausted: Boolean = false,
)

internal interface AccessibilityNodeReader {
    val viewIdResourceName: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val isFocused: Boolean
    val isClickable: Boolean
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

    override val isClickable: Boolean
        get() = runCatching { node.isClickable }.getOrDefault(false)

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
                    observation = BrowserPageObservation(host = address.host, addressBarFocused = node.isFocused)
                    if (node.isFocused) return BrowserPageScanResult(observation, visited, nodeBudgetExhausted, false)
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

/** One off-main traversal gathers every Settings signal that previously required four separate scans. */
internal class AccessibilitySettingsPageScanner(
    private val maximumNodes: Int = DefaultMaximumNodes,
    private val maximumScanNanos: Long = DefaultMaximumScanNanos,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    init {
        require(maximumNodes > 0)
        require(maximumScanNanos > 0L)
    }

    fun scan(
        root: AccessibilityNodeReader?,
        ownPackageName: String,
        ownAppLabel: String,
    ): SettingsPageObservation {
        if (root == null) return SettingsPageObservation()
        val startedAt = nanoTime()
        val pending = ArrayDeque<AccessibilityNodeReader>()
        pending.addLast(root)
        var ownVisible = false
        var adminVisible = false
        var dangerousVisible = false
        var installSourceVisible = false
        var visited = 0
        var nodeBudgetExhausted = false

        fun result(timeBudgetExhausted: Boolean = false) =
            SettingsPageObservation(
                signals =
                    SettingsEventSignals(
                        ownAppIdentityVisible = ownVisible,
                        adminAppIdentityVisible = adminVisible,
                        dangerousSettingsActionVisible = dangerousVisible,
                        installSourceSettingsVisible = installSourceVisible,
                    ),
                visitedNodes = visited,
                nodeBudgetExhausted = nodeBudgetExhausted,
                timeBudgetExhausted = timeBudgetExhausted,
            )

        while (pending.isNotEmpty()) {
            if (visited >= maximumNodes) {
                nodeBudgetExhausted = true
                return result()
            }
            if (nanoTime() - startedAt >= maximumScanNanos) return result(timeBudgetExhausted = true)

            val node = pending.removeFirst()
            visited++
            val text = node.text?.toString()
            val description = node.contentDescription?.toString()
            val viewId = node.viewIdResourceName
            val identityValues = listOf(text, description, viewId)
            ownVisible = ownVisible || identityValues.any { it.matchesOwnAppIdentity(ownPackageName, ownAppLabel) }
            adminVisible = adminVisible || identityValues.any { it.matchesAdminAppIdentity() }
            dangerousVisible =
                dangerousVisible ||
                    isDangerousSettingsAction(
                        viewId = viewId,
                        label = text ?: description,
                        clickable = node.isClickable,
                    )
            installSourceVisible =
                installSourceVisible ||
                    listOf(text, description).any { label -> isInstallSourceSettingsIndicator(viewId, label) }

            if (ownVisible && adminVisible && dangerousVisible && installSourceVisible) return result()

            val children = node.childCount
            for (index in 0 until children) {
                if (nanoTime() - startedAt >= maximumScanNanos) return result(timeBudgetExhausted = true)
                if (visited + pending.size >= maximumNodes) {
                    nodeBudgetExhausted = true
                    break
                }
                node.childAt(index)?.let(pending::addLast)
            }
        }
        return result()
    }

    private companion object {
        // Preserve the historical single-scan coverage while moving it entirely off the main thread.
        const val DefaultMaximumNodes = 200
        const val DefaultMaximumScanNanos = 100_000_000L
    }
}

internal fun SettingsEventSignals.merge(other: SettingsEventSignals): SettingsEventSignals =
    SettingsEventSignals(
        ownAppIdentityVisible = ownAppIdentityVisible || other.ownAppIdentityVisible,
        adminAppIdentityVisible = adminAppIdentityVisible || other.adminAppIdentityVisible,
        dangerousSettingsActionVisible = dangerousSettingsActionVisible || other.dangerousSettingsActionVisible,
        installSourceSettingsVisible = installSourceSettingsVisible || other.installSourceSettingsVisible,
    )

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
