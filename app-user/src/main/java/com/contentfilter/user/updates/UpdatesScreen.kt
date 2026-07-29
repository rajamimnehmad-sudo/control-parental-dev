package com.contentfilter.user.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.BuildConfig

@Composable
fun UpdatesRoute(
    onBack: (() -> Unit)? = null,
    onHelp: () -> Unit = {},
    activationState: String = "",
    recoveryCode: String = "",
    protectionMessage: String = "",
    onRecoveryCodeChanged: (String) -> Unit = {},
    onSubmitRecoveryCode: () -> Unit = {},
    viewModel: UpdatesViewModel = hiltViewModel(),
    feedbackViewModel: UserFeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedbackState by feedbackViewModel.state.collectAsStateWithLifecycle()
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
        onPrepareDagInstall = viewModel::prepareDagInstall,
        onInstallDag = viewModel::installDownloadedDag,
        onBack = onBack,
        onHelp = onHelp,
        activationState = activationState,
        recoveryCode = recoveryCode,
        protectionMessage = protectionMessage,
        onRecoveryCodeChanged = onRecoveryCodeChanged,
        onSubmitRecoveryCode = onSubmitRecoveryCode,
        feedbackState = feedbackState,
        onSubmitFeedback = feedbackViewModel::submit,
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
    onBack: (() -> Unit)?,
    onHelp: () -> Unit,
    activationState: String,
    recoveryCode: String,
    protectionMessage: String,
    onRecoveryCodeChanged: (String) -> Unit,
    onSubmitRecoveryCode: () -> Unit,
    feedbackState: FeedbackUiState,
    onSubmitFeedback: (Int, String) -> Unit,
) {
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
    var rating by rememberSaveable { mutableStateOf(0) }
    var ratingComment by rememberSaveable { mutableStateOf("") }
    ProductVisualPage(
        title = "Ajustes",
        subtitle = "Versión, actualización y acceso de emergencia",
        onBack = onBack,
    ) {
        ProductListSurface {
            ProductListRow(
                leading = { ProductGlyph(ProductIcon.Update, MaterialTheme.colorScheme.primary, Modifier.size(24.dp)) },
                headline = { Text("Estado de actualización", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text(state.status.message(), style = MaterialTheme.typography.bodyMedium) },
            )
            ProductListRow(
                leading = {
                    ProductGlyph(
                        ProductIcon.Settings,
                        MaterialTheme.colorScheme.primary,
                        Modifier.size(24.dp),
                    )
                },
                headline = { Text("Versión instalada", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            )
            ProductListRow(
                leading = {
                    ProductGlyph(
                        ProductIcon.ShieldCheck,
                        MaterialTheme.colorScheme.primary,
                        Modifier.size(24.dp),
                    )
                },
                headline = { Text("Activación", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text(activationState.ifBlank { "Revisando…" }) },
                showDivider = false,
            )
        }
        ProductCard {
            Text("Valorar App Usuario", style = MaterialTheme.typography.titleMedium)
            Text("Tu calificación ayuda a mejorar la aplicación.", style = MaterialTheme.typography.bodyMedium)
            Row {
                (1..5).forEach { value ->
                    IconButton(onClick = { rating = value }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "$value estrellas",
                            tint =
                                if (value <= rating) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                        )
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = ratingComment,
                onValueChange = { if (it.length <= 1000) ratingComment = it },
                label = { Text("Comentario opcional") },
                minLines = 3,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = rating > 0 && !feedbackState.saving,
                onClick = { onSubmitFeedback(rating, ratingComment) },
            ) {
                Text(if (feedbackState.saving) "Enviando…" else "Enviar valoración")
            }
            if (feedbackState.message.isNotBlank()) {
                Text(feedbackState.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ProductCard {
            Text("Código de emergencia", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ingresalo solamente si el administrador te dio un código para autorizar una desinstalación sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = recoveryCode,
                onValueChange = onRecoveryCodeChanged,
                label = { Text("Código de emergencia") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = recoveryCode.isNotBlank(),
                onClick = onSubmitRecoveryCode,
            ) {
                Text("Validar código")
            }
            if (protectionMessage.isNotBlank()) {
                Text(protectionMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ProductListSurface {
            ProductListRow(
                leading = { ProductGlyph(ProductIcon.Search, MaterialTheme.colorScheme.primary, Modifier.size(24.dp)) },
                headline = { Text("Ayuda", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text("Conversá con el asistente según el estado actual") },
                trailing = {
                    ProductGlyph(
                        ProductIcon.ChevronRight,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.size(22.dp),
                    )
                },
                onClick = onHelp,
                showDivider = false,
            )
        }
        Text("Actualizaciones", style = MaterialTheme.typography.titleSmall)
        ProductCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    enabled = state.manifest != null,
                    onClick = { showReleaseNotes = !showReleaseNotes },
                ) {
                    Text(if (showReleaseNotes) "Ocultar" else "Ver novedades")
                }
            }
            state.manifest?.let { manifest ->
                if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    Text(
                        text = "${state.status.versionLabel()}: ${manifest.versionName} (${manifest.versionCode})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (showReleaseNotes) {
                    Text(text = "Últimos cambios", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = manifest.releaseNotes.ifBlank { "Sin novedades informadas." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
        Text("App Administrador", style = MaterialTheme.typography.titleSmall)
        ProductCard {
            Text(
                text = state.adminInstallStatus.message("App Administrador"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.adminInstallStatus == CompanionInstallStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.adminDownloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state.adminInstallStatus) {
                CompanionInstallStatus.ReadyToInstall -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallAdmin) {
                        Text("Instalar App Administrador")
                    }
                }
                CompanionInstallStatus.NeedsInstallPermission -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) {
                        Text("Permitir instalación oficial")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstallAdmin) {
                        Text("Continuar instalación")
                    }
                }
                CompanionInstallStatus.Checking,
                CompanionInstallStatus.Downloading,
                -> Unit
                else -> {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPrepareAdminInstall) {
                        Text("Comprobar e instalar Admin")
                    }
                }
            }
        }
        Text("Navegador DAG", style = MaterialTheme.typography.titleSmall)
        ProductCard {
            Text(
                text = state.dagInstallStatus.message("Navegador DAG"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.dagInstallStatus == CompanionInstallStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.dagDownloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state.dagInstallStatus) {
                CompanionInstallStatus.ReadyToInstall -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallDag) {
                        Text("Instalar Navegador DAG")
                    }
                }
                CompanionInstallStatus.NeedsInstallPermission -> {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onInstallPermission) {
                        Text("Permitir instalación oficial")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onInstallDag) {
                        Text("Continuar instalación")
                    }
                }
                CompanionInstallStatus.Checking,
                CompanionInstallStatus.Downloading,
                -> Unit
                else -> {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPrepareDagInstall) {
                        Text("Comprobar e instalar DAG")
                    }
                }
            }
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

private fun CompanionInstallStatus.message(appName: String): String =
    when (this) {
        CompanionInstallStatus.Idle -> "Comprobá la versión oficial de $appName."
        CompanionInstallStatus.Checking -> "Comprobando $appName."
        CompanionInstallStatus.Downloading -> "Descargando y verificando $appName."
        CompanionInstallStatus.ReadyToInstall -> "APK oficial verificado. Android pedirá confirmación para instalar."
        CompanionInstallStatus.NeedsInstallPermission -> "Android requiere autorizar a Content Filter como instalador."
        CompanionInstallStatus.AlreadyInstalled -> "$appName ya está instalado y actualizado."
        CompanionInstallStatus.VerificationFailed -> "El APK no coincide con el manifiesto o la firma de Content Filter."
        CompanionInstallStatus.Failed -> "No se pudo preparar $appName. Intentá nuevamente."
    }
