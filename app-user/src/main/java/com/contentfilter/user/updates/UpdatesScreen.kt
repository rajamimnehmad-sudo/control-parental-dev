package com.contentfilter.user.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductStatCard
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.BuildConfig

@Composable
fun UpdatesRoute(
    onBack: (() -> Unit)? = null,
    protectionSummary: String = "",
    communityName: String = "",
    guideName: String = "",
    vpnState: String = "",
    accessibilityState: String = "",
    deviceAdminState: String = "",
    syncState: String = "",
    activationState: String = "",
    batteryOptimizationExempt: Boolean = false,
    protectionArmed: Boolean = false,
    settingsAuthorized: Boolean = false,
    removalAuthorized: Boolean = false,
    recoveryAvailable: Boolean = false,
    recoveryCode: String = "",
    protectionMessage: String = "",
    protectionRefreshing: Boolean = false,
    onActivateDeviceAdmin: () -> Unit = {},
    onActivateAccessibility: () -> Unit = {},
    onActivateVpn: () -> Unit = {},
    onRequestBatteryOptimizationExemption: () -> Unit = {},
    onProtectionRefresh: () -> Unit = {},
    onRequestMaintenance: () -> Unit = {},
    onRecoveryCodeChanged: (String) -> Unit = {},
    onSubmitRecoveryCode: () -> Unit = {},
    onCancelRemovalAuthorization: () -> Unit = {},
    onAuthorizedRemoval: () -> Unit = {},
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.status == UpdatesStatus.Idle) {
            viewModel.checkForUpdates()
        }
    }
    UpdatesScreen(
        state = state,
        onCheck = viewModel::checkForUpdates,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installDownloadedUpdate,
        onInstallPermission = viewModel::openInstallPermissionSettings,
        onPrepareAdminInstall = viewModel::prepareAdminInstall,
        onInstallAdmin = viewModel::installDownloadedAdmin,
        onBack = onBack,
        protectionSummary = protectionSummary,
        communityName = communityName,
        guideName = guideName,
        vpnState = vpnState,
        accessibilityState = accessibilityState,
        deviceAdminState = deviceAdminState,
        syncState = syncState,
        activationState = activationState,
        batteryOptimizationExempt = batteryOptimizationExempt,
        protectionArmed = protectionArmed,
        settingsAuthorized = settingsAuthorized,
        removalAuthorized = removalAuthorized,
        recoveryAvailable = recoveryAvailable,
        recoveryCode = recoveryCode,
        protectionMessage = protectionMessage,
        protectionRefreshing = protectionRefreshing,
        onActivateDeviceAdmin = onActivateDeviceAdmin,
        onActivateAccessibility = onActivateAccessibility,
        onActivateVpn = onActivateVpn,
        onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
        onProtectionRefresh = onProtectionRefresh,
        onRequestMaintenance = onRequestMaintenance,
        onRecoveryCodeChanged = onRecoveryCodeChanged,
        onSubmitRecoveryCode = onSubmitRecoveryCode,
        onCancelRemovalAuthorization = onCancelRemovalAuthorization,
        onAuthorizedRemoval = onAuthorizedRemoval,
    )
}

@Composable
private fun UpdatesScreen(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
    onPrepareAdminInstall: () -> Unit,
    onInstallAdmin: () -> Unit,
    onBack: (() -> Unit)?,
    protectionSummary: String,
    communityName: String,
    guideName: String,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    syncState: String,
    activationState: String,
    batteryOptimizationExempt: Boolean,
    protectionArmed: Boolean,
    settingsAuthorized: Boolean,
    removalAuthorized: Boolean,
    recoveryAvailable: Boolean,
    recoveryCode: String,
    protectionMessage: String,
    protectionRefreshing: Boolean,
    onActivateDeviceAdmin: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateVpn: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onProtectionRefresh: () -> Unit,
    onRequestMaintenance: () -> Unit,
    onRecoveryCodeChanged: (String) -> Unit,
    onSubmitRecoveryCode: () -> Unit,
    onCancelRemovalAuthorization: () -> Unit,
    onAuthorizedRemoval: () -> Unit,
) {
    ProductVisualPage(
        title = "Ajustes",
        subtitle = "Protección y versión instalada ${BuildConfig.VERSION_CODE}",
        onBack = onBack,
    ) {
        PremiumFeedbackBanner(
            text = state.status.message(),
            isError =
                state.status == UpdatesStatus.SearchFailed ||
                    state.status == UpdatesStatus.DownloadFailed ||
                    state.status == UpdatesStatus.ChecksumFailed,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductStatCard(
                modifier = Modifier.weight(1f),
                value = BuildConfig.VERSION_CODE.toString(),
                label = "versión",
                accent = ProductViolet,
            )
            ProductStatCard(
                modifier = Modifier.weight(1f),
                value = activationState.ifBlank { "..." },
                label = "activación",
                accent = ProductMint,
            )
        }
        ProductLargeFeatureCard(
            title = "Protección",
            subtitle =
                if (protectionArmed) {
                    "Protección reforzada activa. ${protectionSummary.ifBlank { "Componentes verificados." }}"
                } else {
                    "Protección reforzada pendiente. ${protectionSummary.ifBlank { "Completá las barreras del dispositivo." }}"
                },
            accent = ProductSky,
        )
        ProductCard {
            Text("Estado del dispositivo", style = MaterialTheme.typography.titleMedium)
            if (communityName.isNotBlank()) {
                Text("Comunidad: $communityName", style = MaterialTheme.typography.bodyMedium)
            }
            if (guideName.isNotBlank()) {
                Text("Guía: $guideName", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Accesibilidad: ${accessibilityState.ifBlank { "Desconocida" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Protección contra desinstalación: ${deviceAdminState.ifBlank { "Desconocida" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("VPN: ${vpnState.ifBlank { "Desconocida" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Sincronización: ${syncState.ifBlank { "Desconocida" }}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Activación: ${activationState.ifBlank { "Desconocida" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Control reforzado: ${if (protectionArmed) "Activo" else "Pendiente"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Batería: ${if (batteryOptimizationExempt) "Sin restricciones" else "Optimizada"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (settingsAuthorized) {
                Text(
                    "Mantenimiento temporal autorizado",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        ProductCard {
            Text("Mantenimiento y recuperación", style = MaterialTheme.typography.titleMedium)
            if (accessibilityState != "Activa") {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onActivateAccessibility) {
                    Text("Activar accesibilidad")
                }
            }
            if (vpnState != "Activa") {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onActivateVpn) {
                    Text("Activar protección web")
                }
            }
            if (deviceAdminState != "Activa") {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onActivateDeviceAdmin) {
                    Text("Activar protección contra desinstalación")
                }
            }
            if (!batteryOptimizationExempt) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestBatteryOptimizationExemption,
                ) {
                    Text("Permitir funcionamiento continuo")
                }
            }
            if (activationState in setOf("Expirada", "Suspendida", "Programada", "Pendiente")) {
                Text(
                    when (activationState) {
                        "Expirada" -> "La licencia venció. La configuración se conserva y volverá al renovar."
                        "Suspendida" -> "La comunidad suspendió temporalmente la licencia."
                        "Programada" -> "La licencia todavía no comenzó."
                        else -> "Este dispositivo todavía no completó la activación."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val primaryAction =
                protectionPrimaryAction(
                    protectionArmed = protectionArmed,
                    vpnState = vpnState,
                    accessibilityState = accessibilityState,
                    deviceAdminState = deviceAdminState,
                    batteryOptimizationExempt = batteryOptimizationExempt,
                )
            if (primaryAction == ProtectionPrimaryAction.Repair) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !protectionRefreshing,
                    onClick = onProtectionRefresh,
                ) {
                    Text(if (protectionRefreshing) "Revisando protección..." else "Reparar protección")
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestMaintenance,
                ) {
                    Text("Solicitar acceso temporal")
                }
            }
            if (recoveryAvailable) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = recoveryCode,
                    onValueChange = onRecoveryCodeChanged,
                    label = { Text("Código de recuperación") },
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = recoveryCode.isNotBlank(),
                    onClick = onSubmitRecoveryCode,
                ) {
                    Text("Validar código")
                }
            }
            if (removalAuthorized) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onAuthorizedRemoval) {
                    Text("Desinstalar con autorización")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCancelRemovalAuthorization) {
                    Text("Cancelar autorización")
                }
            }
            if (protectionMessage.isNotBlank()) {
                Text(protectionMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ProductLargeFeatureCard(
            title = "Actualizaciones",
            subtitle = "Buscá nuevas versiones DEV y completá la instalación desde Android.",
            accent = ProductSky,
        )
        ProductCard {
            Text(
                text = "Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.titleMedium,
            )
            state.manifest?.let { manifest ->
                Text(
                    text = "${state.status.versionLabel()}: ${manifest.versionName} (${manifest.versionCode})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = manifest.releaseNotes.ifBlank { "Sin notas de version." },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.status == UpdatesStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (state.status) {
            UpdatesStatus.Available,
            UpdatesStatus.DownloadFailed,
            UpdatesStatus.ChecksumFailed,
            -> {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDownload,
                ) {
                    Text("Actualizar")
                }
            }
            UpdatesStatus.Downloading -> {
                state.downloadProgressPercent?.let { progress ->
                    Text("Descarga: $progress%", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    onClick = onDownload,
                ) {
                    Text("Actualizando...")
                }
            }
            UpdatesStatus.NeedsInstallPermission -> {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onInstallPermission,
                ) {
                    Text("Permitir instalacion")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onInstall,
                ) {
                    Text("Instalar")
                }
            }
            UpdatesStatus.ReadyToInstall -> {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onInstall,
                ) {
                    Text("Instalar")
                }
            }
            else -> Unit
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled =
                state.status != UpdatesStatus.Checking &&
                    state.status != UpdatesStatus.Downloading,
            onClick = onCheck,
        ) {
            Text("Buscar actualizacion")
        }
        ProductLargeFeatureCard(
            title = "App Administrador",
            subtitle = "Instalá la app oficial desde un APK verificado, sin habilitar descargas arbitrarias.",
            accent = ProductMint,
        )
        ProductCard {
            Text(
                text = state.adminInstallStatus.message(),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.adminInstallStatus == AdminInstallStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.adminDownloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state.adminInstallStatus) {
                AdminInstallStatus.ReadyToInstall -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallAdmin) {
                        Text("Instalar App Administrador")
                    }
                }
                AdminInstallStatus.NeedsInstallPermission -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) {
                        Text("Permitir instalación oficial")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstallAdmin) {
                        Text("Continuar instalación")
                    }
                }
                AdminInstallStatus.Checking,
                AdminInstallStatus.Downloading,
                -> Unit
                else -> {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPrepareAdminInstall) {
                        Text("Comprobar e instalar Admin")
                    }
                }
            }
        }
    }
}

internal enum class ProtectionPrimaryAction {
    Repair,
    RequestTemporaryAccess,
}

internal fun protectionPrimaryAction(
    protectionArmed: Boolean,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    batteryOptimizationExempt: Boolean,
): ProtectionPrimaryAction =
    if (
        protectionArmed &&
        vpnState == "Activa" &&
        accessibilityState == "Activa" &&
        deviceAdminState == "Activa" &&
        batteryOptimizationExempt
    ) {
        ProtectionPrimaryAction.RequestTemporaryAccess
    } else {
        ProtectionPrimaryAction.Repair
    }

private fun UpdatesStatus.message(): String =
    when (this) {
        UpdatesStatus.Idle -> "Preparando busqueda."
        UpdatesStatus.Checking -> "Buscando actualizacion."
        UpdatesStatus.Available -> "Hay una version nueva disponible."
        UpdatesStatus.UpToDate -> "Ya tenes la ultima version."
        UpdatesStatus.NotConfigured -> "No hay manifiesto de actualizacion configurado."
        UpdatesStatus.SearchFailed -> "No se pudo buscar actualizacion."
        UpdatesStatus.Downloading -> "Actualizando. Descargando y verificando el APK."
        UpdatesStatus.ReadyToInstall -> "Descarga verificada. Confirma la instalacion en Android."
        UpdatesStatus.NeedsInstallPermission -> "Android requiere permiso para instalar APKs desde esta app."
        UpdatesStatus.ChecksumFailed -> "La descarga no paso la verificacion SHA-256."
        UpdatesStatus.DownloadFailed -> "No se pudo descargar la actualizacion."
    }

private fun UpdatesStatus.versionLabel(): String =
    when (this) {
        UpdatesStatus.ReadyToInstall,
        UpdatesStatus.NeedsInstallPermission,
        -> "Ultima version descargada"
        else -> "Version disponible"
    }

private fun AdminInstallStatus.message(): String =
    when (this) {
        AdminInstallStatus.Idle -> "Comprobá la versión oficial de App Administrador."
        AdminInstallStatus.Checking -> "Comprobando App Administrador."
        AdminInstallStatus.Downloading -> "Descargando y verificando App Administrador."
        AdminInstallStatus.ReadyToInstall -> "APK oficial verificado. Android pedirá confirmación para instalar."
        AdminInstallStatus.NeedsInstallPermission -> "Android requiere autorizar a Content Filter como instalador."
        AdminInstallStatus.AlreadyInstalled -> "App Administrador ya está instalada y actualizada."
        AdminInstallStatus.VerificationFailed -> "El APK no coincide con el manifiesto o la firma de Content Filter."
        AdminInstallStatus.Failed -> "No se pudo preparar App Administrador. Intentá nuevamente."
    }
