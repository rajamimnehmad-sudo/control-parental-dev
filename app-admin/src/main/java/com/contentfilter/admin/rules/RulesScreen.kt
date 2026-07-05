package com.contentfilter.admin.rules

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope

@Composable
fun RulesRoute(viewModel: RulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        onAllowDomainChanged = viewModel::onAllowDomainChanged,
        onAllowDomainMinutesChanged = viewModel::onAllowDomainMinutesChanged,
        onAppSearchChanged = viewModel::onAppSearchChanged,
        onRefreshApps = viewModel::refreshApps,
        onGeneratePairingCode = viewModel::generatePairingCode,
        onDeviceSelected = viewModel::onDeviceSelected,
        onDeviceCleared = viewModel::clearDeviceSelection,
        onDeviceDeleted = viewModel::deleteDevicePermanently,
        onCreateDomainAllow = viewModel::createAllowedDomainRule,
        onCreateAllowedDomainLimit = viewModel::saveAllowedDomainLimit,
        onInternetBlockedChanged = viewModel::setInternetBlocked,
        onGoogleSearchAllowedChanged = viewModel::setGoogleSearchAllowed,
        onAppAllowedChanged = viewModel::setAppAllowed,
        onAppLimitSaved = viewModel::saveAppControlLimit,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
        onDeleteDomainLimit = viewModel::deleteDomainLimit,
    )
}

@Composable
private fun RulesScreen(
    state: RulesUiState,
    onAllowDomainChanged: (String) -> Unit,
    onAllowDomainMinutesChanged: (String) -> Unit,
    onAppSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onGeneratePairingCode: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDeviceCleared: () -> Unit,
    onDeviceDeleted: (String) -> Unit,
    onCreateDomainAllow: () -> Unit,
    onCreateAllowedDomainLimit: () -> Unit,
    onInternetBlockedChanged: (Boolean) -> Unit,
    onGoogleSearchAllowedChanged: (Boolean) -> Unit,
    onAppAllowedChanged: (String, Boolean) -> Unit,
    onAppLimitSaved: (String, String) -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
    onDeleteDomainLimit: (DailyLimit) -> Unit,
) {
    val visibleDomainRules =
        state.rules.filter {
            it.scope == RuleScope.Domain &&
                it.target != "*" &&
                it.target !in SearchEngineDomainsForUi &&
                it.action == RuleAction.Allow
        }
    val visibleDomainLimits =
        state.limits.filter {
            it.enabled &&
                it.targetType == PolicyTargetType.Domain &&
                it.target !in SearchEngineDomainsForUi
        }
    val visibleDomainRuleTargets = visibleDomainRules.map { it.target }.toSet()
    val orphanDomainLimits = visibleDomainLimits.filter { it.target !in visibleDomainRuleTargets }
    val otherRules = state.rules.filter { it.scope != RuleScope.App && it.scope != RuleScope.Domain }
    val selectedDevice = state.userDevices.firstOrNull { it.id == state.selectedDeviceId }
    var selectedPanel by rememberSaveable(state.selectedDeviceId) { mutableStateOf(DevicePanel.Apps) }

    if (selectedDevice != null && selectedPanel == DevicePanel.Apps) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mis dispositivos", style = MaterialTheme.typography.headlineSmall)
            if (state.offlineMode) {
                Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
            }
            if (state.message.isNotBlank()) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            SelectedDeviceHeader(
                device = selectedDevice,
                selectedPanel = selectedPanel,
                onPanelSelected = { selectedPanel = it },
                onBack = onDeviceCleared,
            )
            SectionActionHeader(
                title = "Apps",
                count = state.appControls.size,
                actionText = "Actualizar",
                onAction = onRefreshApps,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.appSearchQuery,
                onValueChange = onAppSearchChanged,
                label = { Text("Buscar app") },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.appControls.isEmpty()) {
                    item {
                        EmptySectionText("Abrí la App Usuario para detectar y sincronizar apps.")
                    }
                }
                items(state.appControls, key = { it.packageName }) { app ->
                    AppControlCard(
                        app = app,
                        onAllowedChanged = { allowed -> onAppAllowedChanged(app.packageName, allowed) },
                        onLimitSaved = { minutes -> onAppLimitSaved(app.packageName, minutes) },
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mis dispositivos", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.offlineMode) {
            item {
                Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.message.isNotBlank()) {
            item {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        if (selectedDevice == null) {
            item {
                Button(
                    onClick = onGeneratePairingCode,
                    enabled = !state.pairingLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.pairingLoading) "Generando..." else "Generar código de enlace")
                }
            }
            if (state.pairingCode.isNotBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Código para App Usuario", style = MaterialTheme.typography.labelLarge)
                            Text(state.pairingCode, style = MaterialTheme.typography.headlineMedium)
                            if (state.pairingExpiresAt.isNotBlank()) {
                                Text("Vence: ${state.pairingExpiresAt}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "Dispositivos vinculados", count = state.userDevices.size)
            }
            if (state.userDevices.isEmpty()) {
                item {
                    EmptySectionText("Enlazá un celular Usuario y abrí Mis apps para sincronizarlo.")
                }
            }
            items(state.userDevices, key = { it.id }) { device ->
                UserDeviceCard(
                    device = device,
                    selected = false,
                    deleting = device.id in state.pendingDeviceDeleteIds,
                    onClick = { onDeviceSelected(device.id) },
                    onDelete = { onDeviceDeleted(device.id) },
                )
            }
        } else {
            item {
                SelectedDeviceHeader(
                    device = selectedDevice,
                    selectedPanel = selectedPanel,
                    onPanelSelected = { selectedPanel = it },
                    onBack = onDeviceCleared,
                )
            }
            if (selectedPanel == DevicePanel.Apps) {
                item {
                    SectionActionHeader(
                        title = "Apps",
                        count = state.appControls.size,
                        actionText = "Actualizar",
                        onAction = onRefreshApps,
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.appSearchQuery,
                        onValueChange = onAppSearchChanged,
                        label = { Text("Buscar app") },
                        singleLine = true,
                    )
                }
                if (state.appControls.isEmpty()) {
                    item {
                        EmptySectionText("Abrí la App Usuario para detectar y sincronizar apps.")
                    }
                }
                items(state.appControls, key = { it.packageName }) { app ->
                    AppControlCard(
                        app = app,
                        onAllowedChanged = { allowed -> onAppAllowedChanged(app.packageName, allowed) },
                        onLimitSaved = { minutes -> onAppLimitSaved(app.packageName, minutes) },
                    )
                }
            } else {
                item {
                    InternetModeCard(
                        blocked = state.internetBlocked,
                        googleAllowed = state.googleSearchAllowed,
                        webModeUpdating = state.pendingInternetBlocked != null,
                        googleUpdating = state.pendingGoogleSearchAllowed != null,
                        onBlockedChanged = onInternetBlockedChanged,
                        onGoogleAllowedChanged = onGoogleSearchAllowedChanged,
                    )
                }
                item {
                    AllowDomainEditorCard(
                        domain = state.allowDomain,
                        minutes = state.allowDomainMinutes,
                        onDomainChanged = onAllowDomainChanged,
                        onMinutesChanged = onAllowDomainMinutesChanged,
                        onAllow = onCreateDomainAllow,
                        onAllowWithLimit = onCreateAllowedDomainLimit,
                    )
                }
                item {
                    SectionHeader(
                        title = "Lista blanca",
                        count = visibleDomainRules.size,
                    )
                }
                if (visibleDomainRules.isEmpty()) {
                    item {
                        EmptySectionText("No hay sitios permitidos.")
                    }
                }
                items(visibleDomainRules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        dailyLimitMinutes = visibleDomainLimits.firstOrNull { it.target == rule.target }?.limitMinutes,
                        onToggle = { onToggle(rule) },
                        onDelete = { onDelete(rule) },
                    )
                }
                if (orphanDomainLimits.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Límites sin regla visible", count = orphanDomainLimits.size)
                    }
                    items(orphanDomainLimits, key = { it.id }) { limit ->
                        DomainLimitCard(limit = limit, onDelete = { onDeleteDomainLimit(limit) })
                    }
                }
            }
            if (otherRules.isNotEmpty()) {
                item {
                    SectionHeader(title = "Otras reglas", count = otherRules.size)
                }
                items(otherRules, key = { it.id }) { rule ->
                    RuleCard(rule = rule, onToggle = { onToggle(rule) }, onDelete = { onDelete(rule) })
                }
            }
        }
    }
}
