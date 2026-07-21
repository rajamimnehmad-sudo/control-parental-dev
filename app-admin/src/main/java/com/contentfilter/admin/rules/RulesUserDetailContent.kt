package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contentfilter.admin.adminMotionDurationMillis
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isScheduleRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.scheduleTarget
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.ui.ProductSectionHeader
import kotlinx.coroutines.launch

private fun lerpDp(
    start: Dp,
    end: Dp,
    fraction: Float,
): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)

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
    val motionDuration = adminMotionDurationMillis()
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
        animationSpec = tween(durationMillis = motionDuration),
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
                                    flat = true,
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
private fun UserDetailHeader(
    device: UserDeviceUiState,
    entryMode: RulesEntryMode,
    selectedPanel: DevicePanel,
    collapseProgress: Float,
    onPanelSelected: (DevicePanel) -> Unit,
    onCompactPanelClick: () -> Unit,
    onBack: () -> Unit,
) {
    val motionDuration = adminMotionDurationMillis()
    val attentionLevel = device.securityAttentionLevel()
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
                            expandHorizontally(animationSpec = tween(durationMillis = motionDuration)) +
                                fadeIn(animationSpec = tween(durationMillis = motionDuration)),
                        exit =
                            shrinkHorizontally(animationSpec = tween(durationMillis = motionDuration)) +
                                fadeOut(animationSpec = tween(durationMillis = motionDuration)),
                    ) {
                        CompactPanelChip(
                            panel = selectedPanel,
                            attentionLevel =
                                attentionLevel.takeIf { selectedPanel == DevicePanel.Protection }
                                    ?: SecurityAttentionLevel.None,
                            onClick = onCompactPanelClick,
                        )
                    }
                    SecurityAttentionGlyph(level = attentionLevel)
                }
                AnimatedVisibility(
                    visible = !compactMode,
                    enter = expandVertically(animationSpec = tween(durationMillis = motionDuration)) + fadeIn(animationSpec = tween(durationMillis = motionDuration)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = motionDuration)) + fadeOut(animationSpec = tween(durationMillis = motionDuration)),
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
                    expandVertically(animationSpec = tween(durationMillis = motionDuration)) +
                        fadeIn(animationSpec = tween(durationMillis = motionDuration)),
                exit =
                    shrinkVertically(animationSpec = tween(durationMillis = motionDuration)) +
                        fadeOut(animationSpec = tween(durationMillis = motionDuration)),
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
    attentionLevel: SecurityAttentionLevel,
    onClick: () -> Unit,
) {
    val label =
        when (panel) {
            DevicePanel.Apps, DevicePanel.AppGroups -> "Apps"
            DevicePanel.Web -> "Web"
            DevicePanel.Protection -> "Seguridad"
        }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(HeaderInk, shape)
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
        if (attentionLevel != SecurityAttentionLevel.None) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 4.dp)
                        .size(7.dp)
                        .background(attentionLevel.color, CircleShape),
            )
        }
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
    val attentionLevel = device.securityAttentionLevel()
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
            attentionLevel = attentionLevel,
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
    attentionLevel: SecurityAttentionLevel = SecurityAttentionLevel.None,
) {
    val shape = RoundedCornerShape(18.dp)
    val motionDuration = adminMotionDurationMillis()
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) HeaderInk else Color.White.copy(alpha = 0.42f),
        animationSpec = tween(durationMillis = motionDuration),
        label = "detail-segment-background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else HeaderInk,
        animationSpec = tween(durationMillis = motionDuration),
        label = "detail-segment-content",
    )
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
                    if (attentionDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics { stateDescription = attentionDescription }
                    },
                )
                .clip(shape)
                .background(
                    color = backgroundColor,
                    shape = shape,
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
        if (attentionLevel != SecurityAttentionLevel.None) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 7.dp)
                        .size(7.dp)
                        .background(
                            color = attentionLevel.color,
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
