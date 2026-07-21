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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

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
    onDeviceDeleted: (String) -> Unit,
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
                .background(AdminSurface),
    ) {
        UsersGlassHeader(
            entryMode = entryMode,
            searchQuery = userSearchQuery,
            searchExpanded = searchExpanded,
            totalCount = state.userDevices.size,
            onSearchChanged = { userSearchQuery = it },
            onSearchExpandedChanged = { expanded ->
                searchExpanded = expanded
                if (!expanded) userSearchQuery = ""
            },
            onCreateUser = { showCreateDialog = true },
            onRefresh = onRefreshDevices,
            onBack = onBack,
        )
        val bannerText = state.message.ifBlank { if (state.offlineMode) "Sin conexión. Mostrando datos guardados." else "" }
        if (bannerText.isNotBlank()) {
            FeedbackBanner(
                text = bannerText,
                modifier = Modifier.padding(horizontal = 16.dp),
                isError = state.offlineMode || bannerText.startsWith("No se pudo"),
            )
        }
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
                    deleting = device.id in state.pendingDeviceDeleteIds,
                    showActions = entryMode == RulesEntryMode.ManageUsers,
                    onClick = { onDeviceSelected(device.id) },
                    onDelete = { onDeviceDeleted(device.id) },
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
                .background(AdminSurface)
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
            HeaderIconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar",
                    tint = HeaderMuted,
                    modifier = Modifier.size(22.dp),
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
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = HeaderMuted,
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            HeaderIconButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar usuario",
                    tint = HeaderInk,
                    modifier = Modifier.size(22.dp),
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
                        modifier = Modifier.fillMaxWidth(),
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
    deleting: Boolean,
    showActions: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val healthy = device.status == UserDeviceStatus.Active && device.protectionComplete
    val statusColor =
        when {
            device.possibleUninstall -> CriticalRed
            healthy -> ActiveGreen
            else -> PendingYellow
        }
    ProductListRow(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        leading = { UserAvatar(name = device.name, color = statusColor) },
        headline = {
            Text(device.name, style = MaterialTheme.typography.titleMedium, color = HeaderInk)
        },
        supporting = {
            Text(
                text = device.listSummary(healthy),
                style = MaterialTheme.typography.bodySmall,
                color = if (device.possibleUninstall) CriticalRed else HeaderMuted,
                maxLines = 2,
            )
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(10.dp).background(statusColor, CircleShape))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = HeaderMuted,
                )
                if (showActions) {
                    Box {
                        IconButton(
                            onClick = { actionsExpanded = true },
                            enabled = !deleting,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Acciones",
                                tint = HeaderMuted,
                            )
                        }
                        DropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (deleting) "Archivando..." else "Archivar") },
                                enabled = !deleting,
                                onClick = {
                                    actionsExpanded = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Archivar usuario") },
            text = {
                Text(
                    "El usuario perderá acceso de inmediato y saldrá de la lista activa. Su configuración y auditoría se conservarán para poder restaurarlo después.",
                )
            },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !deleting,
                    modifier = Modifier,
                    text = "Archivar usuario",
                    loadingText = "Archivando...",
                    successText = "Archivado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun UserCreateButton(onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Nuevo usuario",
                tint = Color.White,
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

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Activo"
            UserDeviceStatus.Unprotected -> "Protección caída"
            UserDeviceStatus.Inactive -> "Sin comunicación"
            UserDeviceStatus.Unknown -> "Pendiente"
        }

internal fun UserDeviceUiState.detailHealthColor(): Color =
    when {
        possibleUninstall -> CriticalRed
        status == UserDeviceStatus.Active && protectionComplete -> ActiveGreen
        else -> PendingYellow
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
