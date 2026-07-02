package com.contentfilter.feature.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.UsageSession
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.domain.repository.UsageSessionRepository
import com.contentfilter.feature.accessibility.policy.AccessibilityAppPolicyEvaluator
import com.contentfilter.feature.accessibility.policy.AccessibilityClock
import com.contentfilter.feature.accessibility.policy.AccessibilityPolicySnapshotProvider
import com.contentfilter.feature.accessibility.telemetry.AccessibilityTelemetryReporter
import com.contentfilter.feature.accessibility.time.AppUsageTracker
import com.contentfilter.feature.accessibility.time.UsageTransition
import com.contentfilter.feature.vpn.service.DevProtectionMode
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProtectorAccessibilityService : AccessibilityService() {
    @Inject lateinit var clock: AccessibilityClock
    @Inject lateinit var policyEvaluator: AccessibilityAppPolicyEvaluator
    @Inject lateinit var snapshotProvider: AccessibilityPolicySnapshotProvider
    @Inject lateinit var deviceActivationRepository: DeviceActivationRepository
    @Inject lateinit var systemStatusRepository: SystemStatusRepository
    @Inject lateinit var telemetryReporter: AccessibilityTelemetryReporter
    @Inject lateinit var usageSessionRepository: UsageSessionRepository

    private val usageTracker = AppUsageTracker()
    private val settingsProtectionPolicy = SettingsProtectionPolicy()
    private var serviceScope: CoroutineScope? = null
    private var extraTimeExpiryJob: Job? = null
    private var extraTimeExpiryPackageName: String? = null
    private var extraTimeExpiryAtEpochMillis: Long? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
        scope.launch {
            snapshotProvider.refresh()
            snapshotProvider.start(scope)
            systemStatusRepository.updateAccessibilityState(ComponentState.Enabled)
            telemetryReporter.recordServiceState("Accessibility service connected.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in HandledEventTypes) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (DevProtectionMode.isProtectionDisabled(this) || packageName.isAlwaysAllowedPackage()) return
        val elapsed = clock.elapsedRealtimeMillis()
        if (handleSettingsProtection(packageName, event.className?.toString(), elapsed)) return

        serviceScope?.launch { systemStatusRepository.updateAccessibilityState(ComponentState.Enabled) }
        serviceScope?.let { snapshotProvider.ensureCurrentDay(it) }
        val now = clock.nowEpochMillis()
        val transition = usageTracker.onForegroundApp(packageName, elapsed, now)
        if (transition != null) {
            serviceScope?.launch { saveTransition(transition) }
        }
        evaluateForegroundApp(packageName, elapsed)
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
                cancel()
            }
        } else {
            snapshotProvider.stop()
            clearExtraTimeExpiry()
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

    private fun evaluateForegroundApp(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ) {
        val state = snapshotProvider.current()
        val persistedMinutes = state.snapshot.dailyUsage
            .firstOrNull { it.packageName == packageName }
            ?.usedMinutes ?: 0
        val usedMinutes = persistedMinutes + usageTracker.activeMinutes(packageName, elapsedRealtimeMillis)
        val decision = policyEvaluator.evaluate(packageName, usedMinutes, state.snapshot, state.health)
        serviceScope?.launch { telemetryReporter.recordDecision(decision) }
        when (decision) {
            is PolicyDecision.Allow -> clearExtraTimeExpiry()
            is PolicyDecision.Block,
            is PolicyDecision.RequestAuthorization -> {
                clearExtraTimeExpiry()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            is PolicyDecision.Warn,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate,
            is PolicyDecision.HealthWarning -> clearExtraTimeExpiry()
            is PolicyDecision.GrantExtraTime -> scheduleExtraTimeExpiry(packageName, decision.validUntilEpochMillis)
        }
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
        extraTimeExpiryJob = scope.launch {
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
            usageSessionRepository.saveSession(
                UsageSession(
                    id = UUID.randomUUID().toString(),
                    deviceId = deviceActivationRepository.currentActivation()?.deviceId ?: UsageSession.LOCAL_DEVICE_ID,
                    packageName = transition.packageName,
                    startedAtEpochMillis = transition.startedAtEpochMillis,
                    endedAtEpochMillis = transition.endedAtEpochMillis,
                ),
            )
        }.onFailure { telemetryReporter.recordError("Usage session save failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val CheckpointIntervalMillis = 5 * 60 * 1_000L
        val HandledEventTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
        )
        val ExactAllowedPackageNames = setOf(
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
        val AllowedPackagePrefixes = listOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod",
            "com.google.android.webview",
        )

        fun String.isAlwaysAllowedPackage(): Boolean =
            this in ExactAllowedPackageNames ||
                AllowedPackagePrefixes.any { startsWith(it) } ||
                endsWith(".launcher")
    }
}
