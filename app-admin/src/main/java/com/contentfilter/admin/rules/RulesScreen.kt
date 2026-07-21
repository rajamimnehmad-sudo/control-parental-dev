package com.contentfilter.admin.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleScope

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
