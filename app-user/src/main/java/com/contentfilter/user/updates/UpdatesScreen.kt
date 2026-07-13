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
    syncState: String = "",
    activationState: String = "",
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
        onBack = onBack,
        protectionSummary = protectionSummary,
        communityName = communityName,
        guideName = guideName,
        vpnState = vpnState,
        accessibilityState = accessibilityState,
        syncState = syncState,
        activationState = activationState,
    )
}

@Composable
private fun UpdatesScreen(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
    onBack: (() -> Unit)?,
    protectionSummary: String,
    communityName: String,
    guideName: String,
    vpnState: String,
    accessibilityState: String,
    syncState: String,
    activationState: String,
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
            subtitle = protectionSummary.ifBlank { "Resumen del dispositivo protegido." },
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
            Text("VPN: ${vpnState.ifBlank { "Desconocida" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Sincronización: ${syncState.ifBlank { "Desconocida" }}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Activación: ${activationState.ifBlank { "Desconocida" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
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
    }
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
