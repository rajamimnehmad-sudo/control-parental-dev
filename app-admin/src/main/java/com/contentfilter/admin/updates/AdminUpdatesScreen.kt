package com.contentfilter.admin.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.ui.PremiumFeedbackBanner

@Composable
fun AdminUpdatesRoute(viewModel: AdminUpdatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (state.status == AdminUpdatesStatus.Idle) {
            viewModel.checkForUpdates()
        }
    }
    AdminUpdatesScreen(
        state = state,
        onCheck = viewModel::checkForUpdates,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installDownloadedUpdate,
        onInstallPermission = viewModel::openInstallPermissionSettings,
        onRequestReset = viewModel::requestResetLocalAdmin,
        onDismissReset = viewModel::dismissResetLocalAdmin,
        onConfirmReset = viewModel::resetLocalAdmin,
    )
}

@Composable
private fun AdminUpdatesScreen(
    state: AdminUpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onInstallPermission: () -> Unit,
    onRequestReset: () -> Unit,
    onDismissReset: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    if (state.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text("Cambiar administrador") },
            text = {
                Text(
                    "Se borrará el admin guardado solo en este teléfono. Después podrás ingresar un token nuevo desde Login.",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmReset) {
                    Text("Resetear")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissReset) {
                    Text("Cancelar")
                }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumFeedbackBanner(
            text = state.resetMessage.ifBlank { state.status.message() },
            isError = state.resetMessage.startsWith("No se pudo") || (state.resetMessage.isBlank() && state.status.isError()),
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Version instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
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
            Spacer(modifier = Modifier.height(8.dp))
            when (state.status) {
                AdminUpdatesStatus.Available,
                AdminUpdatesStatus.DownloadFailed,
                AdminUpdatesStatus.ChecksumFailed,
                -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDownload,
                    ) {
                        Text("Actualizar")
                    }
                }
                AdminUpdatesStatus.Downloading -> {
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
                AdminUpdatesStatus.NeedsInstallPermission -> {
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
                AdminUpdatesStatus.ReadyToInstall -> {
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
                    state.status != AdminUpdatesStatus.Checking &&
                        state.status != AdminUpdatesStatus.Downloading,
                onClick = onCheck,
            ) {
                Text("Buscar actualizacion")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Administrador de este teléfono",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Usá esta opción si quedó un admin viejo y necesitás ingresar un token nuevo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestReset,
            ) {
                Text("Cambiar administrador")
            }
        }
    }
}

private fun AdminUpdatesStatus.message(): String =
    when (this) {
        AdminUpdatesStatus.Idle -> "Preparando busqueda."
        AdminUpdatesStatus.Checking -> "Buscando actualizacion."
        AdminUpdatesStatus.Available -> "Hay una version nueva disponible."
        AdminUpdatesStatus.UpToDate -> "Ya tenes la ultima version."
        AdminUpdatesStatus.NotConfigured -> "No hay manifiesto de actualizacion configurado."
        AdminUpdatesStatus.SearchFailed -> "No se pudo buscar actualizacion."
        AdminUpdatesStatus.Downloading -> "Actualizando. Descargando y verificando el APK."
        AdminUpdatesStatus.ReadyToInstall -> "Descarga verificada. Confirma la instalacion en Android."
        AdminUpdatesStatus.NeedsInstallPermission -> "Android requiere permiso para instalar APKs desde esta app."
        AdminUpdatesStatus.ChecksumFailed -> "La descarga no paso la verificacion SHA-256."
        AdminUpdatesStatus.DownloadFailed -> "No se pudo descargar la actualizacion."
    }

private fun AdminUpdatesStatus.isError(): Boolean =
    this == AdminUpdatesStatus.SearchFailed ||
        this == AdminUpdatesStatus.DownloadFailed ||
        this == AdminUpdatesStatus.ChecksumFailed

private fun AdminUpdatesStatus.versionLabel(): String =
    when (this) {
        AdminUpdatesStatus.ReadyToInstall,
        AdminUpdatesStatus.NeedsInstallPermission,
        -> "Ultima version descargada"
        else -> "Version disponible"
    }
