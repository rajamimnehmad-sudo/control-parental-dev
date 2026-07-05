package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicySnapshotProvider
import com.contentfilter.feature.accessibility.telemetry.AccessibilityTelemetryReporter
import com.contentfilter.feature.accessibility.time.AppUsageTracker
import com.contentfilter.feature.accessibility.time.UsageTransition
import com.contentfilter.feature.vpn.service.DevProtectionMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    @Inject lateinit var usageSessionRepository: UsageSessionRepository

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var syncEngine: SyncEngine

    private val usageTracker = AppUsageTracker()
    private val settingsProtectionPolicy = SettingsProtectionPolicy()
    private val searchEngineScreenDetector = SearchEngineScreenDetector()
    private var serviceScope: CoroutineScope? = null
    private var extraTimeExpiryJob: Job? = null
    private var extraTimeExpiryPackageName: String? = null
    private var extraTimeExpiryAtEpochMillis: Long? = null
    private var policyRefreshJob: Job? = null
    private var foregroundWatchJob: Job? = null
    private var foregroundWatchPackageName: String? = null
    private var appLimitDeadlineJob: Job? = null
    private var appLimitDeadlinePackageName: String? = null
    private var lastPolicyRefreshAtElapsedMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
        scope.launch {
            syncScheduler.requestSync()
            refreshPolicies()
            snapshotProvider.refresh()
            snapshotProvider.start(scope)
            systemStatusRepository.updateAccessibilityState(ComponentState.Enabled)
            telemetryReporter.recordServiceState("Accessibility service connected.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AccessibilityEventFilter.isHandled(event.eventType)) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (DevProtectionMode.isProtectionDisabled(this) || packageName.isAlwaysAllowedPackage()) return
        val elapsed = clock.elapsedRealtimeMillis()
        if (handleSettingsProtection(packageName, event.className?.toString(), elapsed)) return
        if (handleSearchEngineProtection(packageName, event.eventType)) return

        serviceScope?.launch { systemStatusRepository.updateAccessibilityState(ComponentState.Enabled) }
        val now = clock.nowEpochMillis()
        serviceScope?.let { scope ->
            if (snapshotProvider.ensureCurrentDay(scope)) {
                usageTracker.finishCurrent(elapsed, now)?.let { transition ->
                    scope.launch { saveTransition(transition) }
                }
                usageTracker.reset()
            }
        }
        maybeRefreshPolicies(elapsed)
        val transition = usageTracker.onForegroundApp(packageName, elapsed, now)
        if (transition != null) {
            serviceScope?.launch { saveTransition(transition) }
        }
        evaluateForegroundApp(packageName, elapsed)
        startForegroundWatch(packageName)
        if (transition == null) {
            usageTracker.checkpointCurrent(elapsed, now, CheckpointIntervalMillis)?.let { checkpoint ->
                serviceScope?.launch { saveTransition(checkpoint) }
            }
        }
    }

    override fun onInterrupt() {
        serviceScope?.launch { telemetryReporter.recordServiceState("Accessibility service interrupted.") }
    }

    override fun onDestroy() {
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        val transition = usageTracker.finishCurrent(elapsed, now)
        val scope = serviceScope
        if (scope != null) {
            scope.launch {
                transition?.let { saveTransition(it) }
                systemStatusRepository.updateAccessibilityState(ComponentState.Disabled)
                telemetryReporter.recordServiceState("Accessibility service destroyed.")
                snapshotProvider.stop()
                clearExtraTimeExpiry()
                clearAppLimitDeadline()
                clearForegroundWatch()
                cancel()
            }
        } else {
            snapshotProvider.stop()
            clearExtraTimeExpiry()
            clearForegroundWatch()
        }
        serviceScope = null
        super.onDestroy()
    }

    private fun handleSettingsProtection(
        packageName: String,
        className: String?,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        if (!settingsProtectionPolicy.shouldLeaveSettings(packageName, className, elapsedRealtimeMillis)) return false
        performGlobalAction(GLOBAL_ACTION_HOME)
        serviceScope?.launch { telemetryReporter.recordSettingsProtection() }
        return true
    }

    private fun handleSearchEngineProtection(
        packageName: String,
        eventType: Int,
    ): Boolean {
        val visibleText = rootInActiveWindow.visibleText()
        val diagnosis =
            searchEngineScreenDetector.diagnose(
                packageName = packageName,
                snapshot = snapshotProvider.current().snapshot,
                visibleText = visibleText,
            )
        Log.i(
            LogTag,
            "Search protection layer=accessibility event=${AccessibilityEventFilter.label(eventType)} package=$packageName reason=${diagnosis.reason} blockRules=${diagnosis.searchBlockRules} visibleTextLength=${diagnosis.visibleTextLength}",
        )
        serviceScope?.launch {
            telemetryReporter.recordSearchProtection(
                eventLabel = AccessibilityEventFilter.label(eventType),
                packageName = packageName,
                reason = diagnosis.reason,
                blockRules = diagnosis.searchBlockRules,
                result = if (diagnosis.shouldLeave) "blocked-search-screen" else "no-search-signal",
            )
        }
        if (!diagnosis.shouldLeave) return false
        Log.i(LogTag, "Leaving blocked search engine screen package=$packageName")
        performGlobalAction(GLOBAL_ACTION_HOME)
        serviceScope?.launch {
            telemetryReporter.recordServiceState(
                "Search protection accessibility action: ${diagnosis.reason}.",
            )
        }
        return true
    }

    private fun evaluateForegroundApp(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ) {
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
            }
            is PolicyDecision.Warn,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate,
            is PolicyDecision.HealthWarning,
            -> clearExtraTimeExpiry()
            is PolicyDecision.GrantExtraTime -> scheduleExtraTimeExpiry(packageName, decision.validUntilEpochMillis)
        }
    }

    private fun maybeRefreshPolicies(elapsedRealtimeMillis: Long) {
        if (elapsedRealtimeMillis - lastPolicyRefreshAtElapsedMillis < BackgroundPolicyRefreshMillis) return
        if (policyRefreshJob?.isActive == true) return
        val scope = serviceScope ?: return
        policyRefreshJob =
            scope.launch {
                refreshPolicies()
                snapshotProvider.refresh()
            }
    }

    private suspend fun refreshPolicies() {
        runCatching {
            syncEngine.syncCoreDataFull()
            syncEngine.syncRequestResultsFull()
        }
            .onSuccess { lastPolicyRefreshAtElapsedMillis = clock.elapsedRealtimeMillis() }
            .onFailure {
                Log.w(LogTag, "Policy refresh failed: ${it.javaClass.simpleName}")
                telemetryReporter.recordError("Policy refresh failed: ${it.javaClass.simpleName}")
            }
    }

    private fun leaveBlockedApp(packageName: String) {
        Log.i(LogTag, "Blocking foreground app pending confirmation package=$packageName")
        val scope = serviceScope ?: return
        scope.launch {
            delay(BlockRecheckDelayMillis)
            if (usageTracker.currentPackageName() == packageName && packageName.isStillBlocked()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(BlockRecheckDelayMillis)
                if (usageTracker.currentPackageName() == packageName && packageName.isStillBlocked()) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
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
                while (usageTracker.currentPackageName() == packageName) {
                    delay(ForegroundRecheckMillis)
                    val elapsed = clock.elapsedRealtimeMillis()
                    val now = clock.nowEpochMillis()
                    usageTracker.checkpointCurrent(elapsed, now, CheckpointIntervalMillis)?.let { checkpoint ->
                        saveTransition(checkpoint)
                    snapshotProvider.refresh()
                }
                evaluateForegroundApp(packageName, elapsed)
            }
        }
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
        const val ForegroundRecheckMillis = 5_000L
        const val MillisPerMinute = 60_000L
        const val MaxDeadlineDelayMillis = 60_000L
        const val BlockRecheckDelayMillis = 250L
        const val BackgroundPolicyRefreshMillis = 1_000L
        const val LogTag = "ProtectorAccessibility"
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

        fun AccessibilityNodeInfo?.visibleText(): String {
            if (this == null) return ""
            val values = mutableListOf<String>()
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null || values.size >= MaxVisibleTextNodes) return
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
                for (index in 0 until node.childCount) {
                    visit(node.getChild(index))
                    if (values.size >= MaxVisibleTextNodes) return
                }
            }
            visit(this)
            return values.joinToString(" ")
        }

        const val MaxVisibleTextNodes = 80
    }
}
