package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.protectedBrowserRequired
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicySnapshotProvider
import com.contentfilter.feature.accessibility.telemetry.AccessibilityTelemetryReporter
import com.contentfilter.feature.vpn.search.SearchProtectionSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the expensive tree-based Search and Settings protection paths.
 *
 * The AccessibilityService remains responsible for lifecycle and fast event routing. This
 * coordinator owns the bounded worker lane, fresh tree scans, stale-result validation and the
 * small Main-thread actions that must happen only after a worker decision is current.
 */
internal class AccessibilityTreeProtectionCoordinator(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val clock: AccessibilityClock,
    private val snapshotProvider: AccessibilityPolicySnapshotProvider,
    private val telemetryReporter: AccessibilityTelemetryReporter,
    private val pushNotificationRepository: PushNotificationRepository,
    private val protectionStateStore: ProtectionStateStore,
) : AutoCloseable {
    private val settingsProtectionPolicy = SettingsProtectionPolicy()
    private val searchEngineScreenDetector = SearchEngineScreenDetector()
    private val webActionDebouncer = AccessibilityWebActionDebouncer()
    private val browserPageScanner = AccessibilityBrowserPageScanner()
    private val settingsPageScanner = AccessibilitySettingsPageScanner()
    private val browserCandidateCache = mutableMapOf<String, Boolean>()
    private val ownUninstallerPackages by lazy { resolveOwnUninstallerPackages() }
    private val ownAppLabel by lazy { service.applicationInfo.loadLabel(service.packageManager).toString() }
    private val processor =
        AccessibilityTreeEventProcessor(
            scope = scope,
            processSearch = ::processSearchEngineProtection,
            processSettings = ::processSettingsProtection,
        )
    private var settingsEscapeJob: Job? = null
    private var lastTamperAlertAt: Long = 0L

    /**
     * Updates mutual Search/Settings context ownership and returns whether this package needs the
     * Settings protection path.
     */
    fun observePackage(packageName: String): Boolean {
        val couldContainProtectedSettings =
            settingsProtectionPolicy.couldContainProtectedScreen(
                packageName = packageName,
                resolvedOwnUninstaller = packageName in ownUninstallerPackages,
            )
        if (couldContainProtectedSettings) {
            processor.invalidateSearchContext()
        } else {
            processor.invalidateSettingsContext()
            clearSettingsEscape()
        }
        return couldContainProtectedSettings
    }

    fun invalidateSearchContext() {
        processor.invalidateSearchContext()
    }

    fun onPolicyChanged() {
        val root = service.rootInActiveWindow ?: return
        val activePackage = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val resolvedOwnUninstaller = activePackage in ownUninstallerPackages
        when {
            settingsProtectionPolicy.couldContainProtectedScreen(activePackage, resolvedOwnUninstaller) -> {
                processor.invalidateSearchContext()
                processor.submitSettings(
                    packageName = activePackage,
                    eventLabel = PolicyChangedEventLabel,
                    eventType = 0,
                    className = root.className?.toString(),
                    eventSignals = SettingsEventSignals(),
                    invalidateContext = true,
                )
            }
            AccessibilityForegroundAllowlist.contains(activePackage) -> {
                processor.invalidateSearchContext()
                processor.invalidateSettingsContext()
            }
            else -> {
                processor.invalidateSettingsContext()
                submitSearch(
                    packageName = activePackage,
                    eventLabel = PolicyChangedEventLabel,
                    invalidateContext = true,
                )
            }
        }
    }

    fun handleSettingsEvent(
        event: AccessibilityEvent,
        packageName: String,
        className: String?,
        elapsedRealtimeMillis: Long,
        nowEpochMillis: Long,
    ): Boolean {
        val resolvedOwnUninstaller = packageName in ownUninstallerPackages
        val eventSignals = settingsSignalsFromEvent(event)
        val urgent =
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                settingsProtectionPolicy.requiresImmediateEscape(
                    packageName = packageName,
                    className = className,
                    ownAppIdentityVisible = eventSignals.ownAppIdentityVisible,
                    dangerousSettingsActionVisible = eventSignals.dangerousSettingsActionVisible,
                )
        val identityIndependent =
            settingsProtectionPolicy.canBlockImmediatelyWithoutTreeIdentity(packageName, className)
        if (
            urgent &&
            identityIndependent &&
            shouldLeaveSettingsScreen(
                packageName = packageName,
                className = className,
                signals = eventSignals,
                resolvedOwnUninstaller = resolvedOwnUninstaller,
                elapsedRealtimeMillis = elapsedRealtimeMillis,
                nowEpochMillis = nowEpochMillis,
            )
        ) {
            performInitialSettingsProtection(packageName, urgent = true, elapsedRealtimeMillis)
            return true
        }

        processor.submitSettings(
            packageName = packageName,
            eventLabel = AccessibilityEventFilter.label(event.eventType),
            eventType = event.eventType,
            className = className,
            eventSignals = eventSignals,
        )
        return false
    }

    fun submitSearch(
        packageName: String,
        eventLabel: String,
        invalidateContext: Boolean = false,
    ) {
        processor.submitSearch(packageName, eventLabel, invalidateContext)
    }

    override fun close() {
        processor.close()
        webActionDebouncer.clear()
        clearSettingsEscape()
    }

    private fun settingsSignalsFromEvent(event: AccessibilityEvent): SettingsEventSignals {
        val values = event.text.map(CharSequence::toString) + listOfNotNull(event.contentDescription?.toString())
        val ownPackageName = service.applicationContext.packageName
        return SettingsEventSignals(
            ownAppIdentityVisible = values.any { it.matchesOwnAppIdentity(ownPackageName, ownAppLabel) },
            adminAppIdentityVisible = values.any { it.matchesAdminAppIdentity() },
            dangerousSettingsActionVisible =
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    values.any { isDangerousSettingsAction(viewId = null, label = it, clickable = true) },
            installSourceSettingsVisible = values.any { isInstallSourceSettingsIndicator(viewId = null, label = it) },
        )
    }

    private fun shouldLeaveSettingsScreen(
        packageName: String,
        className: String?,
        signals: SettingsEventSignals,
        resolvedOwnUninstaller: Boolean,
        elapsedRealtimeMillis: Long,
        nowEpochMillis: Long,
    ): Boolean =
        settingsProtectionPolicy.shouldLeaveProtectedScreen(
            packageName = packageName,
            className = className,
            ownAppIdentityVisible = signals.ownAppIdentityVisible,
            adminAppIdentityVisible = signals.adminAppIdentityVisible,
            resolvedOwnUninstaller = resolvedOwnUninstaller,
            dangerousSettingsActionVisible = signals.dangerousSettingsActionVisible,
            installSourceSettingsVisible = signals.installSourceSettingsVisible,
            deviceAdminEnabled = DeviceAdminController.isEnabled(service),
            armed = protectionStateStore.isArmed(),
            settingsAuthorized =
                protectionStateStore.isAuthorized(
                    ProtectionAuthorizationScope.Settings,
                    nowEpochMillis,
                ),
            removalAuthorized = protectionStateStore.isAuthorized(ProtectionAuthorizationScope.Removal, nowEpochMillis),
            trustedInstallAuthorized = protectionStateStore.isTrustedInstallAuthorized(nowEpochMillis),
            elapsedRealtimeMillis = elapsedRealtimeMillis,
        )

    private fun performInitialSettingsProtection(
        packageName: String,
        urgent: Boolean,
        elapsedRealtimeMillis: Long,
    ) {
        val fallbackAlreadyActive = settingsEscapeJob?.isActive == true
        if (!urgent && fallbackAlreadyActive) return
        performSettingsEscapeAction(SettingsEscapeStrategy.actionForAttempt(attempt = 0, urgent = urgent))
        if (!fallbackAlreadyActive) scheduleSettingsEscapeFallback(packageName, urgent)
        reportSettingsProtection(elapsedRealtimeMillis)
    }

    private fun scheduleSettingsEscapeFallback(
        packageName: String,
        urgent: Boolean,
    ) {
        scope.launch(Dispatchers.Main.immediate) {
            if (settingsEscapeJob?.isActive == true) return@launch
            settingsEscapeJob =
                launch {
                    for (attempt in 1..SettingsEscapeFallbackAttempt) {
                        delay(SettingsEscapeRecheckDelayMillis)
                        processor.submitSettings(
                            packageName = packageName,
                            eventLabel = SettingsRecheckEventLabel,
                            eventType = 0,
                            className = null,
                            eventSignals = SettingsEventSignals(),
                            fallbackAttempt = attempt,
                            urgent = urgent,
                        )
                    }
                    settingsEscapeJob = null
                }
        }
    }

    private fun reportSettingsProtection(elapsedRealtimeMillis: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            Toast.makeText(service, "Este ajuste está protegido", Toast.LENGTH_SHORT).show()
            val shouldAlert =
                lastTamperAlertAt == 0L ||
                    elapsedRealtimeMillis - lastTamperAlertAt >= TamperAlertDebounceMillis
            if (shouldAlert) lastTamperAlertAt = elapsedRealtimeMillis
            scope.launch {
                telemetryReporter.recordSettingsProtection()
                if (shouldAlert) {
                    pushNotificationRepository.reportProtectionAlert(ProtectionAlertType.TamperAttempt)
                }
            }
        }
    }

    private fun performSettingsEscapeAction(action: SettingsEscapeAction) {
        when (action) {
            SettingsEscapeAction.Back -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            SettingsEscapeAction.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun clearSettingsEscape() {
        settingsEscapeJob?.cancel()
        settingsEscapeJob = null
    }

    @Suppress("DEPRECATION")
    private fun resolveOwnUninstallerPackages(): Set<String> =
        listOf(Intent.ACTION_DELETE, Intent.ACTION_UNINSTALL_PACKAGE)
            .flatMap { action ->
                service.packageManager.queryIntentActivities(
                    Intent(action, Uri.parse("package:${service.packageName}")),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )
            }.mapNotNull { it.activityInfo?.packageName }
            .toSet()

    private suspend fun processSearchEngineProtection(trigger: SearchProtectionTrigger) {
        if (!processor.isSearchContextCurrent(trigger)) return
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != trigger.packageName) return
        val scan = browserPageScanner.scan(AndroidAccessibilityNodeReader(root))
        if (!processor.isSearchContextCurrent(trigger)) return

        val page = scan.observation
        val snapshot = snapshotProvider.current().snapshot
        val recentSearchEngine =
            SearchProtectionSignals
                .recentSearchEngine()
                ?.takeIf { it.policyRevision == snapshot.version }
        val diagnosis =
            searchEngineScreenDetector.diagnose(
                packageName = trigger.packageName,
                snapshot = snapshot,
                currentHost = page.host,
                addressBarFocused = page.addressBarFocused,
                recentSearchEngineId = recentSearchEngine?.engineId,
                browserCandidate = isBrowserCandidate(trigger.packageName),
                elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            )
        val processorMetrics = processor.metrics()
        Log.i(
            LogTag,
            "Search protection layer=accessibility event=${trigger.eventLabel} sequence=${trigger.sequence} " +
                "package=${trigger.packageName} policyVersion=${snapshot.version} " +
                "webNavigationBlocked=${diagnosis.webNavigationBlocked} " +
                "externalSearchResultsAllowed=${snapshot.rules.externalSearchResultsAllowed()} " +
                "protectedBrowserRequired=${snapshot.rules.protectedBrowserRequired()} " +
                "safeSearch=${snapshot.rules.safeSearchEnabled()} " +
                "searchEngine=${diagnosis.searchEngineId ?: "none"} " +
                "action=${diagnosis.action} reason=${diagnosis.reason} " +
                "scanNodes=${scan.visitedNodes} nodeBudget=${scan.nodeBudgetExhausted} " +
                "timeBudget=${scan.timeBudgetExhausted} " +
                "submitted=${processorMetrics.submittedSearch} started=${processorMetrics.startedSearch} " +
                "coalesced=${processorMetrics.coalescedSearch}",
        )
        scope.launch {
            telemetryReporter.recordSearchProtection(
                eventLabel = trigger.eventLabel,
                packageName = trigger.packageName,
                packageCategory = diagnosis.packageCategory,
                reason = diagnosis.reason,
                searchEngineId = diagnosis.searchEngineId,
                action = diagnosis.action.name,
                policyRevision = diagnosis.policyRevision,
            )
        }
        if (diagnosis.action == SearchNavigationAction.Allow) return
        if (!processor.isSearchContextCurrent(trigger)) return

        var actionApplied = false
        withContext(Dispatchers.Main.immediate) {
            if (!processor.isSearchContextCurrent(trigger)) return@withContext
            val activePackageName = service.rootInActiveWindow?.packageName?.toString()
            val currentPolicyRevision = snapshotProvider.current().snapshot.version
            if (activePackageName != trigger.packageName || currentPolicyRevision != diagnosis.policyRevision) {
                Log.i(
                    LogTag,
                    "Search protection action stale package=${trigger.packageName} policyVersion=${diagnosis.policyRevision}",
                )
                return@withContext
            }
            if (
                !webActionDebouncer.shouldPerform(
                    packageName = trigger.packageName,
                    host = page.host,
                    policyRevision = diagnosis.policyRevision,
                    action = diagnosis.action,
                    elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
                )
            ) {
                Log.i(
                    LogTag,
                    "Search protection action suppressed package=${trigger.packageName} " +
                        "policyVersion=${diagnosis.policyRevision} action=${diagnosis.action}",
                )
                return@withContext
            }
            when (diagnosis.action) {
                SearchNavigationAction.Allow -> Unit
                SearchNavigationAction.GoBack -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                SearchNavigationAction.GoHome -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            actionApplied = true
        }
        if (actionApplied) {
            scope.launch {
                telemetryReporter.recordServiceState(
                    "Search protection action=${diagnosis.action} reason=${diagnosis.reason}.",
                )
            }
        }
    }

    private suspend fun processSettingsProtection(trigger: SettingsProtectionTrigger) {
        if (!processor.isSettingsContextCurrent(trigger)) return
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != trigger.packageName) return
        val observedClassName = root.className?.toString() ?: trigger.className
        val scan =
            settingsPageScanner.scan(
                root = AndroidAccessibilityNodeReader(root),
                ownPackageName = service.applicationContext.packageName,
                ownAppLabel = ownAppLabel,
            )
        if (!processor.isSettingsContextCurrent(trigger)) return
        val signals = trigger.eventSignals.merge(scan.signals)
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        val resolvedOwnUninstaller = trigger.packageName in ownUninstallerPackages
        if (
            !shouldLeaveSettingsScreen(
                packageName = trigger.packageName,
                className = observedClassName,
                signals = signals,
                resolvedOwnUninstaller = resolvedOwnUninstaller,
                elapsedRealtimeMillis = elapsed,
                nowEpochMillis = now,
            )
        ) {
            return
        }
        val urgent =
            trigger.urgent ||
                trigger.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                settingsProtectionPolicy.requiresImmediateEscape(
                    packageName = trigger.packageName,
                    className = observedClassName,
                    ownAppIdentityVisible = signals.ownAppIdentityVisible,
                    dangerousSettingsActionVisible = signals.dangerousSettingsActionVisible,
                )
        var actionApplied = false
        withContext(Dispatchers.Main.immediate) {
            if (!processor.isSettingsContextCurrent(trigger)) return@withContext
            val currentRoot = service.rootInActiveWindow ?: return@withContext
            val currentPackageName = currentRoot.packageName?.toString()
            val currentClassName = currentRoot.className?.toString()
            if (
                currentPackageName != trigger.packageName ||
                (observedClassName != null && currentClassName != observedClassName)
            ) {
                return@withContext
            }
            val currentElapsed = clock.elapsedRealtimeMillis()
            val currentNow = clock.nowEpochMillis()
            if (
                !shouldLeaveSettingsScreen(
                    packageName = trigger.packageName,
                    className = currentClassName ?: observedClassName,
                    signals = signals,
                    resolvedOwnUninstaller = resolvedOwnUninstaller,
                    elapsedRealtimeMillis = currentElapsed,
                    nowEpochMillis = currentNow,
                )
            ) {
                return@withContext
            }
            performSettingsEscapeAction(
                SettingsEscapeStrategy.actionForAttempt(
                    attempt = trigger.fallbackAttempt ?: 0,
                    urgent = urgent,
                ),
            )
            actionApplied = true
        }
        if (!actionApplied || trigger.fallbackAttempt != null) return
        scheduleSettingsEscapeFallback(trigger.packageName, urgent)
        reportSettingsProtection(elapsed)
        val processorMetrics = processor.metrics()
        Log.i(
            LogTag,
            "Settings protection event=${trigger.eventLabel} sequence=${trigger.sequence} " +
                "package=${trigger.packageName} scanNodes=${scan.visitedNodes} " +
                "nodeBudget=${scan.nodeBudgetExhausted} timeBudget=${scan.timeBudgetExhausted} " +
                "submitted=${processorMetrics.submittedSettings} started=${processorMetrics.startedSettings} " +
                "coalesced=${processorMetrics.coalescedSettings}",
        )
    }

    private fun isBrowserCandidate(packageName: String): Boolean {
        if (SearchEngineScreenDetector.isBrowserPackage(packageName)) return true
        return synchronized(browserCandidateCache) {
            browserCandidateCache.getOrPut(packageName) {
                runCatching {
                    service.packageManager
                        .queryIntentActivities(
                            Intent(Intent.ACTION_VIEW, Uri.parse(BrowserProbeUrl))
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                        ).any { it.activityInfo?.packageName == packageName }
                }.getOrDefault(false)
            }
        }
    }

    private companion object {
        const val BrowserProbeUrl = "https://www.example.com/"
        const val SettingsEscapeRecheckDelayMillis = 100L
        const val SettingsEscapeFallbackAttempt = 3
        const val SettingsRecheckEventLabel = "SETTINGS_RECHECK"
        const val PolicyChangedEventLabel = "POLICY_CHANGED"
        const val TamperAlertDebounceMillis = 5 * 60_000L
        const val LogTag = "ProtectorAccessibility"
    }
}
