package com.contentfilter.feature.status

import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.ProtectionLevel
import com.contentfilter.core.domain.model.SystemHealthSnapshot

data class SystemStatusUiState(
    val title: String,
    val protectionLevel: ProtectionLevel,
    val summary: String,
    val vpnState: String,
    val accessibilityState: String,
    val deviceAdminState: String,
    val syncState: String,
    val activationState: String,
    val appVersion: String,
    val isVpnActive: Boolean,
    val communityName: String = "",
    val guideName: String = "",
) {
    fun withVpnRunning(isRunning: Boolean): SystemStatusUiState =
        if (isRunning) {
            copy(
                summary = "VPN Activa, Accesibilidad $accessibilityState",
                vpnState = "Activa",
                isVpnActive = true,
            )
        } else {
            copy(
                summary = "VPN Inactiva, Accesibilidad $accessibilityState",
                vpnState = "Inactiva",
                isVpnActive = false,
            )
        }

    companion object {
        fun from(
            snapshot: SystemHealthSnapshot,
            communityName: String = "",
            guideName: String = "",
        ): SystemStatusUiState =
            SystemStatusUiState(
                title = "Estado del sistema",
                protectionLevel = snapshot.protectionLevel,
                summary =
                    "VPN ${snapshot.vpnState.displayName()}, Accesibilidad ${snapshot.accessibilityState.displayName()}, " +
                        "Desinstalación ${snapshot.deviceAdminState.displayName()}",
                vpnState = snapshot.vpnState.displayName(),
                accessibilityState = snapshot.accessibilityState.displayName(),
                deviceAdminState = snapshot.deviceAdminState.displayName(),
                syncState = snapshot.syncState.displayName(),
                activationState = snapshot.licenseState.displayName(),
                appVersion = "1.0.0",
                isVpnActive = snapshot.vpnState == ComponentState.Enabled,
                communityName = communityName,
                guideName = guideName,
            )

        private fun ComponentState.displayName(): String =
            when (this) {
                ComponentState.Enabled -> "Activa"
                ComponentState.Disabled -> "Inactiva"
                ComponentState.Warning -> "Con advertencias"
                ComponentState.Unknown -> "Desconocida"
            }

        private fun LicenseState.displayName(): String =
            when (this) {
                LicenseState.Active -> "Activada"
                LicenseState.Scheduled -> "Programada"
                LicenseState.ExpiringSoon -> "Por vencer"
                LicenseState.PendingActivation -> "Pendiente"
                LicenseState.Expired -> "Expirada"
                LicenseState.GracePeriod -> "Periodo de gracia"
                LicenseState.Suspended -> "Suspendida"
            }
    }
}
