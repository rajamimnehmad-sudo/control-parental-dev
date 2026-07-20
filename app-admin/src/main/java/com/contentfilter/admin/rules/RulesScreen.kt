package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isScheduleRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.scheduleTarget
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLazyVisualPage
import com.contentfilter.core.ui.ProductSectionHeader
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.launch
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
fun RulesRoute(
    entryMode: RulesEntryMode = RulesEntryMode.Apps,
    onBack: (() -> Unit)? = null,
    initialDeviceId: String? = null,
    onInitialDeviceConsumed: () -> Unit = {},
    createUserRequestKey: Int = 0,
    onCreateUserRequestConsumed: () -> Unit = {},
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialDeviceId) {
        initialDeviceId?.let { deviceId ->
            viewModel.onDeviceSelected(
                deviceId = deviceId,
                refreshApps = entryMode != RulesEntryMode.Web,
            )
            onInitialDeviceConsumed()
        }
    }
    RulesScreen(
        entryMode = entryMode,
        createUserRequestKey = createUserRequestKey,
        onCreateUserRequestConsumed = onCreateUserRequestConsumed,
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
        onRefreshArchivedUsers = viewModel::refreshArchivedUsers,
        onRestoreArchivedUser = viewModel::createArchivedUserRestoreCode,
        onRestoreTokenCopied = viewModel::markArchivedUserRestoreCodeCopied,
        onRestoreTokenDismissed = viewModel::dismissArchivedUserRestoreCode,
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
        onDeviceDeleted = viewModel::archiveUser,
        onAppAllowedChanged = viewModel::setAppAllowed,
        onAppLimitSaved = viewModel::saveAppControlLimit,
        onAllowedScheduleSaved = viewModel::saveAllowedSchedule,
        onAllowDomainChanged = viewModel::onAllowDomainChanged,
        onAllowDomainMinutesChanged = viewModel::onAllowDomainMinutesChanged,
        onCreateAllowedDomain = viewModel::createAllowedDomainRule,
        onSaveAllowedDomainLimit = viewModel::saveAllowedDomainLimit,
        onWebNavigationBlockedChanged = viewModel::setInternetBlocked,
        onOnlyResultsChanged = viewModel::setOnlyResultsEnabled,
        onDagEnabledChanged = viewModel::setDagEnabled,
        onDagExtraKosherEnabledChanged = viewModel::setDagExtraKosherEnabled,
        onProtectionArmedChanged = viewModel::setProtectionArmed,
        onAuthorizeRemoval = viewModel::authorizeRemoval,
        onGenerateRecoveryCode = viewModel::generateRecoveryCode,
        onRecoveryCodeCopied = viewModel::clearRecoveryCode,
        onGenerateRelinkCode = viewModel::generateRelinkCode,
        onRelinkCodeCopied = viewModel::clearRelinkCode,
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
    createUserRequestKey: Int,
    onCreateUserRequestConsumed: () -> Unit,
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
    onRefreshArchivedUsers: () -> Unit,
    onRestoreArchivedUser: (String) -> Unit,
    onRestoreTokenCopied: () -> Unit,
    onRestoreTokenDismissed: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onPairingCodeCopied: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDeviceCleared: () -> Unit,
    onDeviceDeleted: (String) -> Unit,
    onAppAllowedChanged: (String, Boolean) -> Unit,
    onAppLimitSaved: (String, String) -> Unit,
    onAllowedScheduleSaved: (RuleScope, String, List<AllowedScheduleWindowInput>) -> Unit,
    onAllowDomainChanged: (String) -> Unit,
    onAllowDomainMinutesChanged: (String) -> Unit,
    onCreateAllowedDomain: () -> Unit,
    onSaveAllowedDomainLimit: () -> Unit,
    onWebNavigationBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onDagEnabledChanged: (Boolean) -> Unit,
    onDagExtraKosherEnabledChanged: (Boolean) -> Unit,
    onProtectionArmedChanged: (String, Boolean) -> Unit,
    onAuthorizeRemoval: (String) -> Unit,
    onGenerateRecoveryCode: (String) -> Unit,
    onRecoveryCodeCopied: () -> Unit,
    onGenerateRelinkCode: (String) -> Unit,
    onRelinkCodeCopied: () -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val otherRules = state.rules.filter { it.scope != RuleScope.App && it.scope != RuleScope.Domain }
    val selectedDevice = state.userDevices.firstOrNull { it.id == state.selectedDeviceId }
    val initialPanel = if (entryMode == RulesEntryMode.Web) DevicePanel.Web else DevicePanel.Apps
    var selectedPanel by rememberSaveable(selectedDevice?.id, entryMode) { mutableStateOf(initialPanel) }
    var showingArchivedUsers by rememberSaveable { mutableStateOf(false) }
    val effectivePanel = if (entryMode == RulesEntryMode.Web) DevicePanel.Web else selectedPanel

    if (selectedDevice == null && showingArchivedUsers) {
        ArchivedUsersContent(
            state = state,
            clipboardManager = clipboardManager,
            onBack = { showingArchivedUsers = false },
            onRefresh = onRefreshArchivedUsers,
            onRestore = onRestoreArchivedUser,
            onTokenCopied = onRestoreTokenCopied,
            onTokenDismissed = onRestoreTokenDismissed,
        )
    } else if (selectedDevice == null) {
        UsersListContent(
            entryMode = entryMode,
            createUserRequestKey = createUserRequestKey,
            onCreateUserRequestConsumed = onCreateUserRequestConsumed,
            state = state,
            clipboardManager = clipboardManager,
            onBack = onBack,
            onRefreshDevices = onRefreshDevices,
            onPairingUserNameChanged = onPairingUserNameChanged,
            onGeneratePairingCode = onGeneratePairingCode,
            onPairingCodeCopied = onPairingCodeCopied,
            onDeviceSelected = onDeviceSelected,
            onDeviceDeleted = onDeviceDeleted,
            onShowArchivedUsers = {
                showingArchivedUsers = true
                onRefreshArchivedUsers()
            },
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
            onAllowedScheduleSaved = onAllowedScheduleSaved,
            onAllowDomainChanged = onAllowDomainChanged,
            onAllowDomainMinutesChanged = onAllowDomainMinutesChanged,
            onCreateAllowedDomain = onCreateAllowedDomain,
            onSaveAllowedDomainLimit = onSaveAllowedDomainLimit,
            onWebNavigationBlockedChanged = onWebNavigationBlockedChanged,
            onOnlyResultsChanged = onOnlyResultsChanged,
            onDagEnabledChanged = onDagEnabledChanged,
            onDagExtraKosherEnabledChanged = onDagExtraKosherEnabledChanged,
            onProtectionArmedChanged = onProtectionArmedChanged,
            onAuthorizeRemoval = onAuthorizeRemoval,
            onGenerateRecoveryCode = onGenerateRecoveryCode,
            onRecoveryCodeCopied = onRecoveryCodeCopied,
            onGenerateRelinkCode = onGenerateRelinkCode,
            onRelinkCodeCopied = onRelinkCodeCopied,
            onArchiveUser = { onDeviceDeleted(selectedDevice.id) },
            onToggle = onToggle,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun UsersListContent(
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
private fun ArchivedUsersContent(
    state: RulesUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (String) -> Unit,
    onTokenCopied: () -> Unit,
    onTokenDismissed: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredUsers =
        remember(state.archivedUsers, searchQuery) {
            val normalized = searchQuery.trim().lowercase()
            if (normalized.isBlank()) {
                state.archivedUsers
            } else {
                state.archivedUsers.filter { user -> user.name.lowercase().contains(normalized) }
            }
        }
    val bannerText = state.message.ifBlank { if (state.offlineMode) "Sin conexión. Mostrando datos guardados." else "" }

    ProductLazyVisualPage(
        title = "Usuarios anteriores",
        subtitle = "${state.archivedUsers.size} archivados · búsqueda por nombre",
        onBack = onBack,
        banner =
            if (bannerText.isNotBlank()) {
                {
                    FeedbackBanner(
                        text = bannerText,
                        isError = state.offlineMode || bannerText.startsWith("No se pudo"),
                    )
                }
            } else {
                null
            },
    ) {
        item(key = "archived-search") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por nombre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item(key = "archived-refresh") {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefresh,
                enabled = !state.archivedUsersLoading,
            ) {
                Text(if (state.archivedUsersLoading) "Actualizando..." else "Actualizar")
            }
        }
        if (!state.archivedUsersLoading && filteredUsers.isEmpty()) {
            item(key = "archived-empty") {
                EmptySectionText(
                    if (state.archivedUsers.isEmpty()) {
                        "Todavía no hay usuarios archivados desde App Admin."
                    } else {
                        "No hay usuarios anteriores que coincidan con ese nombre."
                    },
                )
            }
        }
        items(filteredUsers, key = { it.archiveId }) { user ->
            ArchivedUserCard(
                user = user,
                restoring = user.archiveId in state.restoreLoadingArchiveIds,
                onRestore = { onRestore(user.archiveId) },
            )
        }
    }

    if (state.restorePairingCode.isNotBlank()) {
        AlertDialog(
            onDismissRequest = onTokenDismissed,
            title = { Text("Restaurar ${state.restorePairingUserName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ingresá este token en App Usuario. La configuración se restaurará recién cuando el token se use.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TokenReadyCard(
                        code = state.restorePairingCode,
                        expiresAt = state.restorePairingExpiresAt,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(state.restorePairingCode))
                            onTokenCopied()
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = onTokenDismissed) {
                    Text("Cerrar")
                }
            },
        )
    }
}

@Composable
private fun ArchivedUserCard(
    user: ArchivedUserUiState,
    restoring: Boolean,
    onRestore: () -> Unit,
) {
    ProductCard {
        Text(user.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Archivado: ${user.archivedAtLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (user.canRestore) {
            Text(
                "Configuración segura disponible.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ProgressActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Restaurar con token nuevo",
                loadingText = "Generando token...",
                successText = "Token listo",
                onClick = onRestore,
                loading = restoring,
                enabled = !restoring,
            )
        } else {
            StatusChip("Revisión necesaria", PendingYellow)
            Text(
                "Este archivo es anterior al respaldo seguro. No se restaurará automáticamente sin una decisión explícita.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(name = device.name, color = statusColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = HeaderInk)
                Text(
                    text = device.listSummary(healthy),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (device.possibleUninstall) CriticalRed else HeaderMuted,
                    maxLines = 2,
                )
            }
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
    }
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

private fun UserDeviceUiState.detailHealthColor(): Color =
    when {
        possibleUninstall -> CriticalRed
        status == UserDeviceStatus.Active && protectionComplete -> ActiveGreen
        else -> PendingYellow
    }

private fun UserDeviceUiState.detailAttentionSummary(): String =
    protectionAlert
        ?: when (status) {
            UserDeviceStatus.Unprotected -> "Hay componentes de protección que requieren atención"
            UserDeviceStatus.Inactive -> "No hay comunicación reciente con el dispositivo"
            UserDeviceStatus.Unknown -> "Todavía falta verificar la configuración"
            UserDeviceStatus.Active -> "Falta completar la configuración de protección"
        }

private val AdminSurface = Color(0xFFF2F8F7)
internal val HeaderInk = Color(0xFF162235)
internal val HeaderMuted = Color(0xFF68758A)
internal val ActiveGreen = Color(0xFF00A650)
internal val CriticalRed = Color(0xFFB00020)
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
    onAllowedScheduleSaved: (RuleScope, String, List<AllowedScheduleWindowInput>) -> Unit,
    onAllowDomainChanged: (String) -> Unit,
    onAllowDomainMinutesChanged: (String) -> Unit,
    onCreateAllowedDomain: () -> Unit,
    onSaveAllowedDomainLimit: () -> Unit,
    onWebNavigationBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onDagEnabledChanged: (Boolean) -> Unit,
    onDagExtraKosherEnabledChanged: (Boolean) -> Unit,
    onProtectionArmedChanged: (String, Boolean) -> Unit,
    onAuthorizeRemoval: (String) -> Unit,
    onGenerateRecoveryCode: (String) -> Unit,
    onRecoveryCodeCopied: () -> Unit,
    onGenerateRelinkCode: (String) -> Unit,
    onRelinkCodeCopied: () -> Unit,
    onArchiveUser: () -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
) {
    var appFilter by rememberSaveable(selectedDevice.id) { mutableStateOf(AppQuickFilter.All) }
    var scheduleAppPackage by rememberSaveable(selectedDevice.id) { mutableStateOf<String?>(null) }
    var scheduleDomain by rememberSaveable(selectedDevice.id) { mutableStateOf<String?>(null) }
    var searchExpanded by rememberSaveable(selectedDevice.id) { mutableStateOf(state.appSearchQuery.isNotBlank()) }
    val listState = rememberLazyListState()
    val scrollHeaderProgress =
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / 72f).coerceIn(0f, 1f)
        }
    val headerTargetProgress = if (searchExpanded) 1f else scrollHeaderProgress
    val headerProgress by animateFloatAsState(
        targetValue = headerTargetProgress,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 920f),
        label = "user-detail-header-progress",
    )
    val coroutineScope = rememberCoroutineScope()
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
            onCompactPanelClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            onBack = onBack,
        )
        if (state.message.isNotBlank()) {
            CompactActionBanner(state.message, isError = state.message.startsWith("No se pudo"))
        }
        if (selectedPanel == DevicePanel.Apps) {
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
                        GlobalScheduleButton(
                            title = "Horario global de aplicaciones",
                            rules =
                                state.rules.filter {
                                    it.scope == RuleScope.App &&
                                        it.scheduleTarget() == PolicySchedulePolicy.WildcardTarget
                                },
                            saving =
                                scheduleSavingKey(
                                    selectedDevice.id,
                                    RuleScope.App,
                                    PolicySchedulePolicy.WildcardTarget,
                                ) in state.scheduleSavingKeys,
                            onSave = { windows ->
                                onAllowedScheduleSaved(
                                    RuleScope.App,
                                    PolicySchedulePolicy.WildcardTarget,
                                    windows,
                                )
                            },
                        )
                    }
                    item {
                        AppSectionSelector(
                            selectedPanel = selectedPanel,
                            onPanelSelected = onPanelSelected,
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
                        val appScheduleRules =
                            state.rules.filter {
                                it.scope == RuleScope.App && it.scheduleTarget() == app.packageName
                            }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppControlCard(
                                app = app,
                                scheduleConfigured = appScheduleRules.any { it.activeWindow != null },
                                onAllowedChanged = { allowed -> onAppAllowedChanged(app.packageName, allowed) },
                                onLimitSaved = { minutes -> onAppLimitSaved(app.packageName, minutes) },
                                onScheduleClick = {
                                    scheduleAppPackage =
                                        app.packageName.takeUnless { it == scheduleAppPackage }
                                },
                            )
                            if (scheduleAppPackage == app.packageName) {
                                AllowedScheduleEditor(
                                    title = "Horario de ${app.appName}",
                                    rules = appScheduleRules,
                                    saving =
                                        scheduleSavingKey(selectedDevice.id, RuleScope.App, app.packageName) in
                                            state.scheduleSavingKeys,
                                    onSave = { windows ->
                                        onAllowedScheduleSaved(
                                            RuleScope.App,
                                            app.packageName,
                                            windows,
                                        )
                                    },
                                )
                            }
                        }
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
                        AppSectionSelector(
                            selectedPanel = selectedPanel,
                            onPanelSelected = onPanelSelected,
                        )
                    }
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
                        GlobalScheduleButton(
                            title = "Horario global de Web",
                            rules =
                                state.rules.filter {
                                    it.scope == RuleScope.Domain &&
                                        it.scheduleTarget() == PolicySchedulePolicy.WildcardTarget
                                },
                            saving =
                                scheduleSavingKey(
                                    selectedDevice.id,
                                    RuleScope.Domain,
                                    PolicySchedulePolicy.WildcardTarget,
                                ) in state.scheduleSavingKeys,
                            onSave = { windows ->
                                onAllowedScheduleSaved(
                                    RuleScope.Domain,
                                    PolicySchedulePolicy.WildcardTarget,
                                    windows,
                                )
                            },
                        )
                    }
                    item {
                        WebNavigationPanel(
                            blocked = state.internetBlocked,
                            onlyResultsEnabled = state.onlyResultsEnabled,
                            presentation = state.webPanelPresentation(),
                            navigationSaving = state.pendingInternetBlocked != null,
                            onlyResultsSaving = state.pendingOnlyResultsEnabled != null,
                            dagEnabled = state.dagEnabled,
                            dagEntitled = state.dagEntitled,
                            dagSaving = state.pendingDagEnabled != null,
                            dagExtraKosherEnabled = state.dagExtraKosherEnabled,
                            dagExtraKosherSaving = state.pendingDagExtraKosherEnabled != null,
                            protectionActive = selectedDevice.status == UserDeviceStatus.Active,
                            onBlockedChanged = onWebNavigationBlockedChanged,
                            onOnlyResultsChanged = onOnlyResultsChanged,
                            onDagEnabledChanged = onDagEnabledChanged,
                            onDagExtraKosherEnabledChanged = onDagExtraKosherEnabledChanged,
                        )
                    }
                    item {
                        DomainRuleEditor(
                            domain = state.allowDomain,
                            minutes = state.allowDomainMinutes,
                            saving = state.internetSaving,
                            onDomainChanged = onAllowDomainChanged,
                            onMinutesChanged = onAllowDomainMinutesChanged,
                            onAllow = onCreateAllowedDomain,
                            onAllowWithLimit = onSaveAllowedDomainLimit,
                        )
                    }
                    val domainRules =
                        state.rules.filter {
                            it.scope == RuleScope.Domain &&
                                it.target != PolicySchedulePolicy.WildcardTarget &&
                                !it.target.startsWith("__")
                        }
                    val domainTargets = domainRules.map(PolicyRule::target).distinct().sorted()
                    if (domainTargets.isNotEmpty()) {
                        item {
                            ProductSectionHeader(title = "Sitios configurados", count = domainTargets.size)
                        }
                    }
                    items(domainTargets, key = { "domain-$it" }) { target ->
                        val targetRules = domainRules.filter { it.target == target }
                        val scheduleRules =
                            state.rules.filter {
                                it.scope == RuleScope.Domain && it.scheduleTarget() == target
                            }
                        val regularRules = targetRules.filterNot { it.isScheduleRule() }
                        val dailyLimit =
                            state.limits.firstOrNull {
                                it.targetType == PolicyTargetType.Domain && it.target == target
                            }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            regularRules.forEach { rule ->
                                RuleCard(
                                    rule = rule,
                                    dailyLimitMinutes = dailyLimit?.limitMinutes,
                                    onToggle = { onToggle(rule) },
                                    onDelete = { onDelete(rule) },
                                )
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { scheduleDomain = target.takeUnless { it == scheduleDomain } },
                            ) {
                                Text(
                                    if (scheduleRules.isEmpty()) "Agregar horario a $target" else "Editar horario de $target",
                                )
                            }
                            if (scheduleDomain == target) {
                                AllowedScheduleEditor(
                                    title = "Horario de $target",
                                    rules = scheduleRules,
                                    saving =
                                        scheduleSavingKey(selectedDevice.id, RuleScope.Domain, target) in
                                            state.scheduleSavingKeys,
                                    onSave = { windows ->
                                        onAllowedScheduleSaved(RuleScope.Domain, target, windows)
                                    },
                                )
                            }
                        }
                    }
                }
                DevicePanel.Protection -> {
                    item {
                        ProtectionPanel(
                            state = state,
                            device = selectedDevice,
                            onArmProtection = { onProtectionArmedChanged(selectedDevice.id, true) },
                        )
                    }
                    if (entryMode == RulesEntryMode.ManageUsers) {
                        item(key = "advanced-options-${selectedDevice.id}") {
                            AdvancedUserOptions(
                                state = state,
                                device = selectedDevice,
                                clipboardManager = LocalClipboardManager.current,
                                onAuthorizeRemoval = { onAuthorizeRemoval(selectedDevice.id) },
                                onGenerateRecoveryCode = { onGenerateRecoveryCode(selectedDevice.id) },
                                onRecoveryCodeCopied = onRecoveryCodeCopied,
                                onGenerateRelinkCode = { onGenerateRelinkCode(selectedDevice.id) },
                                onRelinkCodeCopied = onRelinkCodeCopied,
                                onArchiveUser = onArchiveUser,
                            )
                        }
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
    onCompactPanelClick: () -> Unit,
    onBack: () -> Unit,
) {
    val headerGap = lerpDp(14.dp, 10.dp, collapseProgress)
    val iconSize = lerpDp(44.dp, 40.dp, collapseProgress)
    val titleGap = lerpDp(4.dp, 2.dp, collapseProgress)
    val compactMode = collapseProgress >= 0.52f
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
                    AnimatedVisibility(
                        visible = compactMode && entryMode != RulesEntryMode.Web,
                        enter =
                            expandHorizontally(animationSpec = tween(durationMillis = 180)) +
                                fadeIn(animationSpec = tween(durationMillis = 150)),
                        exit =
                            shrinkHorizontally(animationSpec = tween(durationMillis = 140)) +
                                fadeOut(animationSpec = tween(durationMillis = 110)),
                    ) {
                        CompactPanelChip(
                            panel = selectedPanel,
                            critical = device.possibleUninstall && selectedPanel == DevicePanel.Protection,
                            onClick = onCompactPanelClick,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .background(device.detailHealthColor(), CircleShape),
                    )
                }
                AnimatedVisibility(
                    visible = !compactMode,
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
        if (entryMode != RulesEntryMode.Web) {
            AnimatedVisibility(
                visible = !compactMode,
                enter =
                    expandVertically(animationSpec = tween(durationMillis = 180)) +
                        fadeIn(animationSpec = tween(durationMillis = 150)),
                exit =
                    shrinkVertically(animationSpec = tween(durationMillis = 150)) +
                        fadeOut(animationSpec = tween(durationMillis = 110)),
            ) {
                GlassDetailSectionSelector(
                    device = device,
                    selectedPanel = selectedPanel,
                    collapseProgress = collapseProgress,
                    onPanelSelected = onPanelSelected,
                )
            }
        }
    }
}

@Composable
private fun CompactPanelChip(
    panel: DevicePanel,
    critical: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (panel) {
            DevicePanel.Apps, DevicePanel.AppGroups -> "Apps"
            DevicePanel.Web -> "Web"
            DevicePanel.Protection -> "Seguridad"
        }
    val color = if (critical) CriticalRed else HeaderInk
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(color, shape)
                .clickable(onClick = onClick)
                .padding(start = 11.dp, top = 7.dp, end = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Mostrar secciones",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun GlassDetailSectionSelector(
    device: UserDeviceUiState,
    selectedPanel: DevicePanel,
    collapseProgress: Float,
    onPanelSelected: (DevicePanel) -> Unit,
) {
    val outerRadius = lerpDp(24.dp, 20.dp, collapseProgress)
    val shape = RoundedCornerShape(outerRadius)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.7f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.94f), shape)
                .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Apps",
            selected = selectedPanel == DevicePanel.Apps || selectedPanel == DevicePanel.AppGroups,
            onClick = { onPanelSelected(DevicePanel.Apps) },
        )
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Web",
            selected = selectedPanel == DevicePanel.Web,
            onClick = { onPanelSelected(DevicePanel.Web) },
        )
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Seguridad",
            selected = selectedPanel == DevicePanel.Protection,
            attention = !device.protectionComplete || device.status != UserDeviceStatus.Active,
            critical = device.possibleUninstall,
            onClick = { onPanelSelected(DevicePanel.Protection) },
        )
    }
}

@Composable
private fun DetailSegmentButton(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    attention: Boolean = false,
    critical: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    val selectedColor = if (critical) CriticalRed else HeaderInk
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(
                    color = if (selected) selectedColor else Color.White.copy(alpha = 0.42f),
                    shape = shape,
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else HeaderInk,
        )
        if (attention) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 7.dp)
                        .size(7.dp)
                        .background(
                            color =
                                when {
                                    selected -> Color.White
                                    critical -> CriticalRed
                                    else -> PendingYellow
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

@Composable
private fun AppSectionSelector(
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedPanel == DevicePanel.AppGroups) {
            Button(modifier = Modifier.weight(1f), onClick = {}) {
                Text("Crear grupo de apps")
            }
        } else {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onPanelSelected(DevicePanel.AppGroups) },
            ) {
                Text("Crear grupo de apps")
            }
        }
        if (selectedPanel == DevicePanel.Apps) {
            Button(modifier = Modifier.weight(1f), onClick = {}) {
                Text("Todas las apps")
            }
        } else {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onPanelSelected(DevicePanel.Apps) },
            ) {
                Text("Todas las apps")
            }
        }
    }
}
