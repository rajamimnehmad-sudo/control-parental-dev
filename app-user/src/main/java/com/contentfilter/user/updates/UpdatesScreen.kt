package com.contentfilter.user.updates

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.user.BuildConfig

@Composable
fun UpdatesRoute(viewModel: UpdatesViewModel = hiltViewModel()) {
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
        onInstallPermission = viewModel::openInstallPermissionSettings,
        onResetLocalDataForRelink = viewModel::resetLocalDataForRelink,
        onClearDiagnostics = viewModel::clearDiagnostics,
    )
}

@Composable
private fun UpdatesScreen(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstallPermission: () -> Unit,
    onResetLocalDataForRelink: () -> Unit,
    onClearDiagnostics: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Actualizaciones",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = state.status.message(),
            style = MaterialTheme.typography.bodyLarge,
        )
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
        if (BuildConfig.FLAVOR == "dev") {
            Text("Herramientas DEV", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmReset = true },
            ) {
                Text("Resetear datos locales y reenlazar")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDiagnostics = true },
            ) {
                Text("Ver diagnóstico")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { clipboardManager.setText(AnnotatedString(state.diagnosticsText)) },
            ) {
                Text("Copiar diagnóstico")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClearDiagnostics,
            ) {
                Text("Limpiar diagnóstico")
            }
            if (state.devMessage.isNotBlank()) {
                Text(state.devMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Resetear datos locales") },
            text = {
                Text(
                    "Esto limpia Room, Outbox, cache, device local y activacion local. " +
                        "No borra Auth, Account ni datos remotos.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetLocalDataForRelink()
                    },
                ) {
                    Text("Resetear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Diagnóstico") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(state.diagnosticsText)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(state.diagnosticsText))
                        showDiagnostics = false
                    },
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text("Cerrar")
                }
            },
        )
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
