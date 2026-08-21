package com.contentfilter.user.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.BuildConfig

@Composable
fun UpdatesRoute(
    onBack: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.status == UpdatesStatus.Idle) viewModel.checkForUpdates()
    }
    UpdatesScreen(
        state = state,
        onCheck = viewModel::checkForUpdates,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installDownloadedUpdate,
        onInstallPermission = viewModel::openInstallPermissionSettings,
        onPrepareAdminInstall = viewModel::prepareAdminInstall,
        onInstallAdmin = viewModel::installDownloadedAdmin,
        onPrepareDagInstall = viewModel::prepareDagInstall,
        onInstallDag = viewModel::installDownloadedDag,
        onBack = onBack,
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
    onPrepareDagInstall: () -> Unit,
    onInstallDag: () -> Unit,
    onBack: () -> Unit,
) {
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
    ProductVisualPage(
        title = "Actualizaciones",
        subtitle = "Mantené Glosh y sus componentes al día",
        onBack = onBack,
    ) {
        ProductCard {
            Text("Glosh Usuario", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(state.status.message(), style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Versión ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Graphite,
                )
                TextButton(enabled = state.manifest != null, onClick = { showReleaseNotes = !showReleaseNotes }) {
                    Text(if (showReleaseNotes) "Ocultar novedades" else "Ver novedades")
                }
            }
            state.manifest?.let { manifest ->
                if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    Text(
                        text = "Disponible: ${manifest.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = GloshColors.Graphite,
                    )
                }
                if (showReleaseNotes) {
                    Text(
                        manifest.releaseNotes.ifBlank { "Sin novedades informadas." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = GloshColors.Muted,
                    )
                }
            }
            if (state.status == UpdatesStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryUpdateAction(
                state = state,
                onDownload = onDownload,
                onInstall = onInstall,
                onInstallPermission = onInstallPermission,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.status != UpdatesStatus.Checking && state.status != UpdatesStatus.Downloading,
                onClick = onCheck,
            ) {
                Text("Buscar actualizaciones")
            }
        }

        CompanionInstallCard(
            title = "Glosh Administrador",
            status = state.adminInstallStatus,
            progress = state.adminDownloadProgressPercent,
            onPrepare = onPrepareAdminInstall,
            onInstall = onInstallAdmin,
            onInstallPermission = onInstallPermission,
        )

        CompanionInstallCard(
            title = "Navegador protegido",
            status = state.dagInstallStatus,
            progress = state.dagDownloadProgressPercent,
            onPrepare = onPrepareDagInstall,
            onInstall = onInstallDag,
            onInstallPermission = onInstallPermission,
        )
    }
}

@Composable
private fun PrimaryUpdateAction(
    state: UpdatesUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
) {
    when (state.status) {
        UpdatesStatus.Available,
        UpdatesStatus.DownloadFailed,
        UpdatesStatus.ChecksumFailed,
        -> Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) { Text("Actualizar") }
        UpdatesStatus.Downloading -> Button(modifier = Modifier.fillMaxWidth(), enabled = false, onClick = {}) { Text("Actualizando…") }
        UpdatesStatus.NeedsInstallPermission -> {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) { Text("Dar permiso de instalación") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Continuar") }
        }
        UpdatesStatus.ReadyToInstall -> Button(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Instalar") }
        else -> Unit
    }
}

@Composable
private fun CompanionInstallCard(
    title: String,
    status: CompanionInstallStatus,
    progress: Int?,
    onPrepare: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
) {
    ProductCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        Text(status.message(title), style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
        if (status == CompanionInstallStatus.Downloading) {
            LinearProgressIndicator(
                progress = { (progress ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (status) {
            CompanionInstallStatus.ReadyToInstall ->
                Button(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Instalar") }
            CompanionInstallStatus.NeedsInstallPermission -> {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) { Text("Dar permiso de instalación") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Continuar") }
            }
            CompanionInstallStatus.Checking,
            CompanionInstallStatus.Downloading,
            -> Unit
            else -> OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPrepare) { Text("Comprobar") }
        }
    }
}

private fun UpdatesStatus.message(): String =
    when (this) {
        UpdatesStatus.Idle -> "Preparando comprobación."
        UpdatesStatus.Checking -> "Buscando actualizaciones…"
        UpdatesStatus.Available -> "Hay una versión nueva disponible."
        UpdatesStatus.UpToDate -> "Ya tenés la última versión."
        UpdatesStatus.NotConfigured -> "Las actualizaciones todavía no están configuradas."
        UpdatesStatus.SearchFailed -> "No se pudo buscar una actualización."
        UpdatesStatus.Downloading -> "Descargando y verificando la actualización…"
        UpdatesStatus.ReadyToInstall -> "La actualización está lista para instalar."
        UpdatesStatus.NeedsInstallPermission -> "Android necesita permiso para completar la instalación."
        UpdatesStatus.ChecksumFailed -> "La descarga no pasó la verificación de seguridad."
        UpdatesStatus.DownloadFailed -> "No se pudo descargar la actualización."
    }

internal fun UpdatesStatus.settingsSummary(): String = message()

private fun CompanionInstallStatus.message(appName: String): String =
    when (this) {
        CompanionInstallStatus.Idle -> "Podés comprobar si falta instalar o actualizar $appName."
        CompanionInstallStatus.Checking -> "Comprobando $appName…"
        CompanionInstallStatus.Downloading -> "Descargando y verificando $appName…"
        CompanionInstallStatus.ReadyToInstall -> "Listo para instalar. Android pedirá tu confirmación."
        CompanionInstallStatus.NeedsInstallPermission -> "Android necesita permiso para completar la instalación."
        CompanionInstallStatus.AlreadyInstalled -> "$appName ya está instalado y actualizado."
        CompanionInstallStatus.VerificationFailed -> "No se pudo verificar el instalador oficial."
        CompanionInstallStatus.Failed -> "No se pudo preparar $appName. Intentá nuevamente."
    }
