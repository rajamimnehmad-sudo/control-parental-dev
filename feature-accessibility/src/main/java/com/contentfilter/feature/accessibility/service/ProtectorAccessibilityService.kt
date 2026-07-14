package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.core.sync.SyncScheduler
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

    private val usageTracker = AppUsageTracker()
    private val settingsProtectionPolicy = SettingsProtectionPolicy()
    private val searchEngineScreenDetector = SearchEngineScreenDetector()
    private val webActionDebouncer = AccessibilityWebActionDebouncer()
    private val explicitSearchClassifier = ExplicitSearchClassifier()
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
    private var lastExplicitSearchNoticeAt: Long = 0L
    private var lastTamperAlertAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
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
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (blockExplicitSearchIfNeeded(event, packageName)) return
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        if (handleSettingsProtection(event, packageName, event.className?.toString(), elapsed, now)) return
        if (packageName.isAlwaysAllowedPackage()) {
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
        webActionDebouncer.clear()
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
        val ownAppIdentityVisible =
            rootInActiveWindow.containsOwnAppIdentity() || eventContainsOwnAppIdentity(event)
        if (
            !settingsProtectionPolicy.shouldLeaveProtectedScreen(
                packageName = packageName,
                className = className,
                ownAppIdentityVisible = ownAppIdentityVisible,
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
                elapsedRealtimeMillis = elapsedRealtimeMillis,
            )
        ) {
            return false
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
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
                    value?.contains(ownPackage, ignoreCase = true) == true ||
                        value?.equals(appLabel, ignoreCase = true) == true
                }
            ) {
                return true
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
    }

    private fun eventContainsOwnAppIdentity(event: AccessibilityEvent): Boolean {
        val appLabel = applicationInfo.loadLabel(packageManager).toString()
        val ownPackage = applicationContext.packageName
        val values = event.text.map(CharSequence::toString) + listOfNotNull(event.contentDescription?.toString())
        return values.any { value ->
            value.contains(ownPackage, ignoreCase = true) ||
                value.equals(appLabel, ignoreCase = true)
        }
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
                elapsedRealtimeMillis = clock.elapsedRealtimeMillis(),
            )
        Log.i(
            LogTag,
            "Search protection layer=accessibility event=$eventLabel " +
                "package=$packageName policyVersion=${snapshot.version} " +
                "webNavigationBlocked=${diagnosis.webNavigationBlocked} " +
                "externalSearchResultsAllowed=${snapshot.rules.externalSearchResultsAllowed()} " +
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

    private fun evaluateForegroundApp(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        val state = snapshotProvider.current()
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
        Log.i(
            LogTag,
            "Evaluated app package=$packageName persistedMin=$persistedMinutes activeMs=$activeMillis usedMin=$usedMinutes limits=${state.snapshot.dailyLimits.size} rules=${state.snapshot.rules.size} decision=${decision.label()}",
        )
        serviceScope?.launch { telemetryReporter.recordDecision(packageName, decision) }
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
        const val MaxDeadlineDelayMillis = 60_000L
        const val BlockRecheckDelayMillis = 120L
        const val BlockHomeRetries = 2
        const val PolicyChangedEventLabel = "POLICY_CHANGED"
        const val ExplicitSearchNoticeDebounceMillis = 2_000L
        const val TamperAlertDebounceMillis = 5 * 60_000L
        const val MaxIdentityNodes = 200
        const val GoogleSearchPackage = "com.google.android.googlequicksearchbox"
        const val LogTag = "ProtectorAccessibility"
        val ExplicitSearchPackages = setOf("com.android.chrome", GoogleSearchPackage)
        val ExactAllowedPackageNames =
            setOf(
                "android",
                "com.android.contacts",
                "com.android.dialer",
                "com.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.android.phone",
                "com.android.providers.downloads",
                "com.android.settings",
                "com.contentfilter.admin",
                "com.contentfilter.admin.dev",
                "com.contentfilter.admin.beta",
                "com.contentfilter.user",
                "com.contentfilter.user.dev",
                "com.contentfilter.user.beta",
                "com.google.android.contacts",
                "com.google.android.dialer",
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.google.android.packageinstaller",
                "com.google.android.permissioncontroller",
                "com.google.android.setupwizard",
                "com.android.vending",
            )
        val AllowedPackagePrefixes =
            listOf(
                "com.android.launcher",
                "com.google.android.apps.nexuslauncher",
                "com.google.android.inputmethod",
                "com.google.android.webview",
            )

        fun String.isAlwaysAllowedPackage(): Boolean =
            this in ExactAllowedPackageNames ||
                AllowedPackagePrefixes.any { startsWith(it) } ||
                endsWith(".launcher")

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

private data class BrowserPageObservation(
    val host: String? = null,
    val addressBarFocused: Boolean = false,
)
