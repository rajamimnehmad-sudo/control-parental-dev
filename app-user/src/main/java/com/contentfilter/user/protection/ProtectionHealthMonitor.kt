package com.contentfilter.user.protection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.vpn.service.VpnController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionHealthMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val activationRepository: DeviceActivationRepository,
        private val systemStatusRepository: SystemStatusRepository,
        private val pushNotificationRepository: PushNotificationRepository,
        private val protectionControlCoordinator: ProtectionControlCoordinator,
    ) {
        private val reportedAlerts = mutableSetOf<ProtectionAlertType>()

        suspend fun checkNow() {
            if (activationRepository.currentActivation() == null) {
                reportedAlerts.clear()
                return
            }
            val vpnPermissionGranted = VpnController.prepareIntent(context) == null
            val decision =
                ProtectionHealthPolicy.evaluate(
                    vpnActive = VpnController.isRunning(context) && hasActiveVpnNetwork(),
                    accessibilityEnabled = AccessibilityController.isEnabled(context),
                    deviceAdminEnabled = DeviceAdminController.isEnabled(context),
                    vpnPermissionGranted = vpnPermissionGranted,
                    vpnProtectionDisabled = VpnController.isDevProtectionDisabled(context),
                )
            persistChangedStates(decision)
            if (decision.canAutoArm) {
                protectionControlCoordinator.autoArmIfEligible()
            }
            decision.alerts.forEach { alert ->
                if (reportedAlerts.add(alert)) {
                    pushNotificationRepository.reportProtectionAlert(alert)
                }
            }
            reportedAlerts.retainAll(decision.alerts)
            if (decision.shouldRestartVpn) {
                VpnController.start(context)
            }
        }

        private suspend fun persistChangedStates(decision: ProtectionHealthDecision) {
            val current = systemStatusRepository.currentHealth()
            if (current.vpnState != decision.vpnState) {
                systemStatusRepository.updateVpnState(decision.vpnState)
            }
            if (current.accessibilityState != decision.accessibilityState) {
                systemStatusRepository.updateAccessibilityState(decision.accessibilityState)
            }
            if (current.deviceAdminState != decision.deviceAdminState) {
                systemStatusRepository.updateDeviceAdminState(decision.deviceAdminState)
            }
        }

        private fun hasActiveVpnNetwork(): Boolean =
            runCatching {
                val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
                manager.allNetworks.any { network ->
                    manager
                        .getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            }.getOrDefault(false)
    }

internal data class ProtectionHealthDecision(
    val vpnState: ComponentState,
    val accessibilityState: ComponentState,
    val deviceAdminState: ComponentState,
    val shouldRestartVpn: Boolean,
    val canAutoArm: Boolean,
    val alerts: Set<ProtectionAlertType>,
)

internal object ProtectionHealthPolicy {
    fun evaluate(
        vpnActive: Boolean,
        accessibilityEnabled: Boolean,
        deviceAdminEnabled: Boolean,
        vpnPermissionGranted: Boolean,
        vpnProtectionDisabled: Boolean,
    ): ProtectionHealthDecision {
        val alerts =
            buildSet {
                if (!vpnActive) add(ProtectionAlertType.WebDisabled)
                if (!accessibilityEnabled) add(ProtectionAlertType.AppsDisabled)
                if (!deviceAdminEnabled) add(ProtectionAlertType.AdminDisabled)
            }
        return ProtectionHealthDecision(
            vpnState = vpnActive.toComponentState(),
            accessibilityState = accessibilityEnabled.toComponentState(),
            deviceAdminState = deviceAdminEnabled.toComponentState(),
            shouldRestartVpn = !vpnActive && vpnPermissionGranted && !vpnProtectionDisabled,
            canAutoArm =
                vpnActive &&
                    accessibilityEnabled &&
                    deviceAdminEnabled &&
                    !vpnProtectionDisabled,
            alerts = alerts,
        )
    }

    private fun Boolean.toComponentState(): ComponentState =
        if (this) {
            ComponentState.Enabled
        } else {
            ComponentState.Disabled
        }
}
