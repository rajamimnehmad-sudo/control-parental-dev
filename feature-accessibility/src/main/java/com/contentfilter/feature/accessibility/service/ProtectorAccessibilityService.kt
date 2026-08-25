package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.protectedBrowserRequired
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.InstallApprovalStore
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualController
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualProbeController
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicySnapshotProvider
import com.contentfilter.feature.accessibility.telemetry.AccessibilityTelemetryReporter
import com.contentfilter.feature.accessibility.time.AppUsageTracker
import com.contentfilter.feature.accessibility.time.UsageTransition
import com.contentfilter.feature.vpn.search.SearchProtectionSignals
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ProtectorAccessibilityService : AccessibilityService() {
    @Inject lateinit var clock: AccessibilityClock

    @Inject lateinit var policyEvaluator: AccessibilityAppPolicyEvaluator

    @Inject lateinit var snapshotProvider: AccessibilityPolicySnapshotProvider

    @Inject lateinit var deviceActivationRepository: DeviceActivationRepository

    @Inject lateinit var systemStatusRepository: SystemStatusRepository

    @Inject lateinit var telemetryReporter: AccessibilityTelemetryReporter

    @Inject lateinit var pushNotificationRepository: PushNotificationRepository

    @Inject lateinit var protectionStateStore: ProtectionStateStore

    @Inject lateinit var usageSessionRepository: UsageSessionRepository

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var installApprovalStore: InstallApprovalStore

    private val usageTracker = AppUsageTracker()
    private val settingsProtectionPolicy = SettingsProtectionPolicy()
    private val searchEngineScreenDetector = SearchEngineScreenDetector()
    private val webActionDebouncer = AccessibilityWebActionDebouncer()
    private val explicitSearchClassifier = ExplicitSearchClassifier()
    private val browserPageScanner = AccessibilityBrowserPageScanner()
    private val settingsPageScanner = AccessibilitySettingsPageScanner()
    private val browserCandidateCache = mutableMapOf<String, Boolean>()
    private var serviceScope: CoroutineScope? = null
    private var treeEventProcessor: AccessibilityTreeEventProcessor? = null
    private var extraTimeExpiryJob: Job? = null
    private var extraTimeExpiryPackageName: String? = null
    private var extraTimeExpiryAtEpochMillis: Long? = null
    private var foregroundWatchJob: Job? = null
    private var foregroundWatchPackageName: String? = null
    private var appLimitDeadlineJob: Job? = null
    private var appLimitDeadlinePackageName: String? = null
    private var blockRetryJob: Job? = null
    private var blockRetryPackageName: String? = null
    private var settingsEscapeJob: Job? = null
    private var lastExplicitSearchNoticeAt: Long = 0L
    private var lastTamperAlertAt: Long = 0L
    private val foregroundDecisionDiagnosticGate = ForegroundDecisionDiagnosticGate()
    private val ownUninstallerPackages by lazy { resolveOwnUninstallerPackages() }
    private val ownAppLabel by lazy { applicationInfo.loadLabel(packageManager).toString() }
    private var chromeVisualProbeController: ChromeVisualProbeController? = null
    private var chromeVisualController: ChromeVisualController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ChromePhotosDataPlaneRuntimeAttestation.markAccessibilityBound(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
        chromeVisualProbeController = ChromeVisualProbeController(this, scope)
        chromeVisualController = ChromeVisualController(this, scope)
        treeEventProcessor =
            AccessibilityTreeEventProcessor(
                scope = scope,
                processSearch = ::processSearchEngineProtection,
                processSettings = ::processSettingsProtection,
            )
        scope.launch {
            syncScheduler.requestSync()
            snapshotProvider.refresh()
            snapshotProvider.start(scope)
            launch {
                snapshotProvider.observe().collect {
                    schedulePolicyChangedProtection()
                }
            }
            systemStatusRepository.updateAccessibilityState(ComponentState.Enabled)
            telemetryReporter.recordServiceState("Accessibility service connected.")
        }
    }

    private fun schedulePolicyChangedProtection() {
        val root = rootInActiveWindow ?: return
        val activePackage = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val resolvedOwnUninstaller = activePackage in ownUninstallerPackages
        when {
            settingsProtectionPolicy.couldContainProtectedScreen(activePackage, resolvedOwnUninstaller) -> {
                treeEventProcessor?.invalidateSearchContext()
                treeEventProcessor?.submitSettings(
                    packageName = activePackage,
                    eventLabel = PolicyChangedEventLabel,
                    eventType = 0,
                    className = root.className?.toString(),
                    eventSignals = SettingsEventSignals(),
                    invalidateContext = true,
                )
            }
            AccessibilityForegroundAllowlist.contains(activePackage) -> {
                treeEventProcessor?.invalidateSearchContext()
                treeEventProcessor?.invalidateSettingsContext()
            }
            else -> {
                treeEventProcessor?.invalidateSettingsContext()
                scheduleSearchEngineProtection(
                    packageName = activePackage,
                    eventLabel = PolicyChangedEventLabel,
                    invalidateContext = true,
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackageName = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        val protectedChromeVisualOnly =
            AccessibilityEventFilter.isProtectedChromeVisualOnly(
                eventType = event.eventType,
                packageName = eventPackageName,
                protectedSessionActive = ChromePhotosDataPlaneRuntimeAttestation.snapshot().sessionId.isNotBlank(),
            )
        if (AccessibilityEventFilter.isChromeVisualOnly(event.eventType) || protectedChromeVisualOnly) {
            chromeVisualProbeController?.onAccessibilityEvent(event)
            chromeVisualController?.onAccessibilityEvent(event)
            return
        }
        if (!AccessibilityEventFilter.isHandled(event.eventType)) return
        chromeVisualProbeController?.onAccessibilityEvent(event)
        chromeVisualController?.onAccessibilityEvent(event)
        val packageName = eventPackageName ?: return
        val resolvedOwnUninstaller = packageName in ownUninstallerPackages
        val couldContainProtectedSettings =
            settingsProtectionPolicy.couldContainProtectedScreen(packageName, resolvedOwnUninstaller)
        if (couldContainProtectedSettings) {
            treeEventProcessor?.invalidateSearchContext()
        } else {
            treeEventProcessor?.invalidateSettingsContext()
            clearSettingsEscape()
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && !couldContainProtectedSettings) return
        if (blockExplicitSearchIfNeeded(event, packageName)) return
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        if (
            couldContainProtectedSettings &&
            handleSettingsProtection(
                event = event,
                packageName = packageName,
                className = event.className?.toString(),
                resolvedOwnUninstaller = resolvedOwnUninstaller,
                elapsedRealtimeMillis = elapsed,
                nowEpochMillis = now,
            )
        ) {
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) return
        if (AccessibilityForegroundAllowlist.contains(packageName)) {
            treeEventProcessor?.invalidateSearchContext()
            handleAlwaysAllowedForeground(packageName, elapsed, now)
            return
        }
        if (blockRetryPackageName != packageName) clearBlockRetry()
        if (!couldContainProtectedSettings) {
            scheduleSearchEngineProtection(packageName, AccessibilityEventFilter.label(event.eventType))
        }

        serviceScope?.launch { systemStatusRepository.updateAccessibilityState(ComponentState.Enabled) }
        serviceScope?.let { scope ->
            if (snapshotProvider.ensureCurrentDay(scope)) {
                usageTracker.finishCurrent(elapsed, now)?.let { transition ->
                    scope.launch { saveTransition(transition) }
                }
                usageTracker.reset()
            }
        }
        val transition = usageTracker.onForegroundApp(packageName, elapsed, now)
        if (transition != null) {
            serviceScope?.launch { saveTransition(transition) }
        }
        val blocked = evaluateForegroundApp(packageName, elapsed)
        if (!blocked) startForegroundWatch(packageName)
        if (transition == null) {
            usageTracker.checkpointCurrent(elapsed, now, CheckpointIntervalMillis)?.let { checkpoint ->
                serviceScope?.launch { saveTransition(checkpoint) }
            }
        }
    }

    private fun handleAlwaysAllowedForeground(
        packageName: String,
        elapsedRealtimeMillis: Long,
        epochMillis: Long,
    ) {
        clearBlockRetry()
        clearForegroundWatch()
        clearAppLimitDeadline()
        clearExtraTimeExpiry()
        if (usageTracker.currentPackageName() == null || usageTracker.currentPackageName() == packageName) return
        usageTracker.finishCurrent(elapsedRealtimeMillis, epochMillis)?.let { transition ->
            serviceScope?.launch { saveTransition(transition) }
        }
    }

    private fun blockExplicitSearchIfNeeded(
        event: AccessibilityEvent,
        packageName: String,
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false
        if (packageName !in ExplicitSearchPackages) return false
        val source = event.source ?: return false
        val recognized =
            runCatching { source.isEditable && source.isRecognizedSearchField(packageName) }
                .getOrDefault(false)
        if (!recognized) return false
        val query = runCatching { source.text?.takeIf { it.isNotBlank() } }.getOrNull() ?: return false
        if (explicitSearchClassifier.classify(query) != ExplicitSearchDecision.BlockExplicit) return false
        runCatching {
            source.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                },
            )
        }
        val elapsed = clock.elapsedRealtimeMillis()
        if (lastExplicitSearchNoticeAt == 0L || elapsed - lastExplicitSearchNoticeAt >= ExplicitSearchNoticeDebounceMillis) {
            lastExplicitSearchNoticeAt = elapsed
            Toast.makeText(this, "Esta búsqueda está bloqueada", Toast.LENGTH_SHORT).show()
        }
        Log.i(LogTag, "Explicit search decision=block package=$packageName mechanism=local-classifier")
        return true
    }

    private fun AccessibilityNodeInfo.isRecognizedSearchField(packageName: String): Boolean {
        val viewId = viewIdResourceName?.lowercase().orEmpty()
        if (packageName == GoogleSearchPackage) {
            return "search" in viewId || "query" in viewId || className?.toString()?.contains("EditText") == true
        }
        return AccessibilityAddressBarViewIdParts.any { viewId.endsWith("/id/$it") || viewId.endsWith(":id/$it") } ||
            "search" in viewId
    }

    override fun onInterrupt() {
        serviceScope?.launch { telemetryReporter.recordServiceState("Accessibility service interrupted.") }
    }

    override fun onDestroy() {
        ChromePhotosDataPlaneRuntimeAttestation.markAccessibilityBound(false)
        chromeVisualProbeController?.close()
        chromeVisualProbeController = null
        chromeVisualController?.close()
        chromeVisualController = null
        treeEventProcessor?.close()
        treeEventProcessor = null
        webActionDebouncer.clear()
        clearSettingsEscape()
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        val transition = usageTracker.finishCurrent(elapsed, now)
        val scope = serviceScope
        if (scope != null) {
            scope.launch {
                transition?.let { saveTransition(it) }
                systemStatusRepository.updateAccessibilityState(ComponentState.Disabled)
                telemetryReporter.recordServiceState(DeviceProtectionAlert.AppsDisabled)
                pushNotificationRepository.reportProtectionAlert(ProtectionAlertType.AppsDisabled)
                snapshotProvider.stop()
                clearExtraTimeExpiry()
                clearAppLimitDeadline()
                clearForegroundWatch()
                clearBlockRetry()
                cancel()
            }
        } else {
            snapshotProvider.stop()
            clearExtraTimeExpiry()
            clearForegroundWatch()
            clearBlockRetry()
        }
        serviceScope = null
        super.onDestroy()
    }

    private fun handleSettingsProtection(
        event: AccessibilityEvent,
        packageName: String,
        className: String?,
        resolvedOwnUninstaller: Boolean,
        elapsedRealtimeMillis: Long,
        nowEpochMillis: Long,
    ): Boolean {
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

        treeEventProcessor?.submitSettings(
            packageName = packageName,
            eventLabel = AccessibilityEventFilter.label(event.eventType),
            eventType = event.eventType,
            className = className,
            eventSignals = eventSignals,
        )
        return false
    }

    private fun settingsSignalsFromEvent(event: AccessibilityEvent): SettingsEventSignals {
        val values = event.text.map(CharSequence::toString) + listOfNotNull(event.contentDescription?.toString())
        val ownPackageName = applicationContext.packageName
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
            deviceAdminEnabled = DeviceAdminController.isEnabled(this),
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
        val scope = serviceScope ?: return
        scope.launch(Dispatchers.Main.immediate) {
            if (settingsEscapeJob?.isActive == true) return@launch
            settingsEscapeJob =
                launch {
                    for (attempt in 1..SettingsEscapeFallbackAttempt) {
                        delay(SettingsEscapeRecheckDelayMillis)
                        treeEventProcessor?.submitSettings(
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
        val scope = serviceScope ?: return
        scope.launch(Dispatchers.Main.immediate) {
            Toast.makeText(this@ProtectorAccessibilityService, "Este ajuste está protegido", Toast.LENGTH_SHORT).show()
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
            SettingsEscapeAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            SettingsEscapeAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
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
                packageManager.queryIntentActivities(
                    Intent(action, Uri.parse("package:$packageName")),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )
            }.mapNotNull { it.activityInfo?.packageName }
            .toSet()

    private fun scheduleSearchEngineProtection(
        packageName: String,
        eventLabel: String,
        invalidateContext: Boolean = false,
    ) {
        treeEventProcessor?.submitSearch(packageName, eventLabel, invalidateContext)
    }

    private suspend fun processSearchEngineProtection(trigger: SearchProtectionTrigger) {
        val processor = treeEventProcessor ?: return
        if (!processor.isSearchContextCurrent(trigger)) return
        val root = rootInActiveWindow ?: return
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
        serviceScope?.launch {
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
            val activePackageName = rootInActiveWindow?.packageName?.toString()
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
                SearchNavigationAction.GoBack -> performGlobalAction(GLOBAL_ACTION_BACK)
                SearchNavigationAction.GoHome -> performGlobalAction(GLOBAL_ACTION_HOME)
            }
            actionApplied = true
        }
        if (actionApplied) {
            serviceScope?.launch {
                telemetryReporter.recordServiceState(
                    "Search protection action=${diagnosis.action} reason=${diagnosis.reason}.",
                )
            }
        }
    }

    private suspend fun processSettingsProtection(trigger: SettingsProtectionTrigger) {
        val processor = treeEventProcessor ?: return
        if (!processor.isSettingsContextCurrent(trigger)) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != trigger.packageName) return
        val observedClassName = root.className?.toString() ?: trigger.className
        val scan =
            settingsPageScanner.scan(
                root = AndroidAccessibilityNodeReader(root),
                ownPackageName = applicationContext.packageName,
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
            val currentRoot = rootInActiveWindow ?: return@withContext
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
                    packageManager
                        .queryIntentActivities(
                            Intent(Intent.ACTION_VIEW, Uri.parse(BrowserProbeUrl))
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                        ).any { it.activityInfo?.packageName == packageName }
                }.getOrDefault(false)
            }
        }
    }

    private fun evaluateForegroundApp(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        detectUnknownApp(packageName)
        val state = snapshotProvider.current()
        if (installApprovalStore.isPending(packageName)) {
            if (hasExplicitAppApproval(packageName, state.snapshot.rules)) {
                installApprovalStore.markApproved(packageName)
            } else {
                val decision = PolicyDecision.RequestAuthorization(packageName)
                serviceScope?.launch { telemetryReporter.recordDecision(packageName, decision) }
                Toast.makeText(this, "Esta app espera aprobación del administrador", Toast.LENGTH_SHORT).show()
                leaveBlockedApp(packageName)
                return true
            }
        }
        val persistedMinutes =
            state.snapshot.dailyUsage
                .firstOrNull { it.packageName == packageName }
                ?.usedMinutes ?: 0
        val activeMillis = usageTracker.activeMillisForPackage(packageName, elapsedRealtimeMillis)
        val usedMinutes = persistedMinutes + activeMillis.toObservedMinutes()
        scheduleAppLimitDeadline(
            packageName = packageName,
            persistedMinutes = persistedMinutes,
            activeMillis = activeMillis,
            limitMinutes =
                state.snapshot.dailyLimits
                    .filter { it.enabled && it.targetType == PolicyTargetType.App && it.target == packageName }
                    .minOfOrNull { it.limitMinutes },
        )
        val decision = policyEvaluator.evaluate(packageName, usedMinutes, state.snapshot, state.health)
        recordForegroundDecisionIfChanged(
            packageName = packageName,
            persistedMinutes = persistedMinutes,
            activeMillis = activeMillis,
            usedMinutes = usedMinutes,
            limitCount = state.snapshot.dailyLimits.size,
            ruleCount = state.snapshot.rules.size,
            decision = decision,
        )
        when (decision) {
            is PolicyDecision.Allow -> clearExtraTimeExpiry()
            is PolicyDecision.Block,
            is PolicyDecision.RequestAuthorization,
            -> {
                clearExtraTimeExpiry()
                clearAppLimitDeadline()
                leaveBlockedApp(packageName)
                return true
            }
            is PolicyDecision.Warn,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate,
            is PolicyDecision.HealthWarning,
            -> clearExtraTimeExpiry()
            is PolicyDecision.GrantExtraTime -> scheduleExtraTimeExpiry(packageName, decision.validUntilEpochMillis)
        }
        return false
    }

    private fun detectUnknownApp(packageName: String) {
        if (installApprovalStore.isKnown(packageName)) return
        val applicationInfo =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
            }.getOrNull() ?: return
        val isSystemApp =
            applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (
            isSystemApp ||
            packageName == this.packageName ||
            packageName.startsWith("com.contentfilter.admin")
        ) {
            return
        }
        installApprovalStore.markPending(packageName)
        Log.i(LogTag, "Unknown foreground app detected package=$packageName")
        sendBroadcast(
            Intent(InstallApprovalStore.ACTION_UNKNOWN_APP_DETECTED)
                .setPackage(this.packageName)
                .setData(Uri.parse("package:$packageName")),
        )
    }

    private fun recordForegroundDecisionIfChanged(
        packageName: String,
        persistedMinutes: Int,
        activeMillis: Long,
        usedMinutes: Int,
        limitCount: Int,
        ruleCount: Int,
        decision: PolicyDecision,
    ) {
        val key = "$packageName|${decision.label()}|$usedMinutes|$limitCount|$ruleCount"
        if (!foregroundDecisionDiagnosticGate.shouldRecord(key)) return
        Log.i(
            LogTag,
            "Evaluated app package=$packageName persistedMin=$persistedMinutes activeMs=$activeMillis " +
                "usedMin=$usedMinutes limits=$limitCount rules=$ruleCount decision=${decision.label()}",
        )
        serviceScope?.launch { telemetryReporter.recordDecision(packageName, decision) }
    }

    private fun leaveBlockedApp(packageName: String) {
        if (blockRetryPackageName == packageName && blockRetryJob?.isActive == true) return
        Log.i(LogTag, "Blocking foreground app immediately package=$packageName")
        clearBlockRetry()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val scope = serviceScope ?: return
        blockRetryPackageName = packageName
        blockRetryJob =
            scope.launch {
                repeat(BlockHomeRetries) {
                    delay(BlockRecheckDelayMillis)
                    if (!packageName.isActiveBlockedForeground()) return@launch
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
    }

    private fun clearBlockRetry() {
        blockRetryJob?.cancel()
        blockRetryJob = null
        blockRetryPackageName = null
    }

    private fun String.isActiveBlockedForeground(): Boolean {
        val activeWindowPackage = rootInActiveWindow?.packageName?.toString() ?: return false
        return activeWindowPackage == this && isStillBlocked()
    }

    private fun String.isStillBlocked(): Boolean {
        val elapsed = clock.elapsedRealtimeMillis()
        val state = snapshotProvider.current()
        if (
            installApprovalStore.isPending(this) &&
            !hasExplicitAppApproval(this, state.snapshot.rules)
        ) {
            return true
        }
        val persistedMinutes =
            state.snapshot.dailyUsage
                .firstOrNull { it.packageName == this }
                ?.usedMinutes ?: 0
        val activeMillis = usageTracker.activeMillisForPackage(this, elapsed)
        val usedMinutes = persistedMinutes + activeMillis.toObservedMinutes()
        return when (policyEvaluator.evaluate(this, usedMinutes, state.snapshot, state.health)) {
            is PolicyDecision.Block,
            is PolicyDecision.RequestAuthorization,
            -> true
            else -> false
        }
    }

    private fun startForegroundWatch(packageName: String) {
        if (foregroundWatchJob?.isActive == true && foregroundWatchPackageName == packageName) return
        clearForegroundWatch()
        val scope = serviceScope ?: return
        foregroundWatchPackageName = packageName
        foregroundWatchJob =
            scope.launch {
                if (!packageName.isReportedForeground()) return@launch
                if (evaluateForegroundApp(packageName, clock.elapsedRealtimeMillis())) return@launch
                while (usageTracker.currentPackageName() == packageName) {
                    delay(ForegroundRecheckMillis)
                    if (!packageName.isReportedForeground()) return@launch
                    val elapsed = clock.elapsedRealtimeMillis()
                    val now = clock.nowEpochMillis()
                    usageTracker.checkpointCurrent(elapsed, now, CheckpointIntervalMillis)?.let { checkpoint ->
                        saveTransition(checkpoint)
                    }
                    if (evaluateForegroundApp(packageName, elapsed)) return@launch
                }
            }
    }

    private fun String.isReportedForeground(): Boolean {
        val activeWindowPackage = rootInActiveWindow?.packageName?.toString()
        if (activeWindowPackage != null && activeWindowPackage != this) return false
        return usageTracker.currentPackageName() == this
    }

    private fun clearForegroundWatch() {
        foregroundWatchJob?.cancel()
        foregroundWatchJob = null
        foregroundWatchPackageName = null
    }

    private fun scheduleAppLimitDeadline(
        packageName: String,
        persistedMinutes: Int,
        activeMillis: Long,
        limitMinutes: Int?,
    ) {
        if (limitMinutes == null) {
            clearAppLimitDeadline()
            return
        }
        if (appLimitDeadlineJob?.isActive == true && appLimitDeadlinePackageName == packageName) return
        clearAppLimitDeadline()
        val remainingMillis =
            (limitMinutes * MillisPerMinute - persistedMinutes * MillisPerMinute - activeMillis)
                .coerceAtMost(MaxDeadlineDelayMillis)
                .coerceAtLeast(0L)
        val scope = serviceScope ?: return
        appLimitDeadlinePackageName = packageName
        appLimitDeadlineJob =
            scope.launch {
                delay(remainingMillis)
                if (usageTracker.currentPackageName() == packageName) {
                    usageTracker.checkpointCurrent(
                        elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
                        epochMillis = clock.nowEpochMillis(),
                        minimumDurationMillis = 0L,
                    )?.let { saveTransition(it) }
                    snapshotProvider.refresh()
                    evaluateForegroundApp(packageName, clock.elapsedRealtimeMillis())
                }
            }
    }

    private fun clearAppLimitDeadline() {
        appLimitDeadlineJob?.cancel()
        appLimitDeadlineJob = null
        appLimitDeadlinePackageName = null
    }

    private fun scheduleExtraTimeExpiry(
        packageName: String,
        validUntilEpochMillis: Long,
    ) {
        if (
            extraTimeExpiryJob?.isActive == true &&
            extraTimeExpiryPackageName == packageName &&
            extraTimeExpiryAtEpochMillis == validUntilEpochMillis
        ) {
            return
        }

        clearExtraTimeExpiry()
        val scope = serviceScope ?: return
        val delayMillis = (validUntilEpochMillis - clock.nowEpochMillis()).coerceAtLeast(0L)
        extraTimeExpiryPackageName = packageName
        extraTimeExpiryAtEpochMillis = validUntilEpochMillis
        extraTimeExpiryJob =
            scope.launch {
                delay(delayMillis)
                val trackedPackageName = extraTimeExpiryPackageName
                extraTimeExpiryJob = null
                extraTimeExpiryPackageName = null
                extraTimeExpiryAtEpochMillis = null
                if (trackedPackageName == packageName && usageTracker.currentPackageName() == packageName) {
                    evaluateForegroundApp(packageName, clock.elapsedRealtimeMillis())
                }
            }
    }

    private fun clearExtraTimeExpiry() {
        extraTimeExpiryJob?.cancel()
        extraTimeExpiryJob = null
        extraTimeExpiryPackageName = null
        extraTimeExpiryAtEpochMillis = null
    }

    private suspend fun saveTransition(transition: UsageTransition) {
        runCatching {
            val durationMs = transition.endedAtEpochMillis - transition.startedAtEpochMillis
            usageSessionRepository.saveSession(
                UsageSession(
                    id = UUID.randomUUID().toString(),
                    deviceId = deviceActivationRepository.currentActivation()?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                    packageName = transition.packageName,
                    startedAtEpochMillis = transition.startedAtEpochMillis,
                    endedAtEpochMillis = transition.endedAtEpochMillis,
                ),
            )
            Log.i(LogTag, "Saved usage package=${transition.packageName} durationMs=$durationMs")
        }.onFailure { telemetryReporter.recordError("Usage session save failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val CheckpointIntervalMillis = 15_000L
        const val ForegroundRecheckMillis = 250L
        const val MillisPerMinute = 60_000L
        const val BrowserProbeUrl = "https://www.example.com/"
        const val MaxDeadlineDelayMillis = 60_000L
        const val BlockRecheckDelayMillis = 120L
        const val BlockHomeRetries = 2
        const val SettingsEscapeRecheckDelayMillis = 100L
        const val SettingsEscapeFallbackAttempt = 3
        const val SettingsRecheckEventLabel = "SETTINGS_RECHECK"
        const val PolicyChangedEventLabel = "POLICY_CHANGED"
        const val ExplicitSearchNoticeDebounceMillis = 2_000L
        const val TamperAlertDebounceMillis = 5 * 60_000L
        const val GoogleSearchPackage = "com.google.android.googlequicksearchbox"
        const val LogTag = "ProtectorAccessibility"
        val ExplicitSearchPackages = setOf("com.android.chrome", GoogleSearchPackage)

        fun Long.toObservedMinutes(): Int =
            if (this <= 0L) {
                0
            } else {
                ((this + MillisPerMinute - 1) / MillisPerMinute).toInt()
            }

        fun PolicyDecision.label(): String =
            when (this) {
                is PolicyDecision.Allow -> "Allow"
                is PolicyDecision.Block -> "Block"
                is PolicyDecision.GrantExtraTime -> "GrantExtraTime"
                is PolicyDecision.HealthWarning -> "HealthWarning"
                is PolicyDecision.RequestAuthorization -> "RequestAuthorization"
                is PolicyDecision.RequireActivation -> "RequireActivation"
                is PolicyDecision.RequireUpdate -> "RequireUpdate"
                is PolicyDecision.Warn -> "Warn"
            }
    }
}

internal class ForegroundDecisionDiagnosticGate {
    private var lastKey: String? = null

    fun shouldRecord(key: String): Boolean {
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

internal fun hasExplicitAppApproval(
    packageName: String,
    rules: List<PolicyRule>,
): Boolean =
    rules.any {
        it.enabled &&
            it.scope == RuleScope.App &&
            it.target == packageName &&
            it.action == RuleAction.Allow
    }
