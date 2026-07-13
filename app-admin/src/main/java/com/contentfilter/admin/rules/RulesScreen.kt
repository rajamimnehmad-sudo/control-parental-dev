package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductSectionHeader
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
fun RulesRoute(
    entryMode: RulesEntryMode = RulesEntryMode.Apps,
    onBack: (() -> Unit)? = null,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulesScreen(
        entryMode = entryMode,
        state = state,
        onBack = onBack,
        onAppSearchChanged = viewModel::onAppSearchChanged,
        onGroupNameChanged = viewModel::onGroupNameChanged,
        onGroupMinutesChanged = viewModel::onGroupMinutesChanged,
        onGroupAppToggled = viewModel::onGroupAppToggled,
        onSaveAppGroup = viewModel::saveAppGroup,
        onEditAppGroup = viewModel::editAppGroup,
        onCancelAppGroupEdit = viewModel::cancelAppGroupEdit,
        onDeleteAppGroup = viewModel::deleteAppGroup,
        onRefreshApps = viewModel::refreshApps,
        onRefreshDevices = viewModel::refreshDevices,
        onPairingUserNameChanged = viewModel::onPairingUserNameChanged,
        onGeneratePairingCode = viewModel::generatePairingCode,
        onPairingCodeCopied = viewModel::clearPairingCode,
        onDeviceSelected = { deviceId ->
            viewModel.onDeviceSelected(
                deviceId = deviceId,
                refreshApps = entryMode != RulesEntryMode.Web,
            )
        },
        onDeviceCleared = viewModel::clearDeviceSelection,
        onDeviceDeleted = viewModel::deleteDevicePermanently,
        onAppAllowedChanged = viewModel::setAppAllowed,
        onAppLimitSaved = viewModel::saveAppControlLimit,
        onWebNavigationBlockedChanged = viewModel::setInternetBlocked,
        onOnlyResultsChanged = viewModel::setOnlyResultsEnabled,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
    )
}

enum class RulesEntryMode {
    Apps,
    Web,
    ManageUsers,
}

@Composable
private fun RulesScreen(
    entryMode: RulesEntryMode,
    state: RulesUiState,
    onBack: (() -> Unit)?,
    onAppSearchChanged: (String) -> Unit,
    onGroupNameChanged: (String) -> Unit,
    onGroupMinutesChanged: (String) -> Unit,
    onGroupAppToggled: (String, Boolean) -> Unit,
    onSaveAppGroup: () -> Unit,
    onEditAppGroup: (String) -> Unit,
    onCancelAppGroupEdit: () -> Unit,
    onDeleteAppGroup: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onRefreshDevices: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onPairingCodeCopied: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDeviceCleared: () -> Unit,
    onDeviceDeleted: (String) -> Unit,
    onAppAllowedChanged: (String, Boolean) -> Unit,
    onAppLimitSaved: (String, String) -> Unit,
    onWebNavigationBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val otherRules = state.rules.filter { it.scope != RuleScope.App && it.scope != RuleScope.Domain }
    val selectedDevice = state.userDevices.firstOrNull { it.id == state.selectedDeviceId }
    val initialPanel = if (entryMode == RulesEntryMode.Web) DevicePanel.Web else DevicePanel.Apps
    var selectedPanel by rememberSaveable(selectedDevice?.id, entryMode) { mutableStateOf(initialPanel) }
    val effectivePanel = if (entryMode == RulesEntryMode.Web) DevicePanel.Web else selectedPanel

    if (selectedDevice == null) {
        UsersListContent(
            entryMode = entryMode,
            state = state,
            clipboardManager = clipboardManager,
            onBack = onBack,
            onRefreshDevices = onRefreshDevices,
            onPairingUserNameChanged = onPairingUserNameChanged,
            onGeneratePairingCode = onGeneratePairingCode,
            onPairingCodeCopied = onPairingCodeCopied,
            onDeviceSelected =
                if (entryMode == RulesEntryMode.ManageUsers) {
                    {}
                } else {
                    onDeviceSelected
                },
            onDeviceDeleted = onDeviceDeleted,
        )
    } else {
        UserDetailContent(
            state = state,
            selectedDevice = selectedDevice,
            entryMode = entryMode,
            selectedPanel = effectivePanel,
            otherRules = otherRules,
            onBack = onDeviceCleared,
            onPanelSelected = { selectedPanel = it },
            onRefreshApps = onRefreshApps,
            onAppSearchChanged = onAppSearchChanged,
            onGroupNameChanged = onGroupNameChanged,
            onGroupMinutesChanged = onGroupMinutesChanged,
            onGroupAppToggled = onGroupAppToggled,
            onSaveAppGroup = onSaveAppGroup,
            onEditAppGroup = onEditAppGroup,
            onCancelAppGroupEdit = onCancelAppGroupEdit,
            onDeleteAppGroup = onDeleteAppGroup,
            onAppAllowedChanged = onAppAllowedChanged,
            onAppLimitSaved = onAppLimitSaved,
            onWebNavigationBlockedChanged = onWebNavigationBlockedChanged,
            onOnlyResultsChanged = onOnlyResultsChanged,
            onToggle = onToggle,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun UsersListContent(
    entryMode: RulesEntryMode,
    state: RulesUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onBack: (() -> Unit)?,
    onRefreshDevices: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onPairingCodeCopied: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDeviceDeleted: (String) -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
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
    val activeCount = state.userDevices.count { it.status == UserDeviceStatus.Active }
    val pendingCount = state.userDevices.count { it.status != UserDeviceStatus.Active }

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
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 18.dp,
                ),
        ) {
            if (state.offlineMode) {
                item {
                    FeedbackBanner("Sin conexion. Mostrando datos guardados.", isError = true)
                }
            }
            if (state.message.isNotBlank()) {
                item {
                    FeedbackBanner(state.message, isError = state.message.startsWith("No se pudo"))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserMetricCard(
                        modifier = Modifier.weight(1f),
                        value = activeCount.toString(),
                        label = "activos",
                        color = ActiveGreen,
                    )
                    UserMetricCard(
                        modifier = Modifier.weight(1f),
                        value = pendingCount.toString(),
                        label = "pendientes",
                        color = PendingYellow,
                    )
                }
            }
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
private fun TokenReadyCard(
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
private fun UserMetricCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .background(color.copy(alpha = 0.18f), CircleShape),
            )
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, color = HeaderInk)
                Text(label, style = MaterialTheme.typography.bodySmall, color = HeaderMuted)
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
    val statusColor = device.status.userStatusColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(name = device.name, color = statusColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = HeaderInk)
                StatusChip(device.status.label, statusColor)
                Text(
                    text = "${device.appCount} apps · ${device.lastSeenLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeaderMuted,
                )
                device.protectionAlert?.let { alert ->
                    Text(
                        text = alert,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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
                            text = { Text(if (deleting) "Borrando..." else "Borrar") },
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
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar usuario") },
            text = {
                Text(
                    "Esto borra definitivamente el usuario, sus apps detectadas, activaciones y solicitudes asociadas.",
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
                    text = "Borrar definitivo",
                    loadingText = "Borrando...",
                    successText = "Borrado",
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
private fun HeaderIconButton(
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
                .size(50.dp)
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
    }
}

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Activo"
            UserDeviceStatus.Unprotected -> "Protección caída"
            UserDeviceStatus.Inactive -> "Pendiente"
            UserDeviceStatus.Unknown -> "Pendiente"
        }

@Composable
private fun UserDeviceStatus.userStatusColor(): Color =
    when (this) {
        UserDeviceStatus.Active -> ActiveGreen
        UserDeviceStatus.Unprotected -> MaterialTheme.colorScheme.error
        UserDeviceStatus.Inactive -> PendingYellow
        UserDeviceStatus.Unknown -> HeaderMuted
    }

private val AdminSurface = Color(0xFFF2F8F7)
private val HeaderInk = Color(0xFF162235)
private val HeaderMuted = Color(0xFF68758A)
private val ActiveGreen = Color(0xFF00A650)
private val PendingYellow = Color(0xFFFFC849)

private fun lerpDp(
    start: Dp,
    end: Dp,
    fraction: Float,
): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)

@Composable
private fun UserDetailContent(
    state: RulesUiState,
    selectedDevice: UserDeviceUiState,
    entryMode: RulesEntryMode,
    selectedPanel: DevicePanel,
    otherRules: List<PolicyRule>,
    onBack: () -> Unit,
    onPanelSelected: (DevicePanel) -> Unit,
    onRefreshApps: () -> Unit,
    onAppSearchChanged: (String) -> Unit,
    onGroupNameChanged: (String) -> Unit,
    onGroupMinutesChanged: (String) -> Unit,
    onGroupAppToggled: (String, Boolean) -> Unit,
    onSaveAppGroup: () -> Unit,
    onEditAppGroup: (String) -> Unit,
    onCancelAppGroupEdit: () -> Unit,
    onDeleteAppGroup: (String) -> Unit,
    onAppAllowedChanged: (String, Boolean) -> Unit,
    onAppLimitSaved: (String, String) -> Unit,
    onWebNavigationBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
) {
    var appFilter by remember(selectedDevice.id) { mutableStateOf(AppQuickFilter.All) }
    var searchExpanded by rememberSaveable(selectedDevice.id) { mutableStateOf(state.appSearchQuery.isNotBlank()) }
    val listState = rememberLazyListState()
    val scrollHeaderProgress =
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / 72f).coerceIn(0f, 1f)
        }
    val headerTargetProgress =
        if (searchExpanded || selectedPanel != DevicePanel.Apps) {
            1f
        } else {
            scrollHeaderProgress
        }
    val headerProgress by animateFloatAsState(
        targetValue = headerTargetProgress,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 920f),
        label = "user-detail-header-progress",
    )
    val displayedApps =
        remember(state.appControls, appFilter, state.appSearchQuery) {
            if (state.appSearchQuery.isNotBlank()) {
                state.appControls
            } else {
                state.appControls.filter { app -> app.matchesQuickFilter(appFilter) }
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AdminSurface)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(lerpDp(12.dp, 8.dp, headerProgress)),
    ) {
        UserDetailHeader(
            device = selectedDevice,
            entryMode = entryMode,
            selectedPanel = selectedPanel,
            collapseProgress = headerProgress,
            onPanelSelected = onPanelSelected,
            onBack = onBack,
        )
        if (state.message.isNotBlank()) {
            CompactActionBanner(state.message, isError = state.message.startsWith("No se pudo"))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    bottom = 18.dp,
                ),
        ) {
            when (selectedPanel) {
                DevicePanel.Apps -> {
                    item {
                        AppsToolbar(
                            apps = state.appControls,
                            selectedFilter = appFilter,
                            searchQuery = state.appSearchQuery,
                            searchExpanded = searchExpanded,
                            onFilterSelected = { appFilter = it },
                            onSearchExpandedChanged = { expanded ->
                                searchExpanded = expanded
                                if (!expanded) onAppSearchChanged("")
                            },
                            onSearchChanged = onAppSearchChanged,
                            onRefreshApps = onRefreshApps,
                        )
                    }
                    if (displayedApps.isEmpty()) {
                        item {
                            EmptySectionText(
                                if (state.appControls.isEmpty()) {
                                    "Abrí la App Usuario para detectar y sincronizar apps."
                                } else {
                                    "No hay apps en este filtro."
                                },
                            )
                        }
                    }
                    items(displayedApps, key = { it.packageName }) { app ->
                        AppControlCard(
                            app = app,
                            onAllowedChanged = { allowed -> onAppAllowedChanged(app.packageName, allowed) },
                            onLimitSaved = { minutes -> onAppLimitSaved(app.packageName, minutes) },
                        )
                    }
                    if (otherRules.isNotEmpty()) {
                        item {
                            ProductSectionHeader(title = "Otras reglas", count = otherRules.size)
                        }
                        items(otherRules, key = { it.id }) { rule ->
                            RuleCard(rule = rule, onToggle = { onToggle(rule) }, onDelete = { onDelete(rule) })
                        }
                    }
                }
                DevicePanel.AppGroups -> {
                    item {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.appSearchQuery,
                            onValueChange = onAppSearchChanged,
                            label = { Text("Buscar app para agrupar") },
                            singleLine = true,
                        )
                    }
                    item {
                        AppGroupsPanel(
                            state = state,
                            onGroupNameChanged = onGroupNameChanged,
                            onGroupMinutesChanged = onGroupMinutesChanged,
                            onGroupAppToggled = onGroupAppToggled,
                            onSaveAppGroup = onSaveAppGroup,
                            onEditAppGroup = onEditAppGroup,
                            onCancelAppGroupEdit = onCancelAppGroupEdit,
                            onDeleteAppGroup = onDeleteAppGroup,
                        )
                    }
                }
                DevicePanel.Web -> {
                    item {
                        WebNavigationPanel(
                            blocked = state.internetBlocked,
                            onlyResultsEnabled = state.onlyResultsEnabled,
                            presentation = state.webPanelPresentation(),
                            navigationSaving = state.pendingInternetBlocked != null,
                            onlyResultsSaving = state.pendingOnlyResultsEnabled != null,
                            protectionActive = selectedDevice.status == UserDeviceStatus.Active,
                            onBlockedChanged = onWebNavigationBlockedChanged,
                            onOnlyResultsChanged = onOnlyResultsChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsToolbar(
    apps: List<AppControlUiState>,
    selectedFilter: AppQuickFilter,
    searchQuery: String,
    searchExpanded: Boolean,
    onFilterSelected: (AppQuickFilter) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    label = { Text("Buscar app") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aplicaciones",
                        style = MaterialTheme.typography.titleLarge,
                        color = HeaderInk,
                    )
                    Text(
                        text = "${apps.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = HeaderMuted,
                    )
                }
            }
            HeaderIconButton(onClick = onRefreshApps) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar",
                    tint = HeaderMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            HeaderIconButton(
                onClick = { onSearchExpandedChanged(!searchExpanded) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar app",
                    tint = HeaderInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (!searchExpanded) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilterBanner(
                    filter = AppQuickFilter.All,
                    selected = selectedFilter == AppQuickFilter.All,
                    count = apps.size,
                    onClick = { onFilterSelected(AppQuickFilter.All) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Blocked,
                    selected = selectedFilter == AppQuickFilter.Blocked,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Blocked) },
                    onClick = { onFilterSelected(AppQuickFilter.Blocked) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Limited,
                    selected = selectedFilter == AppQuickFilter.Limited,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Limited) },
                    onClick = { onFilterSelected(AppQuickFilter.Limited) },
                )
                AppFilterBanner(
                    filter = AppQuickFilter.Open,
                    selected = selectedFilter == AppQuickFilter.Open,
                    count = apps.count { it.matchesQuickFilter(AppQuickFilter.Open) },
                    onClick = { onFilterSelected(AppQuickFilter.Open) },
                )
            }
        }
    }
}

@Composable
private fun WebNavigationPanel(
    blocked: Boolean,
    onlyResultsEnabled: Boolean,
    presentation: WebPanelPresentation,
    navigationSaving: Boolean,
    onlyResultsSaving: Boolean,
    protectionActive: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
) {
    ProductCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InternetModeSelector(
                blocked = blocked,
                saving = navigationSaving,
                onBlockedChanged = onBlockedChanged,
            )
            Text(
                text = presentation.headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (blocked) {
                Text(
                    "Los navegadores no pueden abrir sitios. Tus protecciones quedan guardadas para cuando abras Internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeaderMuted,
                )
            } else {
                Text(
                    presentation.activeLayers.joinToString(" · ").ifBlank { "Sin capas adicionales" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeaderMuted,
                )
            }
            AnimatedVisibility(visible = presentation.showLayers) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "SafeSearch se aplica automaticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeaderMuted,
                    )
                    WebSwitchRow(
                        title = "Solo resultados",
                        description = "Permite buscar y ver resultados, pero bloquea todos los sitios externos.",
                        checked = onlyResultsEnabled,
                        enabled = !onlyResultsSaving,
                        saving = onlyResultsSaving,
                        onCheckedChange = onOnlyResultsChanged,
                    )
                }
            }
            if (blocked && !protectionActive) {
                FeedbackBanner(
                    "Protección web no activa: revisá VPN y Accesibilidad en el dispositivo.",
                    isError = true,
                )
            }
        }
    }
}

@Composable
private fun InternetModeSelector(
    blocked: Boolean,
    saving: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
) {
    var dragDistance by remember { mutableStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F4F5))
                    .border(1.dp, Color(0xFFD2DADD), RoundedCornerShape(8.dp))
                    .pointerInput(blocked, saving) {
                        if (!saving) {
                            val swipeThreshold = 48.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                                onDragEnd = {
                                    when {
                                        dragDistance > swipeThreshold && !blocked -> onBlockedChanged(true)
                                        dragDistance < -swipeThreshold && blocked -> onBlockedChanged(false)
                                    }
                                    dragDistance = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                dragDistance += amount
                            }
                        }
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InternetModeOption(
                title = "INTERNET ABIERTO",
                selected = !blocked,
                enabled = !saving,
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                onClick = { if (blocked) onBlockedChanged(false) },
            )
            InternetModeOption(
                title = "INTERNET BLOQUEADO",
                selected = blocked,
                enabled = !saving,
                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                onClick = { if (!blocked) onBlockedChanged(true) },
            )
        }
        if (saving) {
            Text("Aplicando…", style = MaterialTheme.typography.bodySmall, color = HeaderMuted)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.InternetModeOption(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .height(58.dp)
                .background(if (selected) ProductSky else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WebSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = HeaderMuted)
            if (saving) {
                Text("Guardando...", style = MaterialTheme.typography.bodySmall, color = HeaderMuted)
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun AppFilterBanner(
    filter: AppQuickFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val color = filter.color
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .width(142.dp)
                .clip(shape)
                .background(if (selected) Color(0xFFE9EEF0) else Color.White, shape)
                .border(
                    width = 1.dp,
                    color = if (selected) Color(0xFFB7C0C7) else Color(0xFFE1E7EA),
                    shape = shape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(color),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = HeaderInk,
                )
                Text(
                    text = "$count apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = HeaderMuted,
                )
            }
        }
    }
}

private enum class AppQuickFilter(
    val label: String,
    val color: Color,
) {
    All("Todas", Color(0xFF64748B)),
    Blocked("Bloqueadas", Color(0xFFC62828)),
    Limited("Con limite", Color(0xFFF9A825)),
    Open("Abiertas", Color(0xFF2E7D32)),
}

private fun AppControlUiState.matchesQuickFilter(filter: AppQuickFilter): Boolean =
    when (filter) {
        AppQuickFilter.All -> true
        AppQuickFilter.Blocked -> !confirmedAllowed && extraTimeRemainingMinutes == null
        AppQuickFilter.Limited -> extraTimeRemainingMinutes != null || dailyLimitMinutes != null
        AppQuickFilter.Open -> confirmedAllowed && extraTimeRemainingMinutes == null && dailyLimitMinutes == null
    }

@Composable
private fun CompactActionBanner(
    message: String,
    isError: Boolean,
) {
    FeedbackBanner(text = message, isError = isError)
}

@Composable
private fun UserDetailHeader(
    device: UserDeviceUiState,
    entryMode: RulesEntryMode,
    selectedPanel: DevicePanel,
    collapseProgress: Float,
    onPanelSelected: (DevicePanel) -> Unit,
    onBack: () -> Unit,
) {
    val headerGap = lerpDp(14.dp, 10.dp, collapseProgress)
    val iconSize = lerpDp(44.dp, 40.dp, collapseProgress)
    val titleGap = lerpDp(4.dp, 2.dp, collapseProgress)
    val tabShape = lerpDp(22.dp, 18.dp, collapseProgress)
    Column(
        verticalArrangement = Arrangement.spacedBy(headerGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(
                modifier = Modifier.size(iconSize),
                onClick = onBack,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = HeaderInk,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(titleGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = device.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = HeaderInk,
                        maxLines = 1,
                    )
                    StatusChip(device.status.label, device.status.userStatusColor())
                }
                AnimatedVisibility(
                    visible = collapseProgress < 0.72f,
                    enter = expandVertically(animationSpec = tween(durationMillis = 90)) + fadeIn(animationSpec = tween(durationMillis = 90)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 70)) + fadeOut(animationSpec = tween(durationMillis = 70)),
                ) {
                    Text(
                        text = "${device.lastSeenLabel} · ${device.appCount} apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeaderMuted,
                    )
                }
            }
        }
        if (entryMode == RulesEntryMode.Apps) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedPanel == DevicePanel.Apps) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { },
                        shape = RoundedCornerShape(tabShape),
                    ) {
                        Text("Aplicaciones")
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onPanelSelected(DevicePanel.Apps) },
                        shape = RoundedCornerShape(tabShape),
                    ) {
                        Text("Aplicaciones")
                    }
                }
                if (selectedPanel == DevicePanel.AppGroups) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { },
                        shape = RoundedCornerShape(tabShape),
                    ) {
                        Text("Apps en grupo")
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onPanelSelected(DevicePanel.AppGroups) },
                        shape = RoundedCornerShape(tabShape),
                    ) {
                        Text("Apps en grupo")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGroupsPanel(
    state: RulesUiState,
    onGroupNameChanged: (String) -> Unit,
    onGroupMinutesChanged: (String) -> Unit,
    onGroupAppToggled: (String, Boolean) -> Unit,
    onSaveAppGroup: () -> Unit,
    onEditAppGroup: (String) -> Unit,
    onCancelAppGroupEdit: () -> Unit,
    onDeleteAppGroup: (String) -> Unit,
) {
    val editingGroupId = state.editingGroupId
    val usedPackages =
        state.appGroups
            .filter { it.id != editingGroupId }
            .flatMap { group -> group.appPackages.map { packageName -> packageName to group.name } }
            .toMap()
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Apps en grupo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tiempo compartido por grupo · reinicia 12 PM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip("${state.appGroups.size}", MaterialTheme.colorScheme.primary)
        }
        if (state.appGroups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.appGroups.forEach { group ->
                    AppGroupSummaryCard(
                        group = group,
                        deleting = group.id in state.pendingAppGroupDeleteIds,
                        onEdit = { onEditAppGroup(group.id) },
                        onDelete = { onDeleteAppGroup(group.id) },
                    )
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editingGroupId == null) "Nuevo grupo" else "Editando grupo",
                style = MaterialTheme.typography.labelLarge,
            )
            if (editingGroupId != null) {
                OutlinedButton(onClick = onCancelAppGroupEdit) {
                    Text("Cancelar")
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupName,
            onValueChange = onGroupNameChanged,
            label = { Text("Nombre del grupo") },
            placeholder = { Text("Entretenimiento") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.groupMinutes,
            onValueChange = onGroupMinutesChanged,
            label = { Text("Tiempo total diario") },
            placeholder = { Text("240 minutos") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text("Apps disponibles", style = MaterialTheme.typography.labelLarge)
        val selectedPackages = state.groupSelectedPackages
        val selectedApps = state.appControls.filter { it.packageName in selectedPackages }
        val selectableApps = state.appControls.filter { it.packageName !in selectedPackages }.take(10)
        if (state.appControls.isEmpty()) {
            Text(
                "Actualizá apps o buscá el usuario para armar el grupo.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectableApps.forEach { app ->
                    val usedByGroup = usedPackages[app.packageName]
                    GroupAppPickerRow(
                        app = app,
                        actionText = if (usedByGroup == null) "+" else "En grupo",
                        helperText = usedByGroup?.let { "Ya está en $it" },
                        enabled = usedByGroup == null,
                        onClick = { onGroupAppToggled(app.packageName, true) },
                    )
                }
            }
        }
        Text("Cajón de apps (${selectedApps.size})", style = MaterialTheme.typography.labelLarge)
        if (selectedApps.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                        .padding(12.dp),
            ) {
                Text(
                    "Agregá apps con +. Todas compartirán el tiempo total diario.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedApps.forEach { app ->
                    GroupAppPickerRow(
                        app = app,
                        actionText = "x",
                        helperText = null,
                        onClick = { onGroupAppToggled(app.packageName, false) },
                    )
                }
            }
        }
        ProgressActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSaveAppGroup,
            loading = state.groupSaving,
            loadingText = if (editingGroupId == null) "Guardando..." else "Actualizando...",
            successText = if (editingGroupId == null) "Grupo guardado" else "Grupo actualizado",
            text = if (editingGroupId == null) "Guardar grupo" else "Actualizar grupo",
        )
    }
}

@Composable
private fun GroupAppPickerRow(
    app: AppControlUiState,
    actionText: String,
    helperText: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(app.appName, app.iconBase64)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.bodyMedium)
            Text(helperText ?: app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(actionText)
        }
    }
}

@Composable
private fun AppGroupSummaryCard(
    group: AppGroupUiState,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${group.appPackages.size} apps · ${group.limitMinutes} min · ${group.resetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !deleting,
                ) {
                    Text("Editar")
                }
                ProgressActionButton(
                    modifier = Modifier,
                    text = "Borrar",
                    loadingText = "Borrando...",
                    successText = "Borrado",
                    loading = deleting,
                    enabled = !deleting,
                    onClick = { confirmDelete = true },
                    tone = ActionButtonTone.Destructive,
                )
            }
        }
        group.appPackages.take(4).forEach { packageName ->
            Text(packageName, style = MaterialTheme.typography.bodySmall)
        }
        if (group.appPackages.size > 4) {
            Text("+${group.appPackages.size - 4} más", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar grupo") },
            text = { Text("Las apps de este grupo volverán a sus reglas individuales.") },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier,
                    text = "Borrar",
                    loadingText = "Borrando...",
                    successText = "Borrado",
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
