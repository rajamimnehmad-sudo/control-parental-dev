package com.contentfilter.admin.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.PremiumFeedbackBanner

@Composable
fun AdminUpdatesRoute(viewModel: AdminUpdatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.status == AdminUpdatesStatus.Idle) viewModel.checkForUpdates()
    }
    AdminUpdatesScreen(
        state = state,
        onCheck = viewModel::checkForUpdates,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installDownloadedUpdate,
        onInstallPermission = viewModel::openInstallPermissionSettings,
    )
}

@Composable
private fun AdminUpdatesScreen(
    state: AdminUpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
) {
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.status.isError()) {
            PremiumFeedbackBanner(text = state.status.message(), isError = true)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProductCard {
                Text("Glosh Administrador", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
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
                        Text("Disponible: ${manifest.versionName}", style = MaterialTheme.typography.titleMedium)
                    }
                    if (showReleaseNotes) {
                        Text(
                            manifest.releaseNotes.ifBlank { "Sin novedades informadas." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = GloshColors.Muted,
                        )
                    }
                }
                if (state.status == AdminUpdatesStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { (state.downloadProgressPercent ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when (state.status) {
                    AdminUpdatesStatus.Available,
                    AdminUpdatesStatus.DownloadFailed,
                    AdminUpdatesStatus.ChecksumFailed,
                    -> Button(modifier = Modifier.fillMaxWidth(), onClick = onDownload) { Text("Actualizar") }
                    AdminUpdatesStatus.Downloading ->
                        Button(modifier = Modifier.fillMaxWidth(), enabled = false, onClick = {}) { Text("Actualizando…") }
                    AdminUpdatesStatus.NeedsInstallPermission -> {
                        Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) { Text("Dar permiso de instalación") }
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Continuar") }
                    }
                    AdminUpdatesStatus.ReadyToInstall ->
                        Button(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("Instalar") }
                    else -> Unit
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.status != AdminUpdatesStatus.Checking && state.status != AdminUpdatesStatus.Downloading,
                    onClick = onCheck,
                ) {
                    Text("Buscar actualizaciones")
                }
            }
        }
    }
}

@Composable
fun AdminLocalAccessRoute(viewModel: AdminUpdatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetLocalAdmin,
            title = { Text("Cambiar administrador") },
            text = {
                Text(
                    "Se quitará solamente el administrador guardado en este teléfono. La comunidad y sus datos no se borran; después vas a poder ingresar otro token.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::resetLocalAdmin) { Text("Quitar de este teléfono") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissResetLocalAdmin) { Text("Cancelar") }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.resetMessage.isNotBlank()) {
            PremiumFeedbackBanner(
                text = state.resetMessage,
                isError = state.resetMessage.startsWith("No se pudo"),
            )
        }
        ProductCard {
            Text("Administrador de este teléfono", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(
                "Desde acá podés quitar el administrador local para vincular otro. Esto no borra la comunidad ni a sus usuarios.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = viewModel::requestResetLocalAdmin) {
                Text("Cambiar administrador")
            }
        }
    }
}

private fun AdminUpdatesStatus.message(): String =
    when (this) {
        AdminUpdatesStatus.Idle -> "Preparando comprobación."
        AdminUpdatesStatus.Checking -> "Buscando actualizaciones…"
        AdminUpdatesStatus.Available -> "Hay una versión nueva disponible."
        AdminUpdatesStatus.UpToDate -> "Ya tenés la última versión."
        AdminUpdatesStatus.NotConfigured -> "Las actualizaciones todavía no están configuradas."
        AdminUpdatesStatus.SearchFailed -> "No se pudo buscar una actualización."
        AdminUpdatesStatus.Downloading -> "Descargando y verificando la actualización…"
        AdminUpdatesStatus.ReadyToInstall -> "La actualización está lista para instalar."
        AdminUpdatesStatus.NeedsInstallPermission -> "Android necesita permiso para completar la instalación."
        AdminUpdatesStatus.ChecksumFailed -> "La descarga no pasó la verificación de seguridad."
        AdminUpdatesStatus.DownloadFailed -> "No se pudo descargar la actualización."
    }

private fun AdminUpdatesStatus.isError(): Boolean =
    this == AdminUpdatesStatus.SearchFailed ||
        this == AdminUpdatesStatus.DownloadFailed ||
        this == AdminUpdatesStatus.ChecksumFailed
