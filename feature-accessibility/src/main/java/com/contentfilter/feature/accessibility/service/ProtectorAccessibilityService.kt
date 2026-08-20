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
    private val browserCandidateCache = mutableMapOf<String, Boolean>()
    private var serviceScope: CoroutineScope? = null
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
    private var observedWindowPackageName: String? = null
    private var observedWindowClassName: String? = null
    private var lastExplicitSearchNoticeAt: Long = 0L
    private var lastTamperAlertAt: Long = 0L
    private val foregroundDecisionDiagnosticGate = ForegroundDecisionDiagnosticGate()
    private val ownUninstallerPackages by lazy { resolveOwnUninstallerPackages() }
    private var chromeVisualProbeController: ChromeVisualProbeController? = null
    private var chromeVisualController: ChromeVisualController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
        chromeVisualProbeController = ChromeVisualProbeController(this, scope)
        chromeVisualController = ChromeVisualController(this, scope)
        scope.launch {
            syncScheduler.requestSync()
            snapshotProvider.refresh()
            snapshotProvider.start(scope)
            launch {
                snapshotProvider.observe().collect {
                    withContext(Dispatchers.Main.immediate) {
                        val packageName = rootInActiveWindow?.packageName?.toString()
                        if (packageName != null) {
                            handleSearchEngineProtection(packageName, PolicyChangedEventLabel)
                        }
                    }
                }
            }
            systemStatusRepository.updateAccessibilityState(ComponentState.Enabled)
            telemetryReporter.recordServiceState("Accessibility service connected.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AccessibilityEventFilter.isHandled(event.eventType)) return
        chromeVisualProbeController?.onAccessibilityEvent(event)
        chromeVisualController?.onAccessibilityEvent(event)
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val resolvedOwnUninstaller = packageName in ownUninstallerPackages
            if (!settingsProtectionPolicy.couldContainProtectedScreen(packageName, resolvedOwnUninstaller)) return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            observedWindowPackageName = packageName
            observedWindowClassName = event.className?.toString()
        }
        if (blockExplicitSearchIfNeeded(event, packageName)) return
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        if (handleSettingsProtection(event, packageName, event.className?.toString(), elapsed, now)) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) return
        if (AccessibilityForegroundAllowlist.contains(packageName)) {
            handleAlwaysAllowedForeground(packageName, elapsed, now)
            return
        }
        if (blockRetryPackageName != packageName) clearBlockRetry()
        if (handleSearchEngineProtection(packageName, AccessibilityEventFilter.label(event.eventType))) return

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
        if (!source.isEditable || !source.isRecognizedSearchField(packageName)) return false
        val query = source.text?.takeIf { it.isNotBlank() } ?: return false
        if (explicitSearchClassifier.classify(query) != ExplicitSearchDecision.BlockExplicit) return false
        source.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            },
        )
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
        return AddressBarViewIdParts.any { viewId.endsWith("/id/$it") || viewId.endsWith(":id/$it") } ||
            "search" in viewId
    }

    override fun onInterrupt() {
        serviceScope?.launch { telemetryReporter.recordServiceState("Accessibility service interrupted.") }
    }

    override fun onDestroy() {
        chromeVisualProbeController?.close()
        chromeVisualProbeController = null
        chromeVisualController?.close()
        chromeVisualController = null
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
        elapsedRealtimeMillis: Long,
        nowEpochMillis: Long,
    ): Boolean {
        val resolvedOwnUninstaller = packageName in ownUninstallerPackages
        if (!settingsProtectionPolicy.couldContainProtectedScreen(packageName, resolvedOwnUninstaller)) return false
        val ownAppIdentityVisible =
            rootInActiveWindow.containsOwnAppIdentity() || eventContainsOwnAppIdentity(event)
        val adminAppIdentityVisible =
            rootInActiveWindow.containsAdminAppIdentity() || eventContainsAdminAppIdentity(event)
        val dangerousSettingsActionVisible = rootInActiveWindow.containsDangerousSettingsAction()
        val installSourceSettingsVisible = rootInActiveWindow.containsInstallSourceSettingsIndicator()
        if (
            !settingsProtectionPolicy.shouldLeaveProtectedScreen(
                packageName = packageName,
                className = className,
                ownAppIdentityVisible = ownAppIdentityVisible,
                adminAppIdentityVisible = adminAppIdentityVisible,
                resolvedOwnUninstaller = resolvedOwnUninstaller,
                dangerousSettingsActionVisible = dangerousSettingsActionVisible,
                installSourceSettingsVisible = installSourceSettingsVisible,
                deviceAdminEnabled = DeviceAdminController.isEnabled(this),
                armed = protectionStateStore.isArmed(),
                settingsAuthorized =
                    protectionStateStore.isAuthorized(
                        ProtectionAuthorizationScope.Settings,
                        nowEpochMillis,
                    ),
                removalAuthorized =
                    protectionStateStore.isAuthorized(
                        ProtectionAuthorizationScope.Removal,
                        nowEpochMillis,
                    ),
                trustedInstallAuthorized = protectionStateStore.isTrustedInstallAuthorized(nowEpochMillis),
                elapsedRealtimeMillis = elapsedRealtimeMillis,
            )
        ) {
            return false
        }
        leaveProtectedSettings(
            urgent =
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    settingsProtectionPolicy.requiresImmediateEscape(
                        packageName = packageName,
                        className = className,
                        ownAppIdentityVisible = ownAppIdentityVisible,
                        dangerousSettingsActionVisible = dangerousSettingsActionVisible,
                    ),
        )
        Toast.makeText(this, "Este ajuste está protegido", Toast.LENGTH_SHORT).show()
        serviceScope?.launch {
            telemetryReporter.recordSettingsProtection()
            if (lastTamperAlertAt == 0L || elapsedRealtimeMillis - lastTamperAlertAt >= TamperAlertDebounceMillis) {
                lastTamperAlertAt = elapsedRealtimeMillis
                pushNotificationRepository.reportProtectionAlert(ProtectionAlertType.TamperAttempt)
            }
        }
        return true
    }

    private fun leaveProtectedSettings(urgent: Boolean = false) {
        if (urgent) {
            performSettingsEscapeAction(SettingsEscapeStrategy.actionForAttempt(attempt = 0, urgent = true))
        } else {
            if (settingsEscapeJob?.isActive == true) return
            performSettingsEscapeAction(SettingsEscapeStrategy.actionForAttempt(attempt = 0, urgent = false))
        }
        val scope = serviceScope ?: return
        if (settingsEscapeJob?.isActive == true) return
        settingsEscapeJob =
            scope.launch {
                for (attempt in 1..SettingsEscapeFallbackAttempt) {
                    delay(SettingsEscapeRecheckDelayMillis)
                    if (!isProtectedSettingsStillVisible()) break
                    performSettingsEscapeAction(SettingsEscapeStrategy.actionForAttempt(attempt, urgent = urgent))
                }
                settingsEscapeJob = null
            }
    }

    private fun isProtectedSettingsStillVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return false
        val className =
            observedWindowClassName.takeIf {
                observedWindowPackageName == packageName
            } ?: root.className?.toString()
        return settingsProtectionPolicy.shouldLeaveProtectedScreen(
            packageName = packageName,
            className = className,
            ownAppIdentityVisible = root.containsOwnAppIdentity(),
            adminAppIdentityVisible = root.containsAdminAppIdentity(),
            resolvedOwnUninstaller = packageName in ownUninstallerPackages,
            dangerousSettingsActionVisible = root.containsDangerousSettingsAction(),
            installSourceSettingsVisible = root.containsInstallSourceSettingsIndicator(),
            deviceAdminEnabled = DeviceAdminController.isEnabled(this),
            armed = protectionStateStore.isArmed(),
            settingsAuthorized =
                protectionStateStore.isAuthorized(
                    ProtectionAuthorizationScope.Settings,
                    clock.nowEpochMillis(),
                ),
            removalAuthorized =
                protectionStateStore.isAuthorized(
                    ProtectionAuthorizationScope.Removal,
                    clock.nowEpochMillis(),
                ),
            trustedInstallAuthorized = protectionStateStore.isTrustedInstallAuthorized(clock.nowEpochMillis()),
            elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
        )
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

    private fun AccessibilityNodeInfo?.containsOwnAppIdentity(): Boolean {
        val root = this ?: return false
        val appLabel = applicationInfo.loadLabel(packageManager).toString()
        val ownPackage = applicationContext.packageName
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MaxIdentityNodes) {
            val node = pending.removeFirst()
            visited += 1
            val values = listOf(node.text?.toString(), node.contentDescription?.toString(), node.viewIdResourceName)
            if (
                values.any { value ->
                    value.matchesOwnAppIdentity(ownPackage, appLabel)
                }
            ) {
                return true
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
    }

    private fun AccessibilityNodeInfo?.containsDangerousSettingsAction(): Boolean {
        val root = this ?: return false
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MaxIdentityNodes) {
            val node = pending.removeFirst()
            visited += 1
            if (
                isDangerousSettingsAction(
                    viewId = node.viewIdResourceName,
                    label = node.text?.toString() ?: node.contentDescription?.toString(),
                    clickable = node.isClickable,
                )
            ) {
                return true
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
    }

    private fun AccessibilityNodeInfo?.containsInstallSourceSettingsIndicator(): Boolean {
        val root = this ?: return false
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MaxIdentityNodes) {
            val node = pending.removeFirst()
            visited += 1
            val labels = listOf(node.text?.toString(), node.contentDescription?.toString())
            if (labels.any { isInstallSourceSettingsIndicator(node.viewIdResourceName, it) }) return true
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
    }

    private fun AccessibilityNodeInfo?.containsAdminAppIdentity(): Boolean {
        val root = this ?: return false
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MaxIdentityNodes) {
            val node = pending.removeFirst()
            visited += 1
            val values = listOf(node.text?.toString(), node.contentDescription?.toString(), node.viewIdResourceName)
            if (values.any { it.matchesAdminAppIdentity() }) return true
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
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

    private fun eventContainsOwnAppIdentity(event: AccessibilityEvent): Boolean {
        val appLabel = applicationInfo.loadLabel(packageManager).toString()
        val ownPackage = applicationContext.packageName
        val values = event.text.map(CharSequence::toString) + listOfNotNull(event.contentDescription?.toString())
        return values.any { value -> value.matchesOwnAppIdentity(ownPackage, appLabel) }
    }

    private fun eventContainsAdminAppIdentity(event: AccessibilityEvent): Boolean {
        val values = event.text.map(CharSequence::toString) + listOfNotNull(event.contentDescription?.toString())
        return values.any { it.matchesAdminAppIdentity() }
    }

    private fun handleSearchEngineProtection(
        packageName: String,
        eventLabel: String,
    ): Boolean {
        val page = rootInActiveWindow.browserPageObservation()
        val snapshot = snapshotProvider.current().snapshot
        val recentSearchEngine =
            SearchProtectionSignals
                .recentSearchEngine()
                ?.takeIf { it.policyRevision == snapshot.version }
        val diagnosis =
            searchEngineScreenDetector.diagnose(
                packageName = packageName,
                snapshot = snapshot,
                currentHost = page.host,
                addressBarFocused = page.addressBarFocused,
                recentSearchEngineId = recentSearchEngine?.engineId,
                browserCandidate = isBrowserCandidate(packageName),
                elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            )
        Log.i(
            LogTag,
            "Search protection layer=accessibility event=$eventLabel " +
                "package=$packageName policyVersion=${snapshot.version} " +
                "webNavigationBlocked=${diagnosis.webNavigationBlocked} " +
                "externalSearchResultsAllowed=${snapshot.rules.externalSearchResultsAllowed()} " +
                "protectedBrowserRequired=${snapshot.rules.protectedBrowserRequired()} " +
                "safeSearch=${snapshot.rules.safeSearchEnabled()} " +
                "searchEngine=${diagnosis.searchEngineId ?: "none"} " +
                "action=${diagnosis.action} reason=${diagnosis.reason}",
        )
        serviceScope?.launch {
            telemetryReporter.recordSearchProtection(
                eventLabel = eventLabel,
                packageName = packageName,
                packageCategory = diagnosis.packageCategory,
                reason = diagnosis.reason,
                searchEngineId = diagnosis.searchEngineId,
                action = diagnosis.action.name,
                policyRevision = diagnosis.policyRevision,
            )
        }
        if (
            diagnosis.action != SearchNavigationAction.Allow &&
            !webActionDebouncer.shouldPerform(
                packageName = packageName,
                host = page.host,
                policyRevision = diagnosis.policyRevision,
                action = diagnosis.action,
                elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            )
        ) {
            Log.i(
                LogTag,
                "Search protection action suppressed package=$packageName " +
                    "policyVersion=${diagnosis.policyRevision} action=${diagnosis.action}",
            )
            return true
        }
        when (diagnosis.action) {
            SearchNavigationAction.Allow -> return false
            SearchNavigationAction.GoBack -> performGlobalAction(GLOBAL_ACTION_BACK)
            SearchNavigationAction.GoHome -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
        serviceScope?.launch {
            telemetryReporter.recordServiceState(
                "Search protection action=${diagnosis.action} reason=${diagnosis.reason}.",
            )
        }
        return true
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
        const val PolicyChangedEventLabel = "POLICY_CHANGED"
        const val ExplicitSearchNoticeDebounceMillis = 2_000L
        const val TamperAlertDebounceMillis = 5 * 60_000L
        const val MaxIdentityNodes = 200
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

        fun AccessibilityNodeInfo?.browserPageObservation(): BrowserPageObservation {
            if (this == null) return BrowserPageObservation()
            var observation = BrowserPageObservation()
            var visited = 0

            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null || visited >= MaxBrowserNodes) return
                visited++
                if (node.viewIdResourceName.isAddressBarViewId()) {
                    val address =
                        SearchEngineScreenDetector.addressObservationFromAddressBarText(node.text)
                            ?: SearchEngineScreenDetector.addressObservationFromAddressBarText(node.contentDescription)
                    if (address != null && (observation.host == null || node.isFocused)) {
                        observation =
                            observation.copy(
                                host = address.host,
                                addressBarFocused = node.isFocused,
                            )
                    }
                }
                for (index in 0 until node.childCount) {
                    visit(node.getChild(index))
                    if (visited >= MaxBrowserNodes) return
                }
            }
            visit(this)
            return observation
        }

        fun String?.isAddressBarViewId(): Boolean {
            val value = this?.lowercase() ?: return false
            return AddressBarViewIdParts.any { part -> value.endsWith("/id/$part") || value.endsWith(":id/$part") } ||
                (("url" in value || "address" in value || "location" in value) && "bar" in value)
        }

        const val MaxBrowserNodes = 500
        val AddressBarViewIdParts =
            setOf(
                "url_bar",
                "location_bar_edit_text",
                "address_bar",
                "mozac_browser_toolbar_url_view",
            )
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

private data class BrowserPageObservation(
    val host: String? = null,
    val addressBarFocused: Boolean = false,
)
