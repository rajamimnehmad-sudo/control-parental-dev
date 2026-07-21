package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.delay

@Composable
internal fun UsersListContent(
    entryMode: RulesEntryMode,
    createUserRequestKey: Int,
    onCreateUserRequestConsumed: () -> Unit,
    state: RulesUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onBack: (() -> Unit)?,
    onRefreshDevices: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onPairingCodeCopied: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onShowArchivedUsers: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(createUserRequestKey) {
        if (createUserRequestKey > 0 && entryMode == RulesEntryMode.ManageUsers) {
            showCreateDialog = true
            onCreateUserRequestConsumed()
        }
    }
    var userSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val filteredDevices =
        remember(state.userDevices, userSearchQuery) {
            val normalized = userSearchQuery.trim().lowercase()
            if (normalized.isBlank()) {
                state.userDevices
            } else {
                state.userDevices.filter { device ->
                    device.name.lowercase().contains(normalized) ||
                        device.lastSeenLabel.lowercase().contains(normalized) ||
                        device.status.label.lowercase().contains(normalized)
                }
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White),
    ) {
        UsersGlassHeader(
            entryMode = entryMode,
            searchQuery = userSearchQuery,
            searchExpanded = searchExpanded,
            totalCount = state.userDevices.size,
            refreshStatus = deviceRefreshStatusText(state),
            refreshStatusIsError = state.devicesRefreshError != null,
            onSearchChanged = { userSearchQuery = it },
            onSearchExpandedChanged = { expanded ->
                searchExpanded = expanded
                if (!expanded) userSearchQuery = ""
            },
            onCreateUser = { showCreateDialog = true },
            onRefresh = onRefreshDevices,
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 18.dp,
                ),
        ) {
            if (filteredDevices.isEmpty()) {
                item {
                    EmptySectionText(
                        if (state.userDevices.isEmpty()) {
                            "Tocá el botón + para crear el primer token de usuario."
                        } else {
                            "No hay usuarios que coincidan con la búsqueda."
                        },
                    )
                }
            }
            items(filteredDevices, key = { it.id }) { device ->
                ProtectedUserCard(
                    device = device,
                    onClick = { onDeviceSelected(device.id) },
                )
            }
            if (entryMode == RulesEntryMode.ManageUsers) {
                item(key = "archived-users") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onShowArchivedUsers,
                    ) {
                        Text("Ver usuarios anteriores")
                    }
                }
            }
        }
    }

    if (showCreateDialog && entryMode == RulesEntryMode.ManageUsers) {
        NewUserDialog(
            state = state,
            onDismiss = { showCreateDialog = false },
            onPairingUserNameChanged = onPairingUserNameChanged,
            onGeneratePairingCode = onGeneratePairingCode,
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(state.pairingCode))
                onPairingCodeCopied()
            },
        )
    }
}

@Composable
private fun UsersGlassHeader(
    entryMode: RulesEntryMode,
    searchQuery: String,
    searchExpanded: Boolean,
    totalCount: Int,
    refreshStatus: String,
    refreshStatusIsError: Boolean,
    onSearchChanged: (String) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onCreateUser: () -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(start = 10.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            onBack?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = HeaderInk,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text =
                        when (entryMode) {
                            RulesEntryMode.Web -> "Web"
                            RulesEntryMode.ManageUsers -> "Administrar usuarios"
                            RulesEntryMode.Apps -> "Apps"
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    color = HeaderInk,
                )
                Text(
                    text =
                        when (entryMode) {
                            RulesEntryMode.Web -> "$totalCount total · elegir usuario para configurar Web"
                            RulesEntryMode.ManageUsers -> "$totalCount total · ver, agregar o borrar usuarios"
                            RulesEntryMode.Apps -> "$totalCount total · elegir usuario para configurar apps"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeaderMuted,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar protegido") },
                    singleLine = true,
                    leadingIcon = {
                        ProductGlyph(icon = ProductIcon.Search, color = HeaderMuted, modifier = Modifier.size(22.dp))
                    },
                    shape = RoundedCornerShape(18.dp),
                )
            } else {
                Text(
                    text = refreshStatus,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (refreshStatusIsError) CriticalRed else HeaderMuted,
                    maxLines = 2,
                )
            }
            HeaderIconButton(onClick = onRefresh) {
                ProductGlyph(
                    icon = ProductIcon.Refresh,
                    color = HeaderMuted,
                    modifier = Modifier.size(22.dp).semantics { contentDescription = "Actualizar" },
                )
            }
            HeaderIconButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
                ProductGlyph(
                    icon = ProductIcon.Search,
                    color = HeaderInk,
                    modifier = Modifier.size(22.dp).semantics { contentDescription = "Buscar usuario" },
                )
            }
            if (entryMode == RulesEntryMode.ManageUsers) {
                UserCreateButton(onClick = onCreateUser)
            }
        }
    }
}

@Composable
private fun NewUserDialog(
    state: RulesUiState,
    onDismiss: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onCopyToken: () -> Unit,
) {
    val hasToken = state.pairingCode.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(hasToken) {
        if (!hasToken) focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasToken) "Token de usuario" else "Nuevo usuario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (hasToken) {
                        "Compartí este token para enlazar el celular protegido."
                    } else {
                        "Creá un token para enlazar un celular protegido."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasToken) {
                    TokenReadyCard(
                        code = state.pairingCode,
                        expiresAt = state.pairingExpiresAt,
                        onCopy = onCopyToken,
                    )
                } else {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        value = state.pairingUserName,
                        onValueChange = onPairingUserNameChanged,
                        label = { Text("Nombre del usuario") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (!hasToken) {
                ProgressActionButton(
                    onClick = onGeneratePairingCode,
                    enabled = !state.pairingLoading,
                    loading = state.pairingLoading,
                    loadingText = "Generando...",
                    successText = "Token listo",
                    text = "Generar",
                    modifier = Modifier,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
internal fun TokenReadyCard(
    code: String,
    expiresAt: String,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip("Token listo", MaterialTheme.colorScheme.secondary)
            Text(code, style = MaterialTheme.typography.headlineMedium, color = HeaderInk)
            if (expiresAt.isNotBlank()) {
                Text("Vence: $expiresAt", style = MaterialTheme.typography.bodySmall, color = HeaderMuted)
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopy,
            ) {
                Text("Copiar token")
            }
        }
    }
}

@Composable
private fun ProtectedUserCard(
    device: UserDeviceUiState,
    onClick: () -> Unit,
) {
    val healthy = device.status == UserDeviceStatus.Active && device.protectionComplete
    val attentionLevel = device.securityAttentionLevel()
    ProductListRow(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        leading = { UserAvatar(name = device.name, color = HeaderInk) },
        headline = {
            Text(device.name, style = MaterialTheme.typography.titleMedium, color = HeaderInk)
        },
        supporting = {
            Text(
                text = device.listSummary(healthy),
                style = MaterialTheme.typography.bodySmall,
                color = if (attentionLevel == SecurityAttentionLevel.Critical) CriticalRed else HeaderMuted,
                maxLines = 2,
            )
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecurityAttentionGlyph(level = attentionLevel)
                ProductGlyph(icon = ProductIcon.ChevronRight, color = HeaderMuted, modifier = Modifier.size(22.dp))
            }
        },
    )
}

@Composable
internal fun SecurityAttentionGlyph(
    level: SecurityAttentionLevel,
    modifier: Modifier = Modifier,
) {
    if (level == SecurityAttentionLevel.None) return
    val description =
        when (level) {
            SecurityAttentionLevel.Critical -> "Error de seguridad"
            SecurityAttentionLevel.Warning -> "Seguridad pendiente de verificar"
            SecurityAttentionLevel.None -> return
        }
    ProductGlyph(
        icon = ProductIcon.ShieldAlert,
        color = level.color,
        modifier = modifier.size(18.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun UserCreateButton(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Nuevo usuario" },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HeaderInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(
                icon = ProductIcon.UserPlus,
                color = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun UserAvatar(
    name: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
    }
}

private fun UserDeviceUiState.listSummary(healthy: Boolean): String =
    if (healthy) {
        "$lastSeenLabel · $appCount apps"
    } else {
        protectionAlert
            ?: when (status) {
                UserDeviceStatus.Unprotected -> "La protección requiere atención"
                UserDeviceStatus.Inactive -> "Sin comunicación reciente"
                UserDeviceStatus.Unknown -> "Configuración pendiente"
                UserDeviceStatus.Active -> "Falta completar la protección"
            }
    }

@Composable
private fun deviceRefreshStatusText(state: RulesUiState): String {
    var nowEpochMillis by remember(state.devicesLastRefreshedAtEpochMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(state.devicesLastRefreshedAtEpochMillis) {
        if (state.devicesLastRefreshedAtEpochMillis != null) {
            while (true) {
                delay(60_000)
                nowEpochMillis = System.currentTimeMillis()
            }
        }
    }
    return state.deviceRefreshStatusText(nowEpochMillis)
}

internal fun RulesUiState.deviceRefreshStatusText(nowEpochMillis: Long): String =
    when {
        devicesRefreshing -> "Actualizando…"
        devicesRefreshError != null -> "No se pudo actualizar"
        devicesLastRefreshedAtEpochMillis != null -> {
            val elapsedMinutes = ((nowEpochMillis - devicesLastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
            if (elapsedMinutes == 0L) "Actualizado ahora" else "Actualizado hace $elapsedMinutes min"
        }
        offlineMode -> "Datos guardados"
        else -> "Listo para actualizar"
    }

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Activo"
            UserDeviceStatus.Unprotected -> "Protección caída"
            UserDeviceStatus.Inactive -> "Sin comunicación"
            UserDeviceStatus.Unknown -> "Pendiente"
        }

internal enum class SecurityAttentionLevel {
    None,
    Warning,
    Critical,
}

internal fun UserDeviceUiState.securityAttentionLevel(): SecurityAttentionLevel =
    when {
        possibleUninstall || confirmedProtectionFailure -> SecurityAttentionLevel.Critical
        protectionVerificationPending || status == UserDeviceStatus.Inactive || status == UserDeviceStatus.Unknown ->
            SecurityAttentionLevel.Warning
        else -> SecurityAttentionLevel.None
    }

internal val SecurityAttentionLevel.color: Color
    get() =
        when (this) {
            SecurityAttentionLevel.Critical -> CriticalRed
            SecurityAttentionLevel.Warning -> PendingYellow
            SecurityAttentionLevel.None -> Color.Transparent
        }

internal fun UserDeviceUiState.detailAttentionSummary(): String =
    protectionAlert
        ?: when (status) {
            UserDeviceStatus.Unprotected -> "Hay componentes de protección que requieren atención"
            UserDeviceStatus.Inactive -> "No hay comunicación reciente con el dispositivo"
            UserDeviceStatus.Unknown -> "Todavía falta verificar la configuración"
            UserDeviceStatus.Active -> "Falta completar la configuración de protección"
        }

internal val AdminSurface = Color(0xFFF2F8F7)
internal val HeaderInk = Color(0xFF162235)
internal val HeaderMuted = Color(0xFF68758A)
internal val ActiveGreen = Color(0xFF00A650)
internal val CriticalRed = Color(0xFFB00020)
internal val PendingYellow = Color(0xFFFFC849)
