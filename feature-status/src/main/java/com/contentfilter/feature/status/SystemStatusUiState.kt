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
    val syncState: String,
    val activationState: String,
    val appVersion: String,
    val isVpnActive: Boolean,
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
        fun from(snapshot: SystemHealthSnapshot): SystemStatusUiState =
            SystemStatusUiState(
                title = "Estado del sistema",
                protectionLevel = snapshot.protectionLevel,
                summary = "VPN ${snapshot.vpnState.displayName()}, Accesibilidad ${snapshot.accessibilityState.displayName()}",
                vpnState = snapshot.vpnState.displayName(),
                accessibilityState = snapshot.accessibilityState.displayName(),
                syncState = snapshot.syncState.displayName(),
                activationState = snapshot.licenseState.displayName(),
                appVersion = "1.0.0",
                isVpnActive = snapshot.vpnState == ComponentState.Enabled,
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
                LicenseState.ExpiringSoon -> "Por vencer"
                LicenseState.PendingActivation -> "Pendiente"
                LicenseState.Expired -> "Expirada"
                LicenseState.GracePeriod -> "Periodo de gracia"
                LicenseState.Suspended -> "Suspendida"
            }
    }
}
