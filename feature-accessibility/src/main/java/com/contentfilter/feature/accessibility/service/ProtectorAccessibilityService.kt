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
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.InstallApprovalStore
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualController
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualProbeController
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldController
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicySnapshotProvider
import com.contentfilter.feature.accessibility.telemetry.AccessibilityTelemetryReporter
import com.contentfilter.feature.accessibility.time.AppUsageTracker
import com.contentfilter.feature.accessibility.time.UsageTransition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
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

    @Inject lateinit var pushNotificationRepository: PushNotificationRepository

    @Inject lateinit var protectionStateStore: ProtectionStateStore

    @Inject lateinit var usageSessionRepository: UsageSessionRepository

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var installApprovalStore: InstallApprovalStore

    private val usageTracker = AppUsageTracker()
    private val explicitSearchClassifier = ExplicitSearchClassifier()
    private var serviceScope: CoroutineScope? = null
    private var treeProtectionCoordinator: AccessibilityTreeProtectionCoordinator? = null
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
    private val foregroundDecisionDiagnosticGate = ForegroundDecisionDiagnosticGate()
    private var chromeVisualProbeController: ChromeVisualProbeController? = null
    private var chromeVisualShieldController: ChromeVisualShieldController? = null
    private var chromeVisualController: ChromeVisualController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ChromePhotosDataPlaneRuntimeAttestation.markAccessibilityBound(true)
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        serviceScope = scope
        chromeVisualProbeController = ChromeVisualProbeController(this, scope)
        chromeVisualShieldController =
            ChromeVisualShieldController(this, scope) { active ->
                if (active) chromeVisualProbeController?.suspendForVisualShield()
            }
        chromeVisualController = ChromeVisualController(this, scope)
        treeProtectionCoordinator =
            AccessibilityTreeProtectionCoordinator(
                service = this,
                scope = scope,
                clock = clock,
                snapshotProvider = snapshotProvider,
                telemetryReporter = telemetryReporter,
                pushNotificationRepository = pushNotificationRepository,
                protectionStateStore = protectionStateStore,
            )
        scope.launch {
            syncScheduler.requestSync()
            snapshotProvider.refresh()
            snapshotProvider.start(scope)
            launch {
                snapshotProvider.observe().collect {
                    treeProtectionCoordinator?.onPolicyChanged()
                }
            }
            systemStatusRepository.updateAccessibilityState(ComponentState.Enabled)
            telemetryReporter.recordServiceState("Accessibility service connected.")
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
            chromeVisualShieldController?.onAccessibilityEvent(event)
            if (chromeVisualShieldController?.ownsLabSession() != true) {
                chromeVisualProbeController?.onAccessibilityEvent(event)
            }
            chromeVisualController?.onAccessibilityEvent(event)
            return
        }
        if (!AccessibilityEventFilter.isHandled(event.eventType)) return
        chromeVisualShieldController?.onAccessibilityEvent(event)
        if (chromeVisualShieldController?.ownsLabSession() != true) {
            chromeVisualProbeController?.onAccessibilityEvent(event)
        }
        chromeVisualController?.onAccessibilityEvent(event)
        val packageName = eventPackageName ?: return
        val couldContainProtectedSettings = treeProtectionCoordinator?.observePackage(packageName) ?: false
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && !couldContainProtectedSettings) return
        if (blockExplicitSearchIfNeeded(event, packageName)) return
        val elapsed = clock.elapsedRealtimeMillis()
        val now = clock.nowEpochMillis()
        if (
            couldContainProtectedSettings &&
            treeProtectionCoordinator?.handleSettingsEvent(
                event = event,
                packageName = packageName,
                className = event.className?.toString(),
                elapsedRealtimeMillis = elapsed,
                nowEpochMillis = now,
            ) == true
        ) {
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) return
        if (AccessibilityForegroundAllowlist.contains(packageName)) {
            treeProtectionCoordinator?.invalidateSearchContext()
            handleAlwaysAllowedForeground(packageName, elapsed, now)
            return
        }
        if (blockRetryPackageName != packageName) clearBlockRetry()
        if (!couldContainProtectedSettings) {
            treeProtectionCoordinator?.submitSearch(
                packageName = packageName,
                eventLabel = AccessibilityEventFilter.label(event.eventType),
            )
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
        chromeVisualShieldController?.onAccessibilityUnavailable()
        serviceScope?.launch { telemetryReporter.recordServiceState("Accessibility service interrupted.") }
    }

    override fun onDestroy() {
        ChromePhotosDataPlaneRuntimeAttestation.markAccessibilityBound(false)
        chromeVisualShieldController?.close()
        chromeVisualShieldController = null
        chromeVisualProbeController?.close()
        chromeVisualProbeController = null
        chromeVisualController?.close()
        chromeVisualController = null
        treeProtectionCoordinator?.close()
        treeProtectionCoordinator = null
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
        const val MaxDeadlineDelayMillis = 60_000L
        const val BlockRecheckDelayMillis = 120L
        const val BlockHomeRetries = 2
        const val ExplicitSearchNoticeDebounceMillis = 2_000L
        const val GoogleSearchPackage = "com.google.android.googlequicksearchbox"
        const val LogTag = "ProtectorAccessibility"
        val ExplicitSearchPackages = setOf("com.android.chrome", GoogleSearchPackage)
    }
}
