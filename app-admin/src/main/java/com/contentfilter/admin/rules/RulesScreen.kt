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
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope

@Composable
fun RulesRoute(viewModel: RulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        onDomainChanged = viewModel::onDomainChanged,
        onDomainLimitDomainChanged = viewModel::onDomainLimitDomainChanged,
        onDomainLimitMinutesChanged = viewModel::onDomainLimitMinutesChanged,
        onAllowDomainChanged = viewModel::onAllowDomainChanged,
        onAllowDomainMinutesChanged = viewModel::onAllowDomainMinutesChanged,
        onAppSearchChanged = viewModel::onAppSearchChanged,
        onRefreshApps = viewModel::refreshApps,
        onDeviceSelected = viewModel::onDeviceSelected,
        onDeviceCleared = viewModel::clearDeviceSelection,
        onCreateDomainBlock = viewModel::createBlockedDomainRule,
        onCreateDomainLimit = viewModel::createDomainLimit,
        onCreateDomainAllow = viewModel::createAllowedDomainRule,
        onCreateAllowedDomainLimit = viewModel::saveAllowedDomainLimit,
        onInternetBlockedChanged = viewModel::setInternetBlocked,
        onGoogleSearchAllowedChanged = viewModel::setGoogleSearchAllowed,
        onAppAllowedChanged = viewModel::setAppAllowed,
        onAppLimitSaved = viewModel::saveAppControlLimit,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun RulesScreen(
    state: RulesUiState,
    onDomainChanged: (String) -> Unit,
    onDomainLimitDomainChanged: (String) -> Unit,
    onDomainLimitMinutesChanged: (String) -> Unit,
    onAllowDomainChanged: (String) -> Unit,
    onAllowDomainMinutesChanged: (String) -> Unit,
    onAppSearchChanged: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDeviceCleared: () -> Unit,
    onCreateDomainBlock: () -> Unit,
    onCreateDomainLimit: () -> Unit,
    onCreateDomainAllow: () -> Unit,
    onCreateAllowedDomainLimit: () -> Unit,
    onInternetBlockedChanged: (Boolean) -> Unit,
    onGoogleSearchAllowedChanged: (Boolean) -> Unit,
    onAppAllowedChanged: (String, Boolean) -> Unit,
    onAppLimitSaved: (String, String) -> Unit,
    onToggle: (PolicyRule) -> Unit,
    onDelete: (PolicyRule) -> Unit,
) {
    val visibleDomainRules =
        state.rules.filter {
            it.scope == RuleScope.Domain &&
                it.target != "*" &&
                it.target !in GoogleSearchDomainsForUi &&
                it.action == if (state.internetBlocked) RuleAction.Allow else RuleAction.Block
        }
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
            Text("Reglas", style = MaterialTheme.typography.headlineSmall)
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
                items(state.appControls, key = { "${it.deviceName}:${it.packageName}" }) { app ->
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
            Text("Reglas", style = MaterialTheme.typography.headlineSmall)
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
                SectionHeader(title = "Celulares Usuario", count = state.userDevices.size)
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
                    onClick = { onDeviceSelected(device.id) },
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
                items(state.appControls, key = { "${it.deviceName}:${it.packageName}" }) { app ->
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
                        onBlockedChanged = onInternetBlockedChanged,
                        onGoogleAllowedChanged = onGoogleSearchAllowedChanged,
                    )
                }
                item {
                    if (state.internetBlocked) {
                        AllowDomainEditorCard(
                            domain = state.allowDomain,
                            minutes = state.allowDomainMinutes,
                            onDomainChanged = onAllowDomainChanged,
                            onMinutesChanged = onAllowDomainMinutesChanged,
                            onAllow = onCreateDomainAllow,
                            onAllowWithLimit = onCreateAllowedDomainLimit,
                        )
                    } else {
                        RuleEditorCard(
                            title = "Lista negra",
                            description = "Bloquea el dominio y sus subdominios.",
                            value = state.domain,
                            label = "Dominio",
                            onValueChange = onDomainChanged,
                            buttonText = "Bloquear dominio",
                            onSubmit = onCreateDomainBlock,
                        )
                    }
                }
                item {
                    DomainLimitEditorCard(
                        domain = state.domainLimitDomain,
                        minutes = state.domainLimitMinutes,
                        onDomainChanged = onDomainLimitDomainChanged,
                        onMinutesChanged = onDomainLimitMinutesChanged,
                        onSubmit = onCreateDomainLimit,
                    )
                }
                item {
                    SectionHeader(
                        title = if (state.internetBlocked) "Lista blanca" else "Lista negra",
                        count = visibleDomainRules.size,
                    )
                }
                if (visibleDomainRules.isEmpty()) {
                    item {
                        EmptySectionText(
                            if (state.internetBlocked) {
                                "No hay sitios permitidos."
                            } else {
                                "No hay sitios bloqueados."
                            },
                        )
                    }
                }
                items(visibleDomainRules, key = { it.id }) { rule ->
                    RuleCard(rule = rule, onToggle = { onToggle(rule) }, onDelete = { onDelete(rule) })
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

private enum class DevicePanel {
    Apps,
    Internet,
}

@Composable
private fun SelectedDeviceHeader(
    device: UserDeviceUiState,
    selectedPanel: DevicePanel,
    onPanelSelected: (DevicePanel) -> Unit,
    onBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${if (device.active) "Activo" else "Desconectado"} | ${device.lastSeenLabel}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedPanel == DevicePanel.Apps) {
                    Button(onClick = { }) {
                        Text("Apps")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.Apps) }) {
                        Text("Apps")
                    }
                }
                if (selectedPanel == DevicePanel.Internet) {
                    Button(onClick = { }) {
                        Text("Internet")
                    }
                } else {
                    OutlinedButton(onClick = { onPanelSelected(DevicePanel.Internet) }) {
                        Text("Internet")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDeviceCard(
    device: UserDeviceUiState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val indicatorColor =
        if (device.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${if (device.active) "Activo" else "Desconectado"} | ${device.lastSeenLabel} | ${device.appCount} apps",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (selected) "Elegido" else "Ver",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()
    }
}

@Composable
private fun SectionActionHeader(
    title: String,
    count: Int,
    actionText: String,
    onAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$count", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        modifier = Modifier.padding(vertical = 4.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AppControlCard(
    app: AppControlUiState,
    onAllowedChanged: (Boolean) -> Unit,
    onLimitSaved: (String) -> Unit,
) {
    var minutes by remember(app.packageName, app.dailyLimitMinutes) {
        mutableStateOf(app.dailyLimitMinutes?.toString().orEmpty())
    }
    LaunchedEffect(app.dailyLimitMinutes) {
        minutes = app.dailyLimitMinutes?.toString().orEmpty()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.appName, app.iconBase64)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(app.appName, style = MaterialTheme.typography.titleSmall)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    app.versionName?.let { version ->
                        Text("Versión $version", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(
                    checked = app.allowed,
                    enabled = !app.isUpdating,
                    onCheckedChange = onAllowedChanged,
                )
            }
            Text(
                text =
                    when {
                        app.isUpdating -> "Estado: guardando..."
                        app.allowed -> "Estado: Permitida"
                        else -> "Estado: Bloqueada"
                    },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Límite diario: ${app.dailyLimitMinutes?.let { "$it min" } ?: "sin límite"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutos diarios") },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                )
                OutlinedButton(onClick = { onLimitSaved(minutes) }) {
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    name: String,
    iconBase64: String?,
) {
    val bitmap =
        remember(iconBase64) {
            iconBase64?.let {
                runCatching {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape),
        )
    } else {
        FallbackAppIcon(name)
    }
}

@Composable
private fun FallbackAppIcon(name: String) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RuleEditorCard(
    title: String,
    description: String,
    value: String,
    label: String,
    buttonText: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
            )
            Button(onClick = onSubmit) { Text(buttonText) }
        }
    }
}

@Composable
private fun InternetModeCard(
    blocked: Boolean,
    googleAllowed: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
    onGoogleAllowedChanged: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text("Modo web", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text =
                            if (blocked) {
                                "Bloquear todo excepto lista blanca."
                            } else {
                                "Permitir todo excepto dominios bloqueados."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = blocked, onCheckedChange = onBlockedChanged)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Permitir buscadores", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Permite Google, Bing y DuckDuckGo aunque el modo web bloquee todo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = googleAllowed, onCheckedChange = onGoogleAllowedChanged)
            }
        }
    }
}

@Composable
private fun AllowDomainEditorCard(
    domain: String,
    minutes: String,
    onDomainChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onAllow: () -> Unit,
    onAllowWithLimit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Lista blanca", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = domain,
                onValueChange = onDomainChanged,
                label = { Text("Sitio permitido") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = minutes,
                onValueChange = onMinutesChanged,
                label = { Text("Minutos por día opcional") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("Permitir") }
                OutlinedButton(onClick = onAllowWithLimit) { Text("Permitir con tiempo") }
            }
        }
    }
}

@Composable
private fun DomainLimitEditorCard(
    domain: String,
    minutes: String,
    onDomainChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Límite diario de dominio", style = MaterialTheme.typography.titleMedium)
            Text(
                "MVP por eventos DNS: cuenta minutos donde hubo consultas al dominio. Es aproximado.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = domain,
                onValueChange = onDomainChanged,
                label = { Text("Dominio") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = minutes,
                onValueChange = onMinutesChanged,
                label = { Text("Minutos por día") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(onClick = onSubmit) { Text("Guardar límite de dominio") }
        }
    }
}

@Composable
private fun RuleCard(
    rule: PolicyRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text(rule.target, style = MaterialTheme.typography.titleMedium)
                    Text(rule.scope.displayName(), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            Text("Acción: ${rule.action.displayName()}")
            Text("Estado: ${if (rule.enabled) "Activada" else "Desactivada"}")
            OutlinedButton(onClick = { confirmDelete = true }) {
                Text("Eliminar")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar regla") },
            text = { Text("Esta regla se eliminará de este entorno DEV.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private fun RuleAction.displayName(): String =
    when (this) {
        RuleAction.Allow -> "Permitir"
        RuleAction.Block -> "Bloquear"
        RuleAction.Warn -> "Advertir"
        RuleAction.RequestAuthorization -> "Requiere autorización"
    }

private fun RuleScope.displayName(): String =
    when (this) {
        RuleScope.App -> "Aplicación"
        RuleScope.Domain -> "Dominio"
        RuleScope.Category -> "Categoría"
        RuleScope.Global -> "Global"
    }

private val GoogleSearchDomainsForUi =
    setOf(
        "google.com",
        "gstatic.com",
        "googleapis.com",
        "googleusercontent.com",
        "bing.com",
        "duckduckgo.com",
    )
