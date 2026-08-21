package com.contentfilter.admin.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isScheduleRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.scheduleTarget
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.ProtectedBrowserPolicy
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductSectionHeader

@Composable
internal fun UserDetailContent(
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
    onProtectedBrowserRequiredChanged: (Boolean) -> Unit,
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
    val displayedApps =
        remember(state.appControls, appFilter, state.appSearchQuery) {
            if (state.appSearchQuery.isNotBlank()) {
                state.appControls
            } else {
                state.appControls.filter { it.matchesQuickFilter(appFilter) }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(
                    start = GloshSpacing.PageHorizontal,
                    top = 10.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 12.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserDetailHeader(
            device = selectedDevice,
            entryMode = entryMode,
            selectedPanel = selectedPanel,
            onPanelSelected = onPanelSelected,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            when (selectedPanel) {
                DevicePanel.Apps -> {
                    item {
                        GlobalScheduleButton(
                            title = "Horario general de apps",
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
                    item { AppSectionSelector(selectedPanel, onPanelSelected) }
                    if (displayedApps.isEmpty()) {
                        item {
                            EmptySectionText(
                                if (state.appControls.isEmpty()) {
                                    "Abrí Glosh Usuario para detectar y sincronizar las apps."
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
                                    scheduleAppPackage = app.packageName.takeUnless { it == scheduleAppPackage }
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
                                        onAllowedScheduleSaved(RuleScope.App, app.packageName, windows)
                                    },
                                )
                            }
                        }
                    }
                    if (otherRules.isNotEmpty()) {
                        item { ProductSectionHeader(title = "Otras reglas", count = otherRules.size) }
                        items(otherRules, key = { it.id }) { rule ->
                            RuleCard(rule = rule, onToggle = { onToggle(rule) }, onDelete = { onDelete(rule) })
                        }
                    }
                }

                DevicePanel.AppGroups -> {
                    item { AppSectionSelector(selectedPanel, onPanelSelected) }
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
                            title = "Horario de Internet",
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
                            protectedBrowserRequired = state.protectedBrowserRequired,
                            presentation = state.webPanelPresentation(),
                            navigationSaving = state.pendingInternetBlocked != null,
                            onlyResultsSaving = state.pendingOnlyResultsEnabled != null,
                            protectedBrowserSaving = state.pendingProtectedBrowserRequired != null,
                            protectionActive = selectedDevice.status == UserDeviceStatus.Active,
                            protectedBrowserInstalled =
                                state.appControls.any { ProtectedBrowserPolicy.isProtectedBrowser(it.packageName) },
                            alternativeBrowsers =
                                state.appControls.filter {
                                    ProtectedBrowserPolicy.isKnownAlternativeBrowser(it.packageName)
                                },
                            onBlockedChanged = onWebNavigationBlockedChanged,
                            onOnlyResultsChanged = onOnlyResultsChanged,
                            onProtectedBrowserRequiredChanged = onProtectedBrowserRequiredChanged,
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
                        item { ProductSectionHeader(title = "Sitios configurados", count = domainTargets.size) }
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
                                    flat = true,
                                    onToggle = { onToggle(rule) },
                                    onDelete = { onDelete(rule) },
                                )
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { scheduleDomain = target.takeUnless { it == scheduleDomain } },
                            ) {
                                Text(if (scheduleRules.isEmpty()) "Agregar horario" else "Editar horario")
                            }
                            if (scheduleDomain == target) {
                                AllowedScheduleEditor(
                                    title = "Horario de $target",
                                    rules = scheduleRules,
                                    saving =
                                        scheduleSavingKey(selectedDevice.id, RuleScope.Domain, target) in
                                            state.scheduleSavingKeys,
                                    onSave = { windows -> onAllowedScheduleSaved(RuleScope.Domain, target, windows) },
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
private fun UserDetailHeader(
    device: UserDeviceUiState,
    entryMode: RulesEntryMode,
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                ProductGlyph(ProductIcon.Back, GloshColors.Graphite, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = GloshColors.Graphite,
                    maxLines = 1,
                )
                Text(
                    "${device.lastSeenLabel} · ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            SecurityAttentionGlyph(level = device.securityAttentionLevel())
        }
        if (entryMode != RulesEntryMode.Web) {
            DetailSectionSelector(
                device = device,
                selectedPanel = selectedPanel,
                onPanelSelected = onPanelSelected,
            )
        } else {
            Text("Internet", style = MaterialTheme.typography.labelLarge, color = GloshColors.Muted)
        }
    }
}

@Composable
private fun DetailSectionSelector(
    device: UserDeviceUiState,
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
) {
    val attentionLevel = device.securityAttentionLevel()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GloshColors.SurfaceMuted, GloshShapes.Pill)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Apps",
            selected = selectedPanel == DevicePanel.Apps || selectedPanel == DevicePanel.AppGroups,
            onClick = { onPanelSelected(DevicePanel.Apps) },
        )
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Internet",
            selected = selectedPanel == DevicePanel.Web,
            onClick = { onPanelSelected(DevicePanel.Web) },
        )
        DetailSegmentButton(
            modifier = Modifier.weight(1f),
            text = "Seguridad",
            selected = selectedPanel == DevicePanel.Protection,
            attentionLevel = attentionLevel,
            onClick = { onPanelSelected(DevicePanel.Protection) },
        )
    }
}

@Composable
private fun DetailSegmentButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    attentionLevel: SecurityAttentionLevel = SecurityAttentionLevel.None,
) {
    val attentionDescription =
        when (attentionLevel) {
            SecurityAttentionLevel.Critical -> "Error de seguridad"
            SecurityAttentionLevel.Warning -> "Seguridad pendiente de verificar"
            SecurityAttentionLevel.None -> null
        }
    Row(
        modifier =
            modifier
                .then(
                    if (attentionDescription == null) Modifier else Modifier.semantics { stateDescription = attentionDescription },
                )
                .background(if (selected) GloshColors.Lime else Color.Transparent, GloshShapes.Pill)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite)
        if (attentionLevel != SecurityAttentionLevel.None) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .size(7.dp)
                        .background(attentionLevel.color, CircleShape),
            )
        }
    }
}

@Composable
private fun AppSectionSelector(
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedPanel == DevicePanel.Apps) {
            Button(modifier = Modifier.weight(1f), onClick = {}) { Text("Todas las apps") }
        } else {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { onPanelSelected(DevicePanel.Apps) }) {
                Text("Todas las apps")
            }
        }
        if (selectedPanel == DevicePanel.AppGroups) {
            Button(modifier = Modifier.weight(1f), onClick = {}) { Text("Grupos") }
        } else {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { onPanelSelected(DevicePanel.AppGroups) }) {
                Text("Grupos")
            }
        }
    }
}
