package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
internal fun RuleCard(
    rule: PolicyRule,
    dailyLimitMinutes: Int? = null,
    enabled: Boolean = true,
    flat: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
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
                    enabled = enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            Text("Acción: ${rule.action.displayName()}")
            if (dailyLimitMinutes != null) {
                Text("Límite diario: $dailyLimitMinutes min")
            }
            Text("Estado: ${if (rule.enabled) "Activada" else "Desactivada"}")
            OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled) {
                Text("Eliminar")
            }
        }
    }
    if (flat) {
        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            content()
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar regla") },
            text = { Text("Esta regla se eliminará.") },
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

@Composable
internal fun DomainRuleEditor(
    domain: String,
    minutes: String,
    saving: Boolean,
    onDomainChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onAllow: () -> Unit,
    onAllowWithLimit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Agregar sitio", style = MaterialTheme.typography.titleMedium)
        Text(
            "Podés permitir un sitio, limitar sus minutos por DNS o agregarle después un horario exacto.",
            style = MaterialTheme.typography.bodyMedium,
            color = HeaderMuted,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = domain,
            onValueChange = onDomainChanged,
            label = { Text("Dominio") },
            placeholder = { Text("ejemplo.com") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = minutes,
            onValueChange = onMinutesChanged,
            label = { Text("Minutos DNS opcionales") },
            supportingText = { Text("Es una aproximación técnica; no equivale a tiempo real de lectura.") },
            singleLine = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && domain.isNotBlank(),
            onClick = if (minutes.isBlank()) onAllow else onAllowWithLimit,
        ) {
            Text(if (minutes.isBlank()) "Permitir sitio" else "Permitir con límite aproximado")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun WebNavigationPanel(
    blocked: Boolean,
    onlyResultsEnabled: Boolean,
    presentation: WebPanelPresentation,
    navigationSaving: Boolean,
    onlyResultsSaving: Boolean,
    dagEnabled: Boolean,
    dagEntitled: Boolean,
    dagSaving: Boolean,
    dagExtraKosherEnabled: Boolean,
    dagExtraKosherSaving: Boolean,
    protectionActive: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onDagEnabledChanged: (Boolean) -> Unit,
    onDagExtraKosherEnabledChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Acceso a Internet", style = MaterialTheme.typography.titleLarge)
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
                "Los navegadores no pueden abrir sitios. Las protecciones quedan guardadas para cuando abras Internet.",
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Protecciones web", style = MaterialTheme.typography.titleMedium)
        AnimatedVisibility(visible = presentation.showLayers) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "SafeSearch se aplica automáticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeaderMuted,
                )
                WebSwitchRow(
                    icon = ProductIcon.Search,
                    title = "Solo resultados",
                    description = "Permite buscar y ver resultados, pero bloquea los sitios externos.",
                    checked = onlyResultsEnabled,
                    enabled = !onlyResultsSaving,
                    saving = onlyResultsSaving,
                    onCheckedChange = onOnlyResultsChanged,
                )
            }
        }
        WebSwitchRow(
            icon = ProductIcon.Web,
            title = "Buscador DAG",
            description =
                if (dagEntitled) {
                    "Habilita el buscador protegido en App Usuario."
                } else {
                    "DAG no está incluido en la licencia de esta comunidad."
                },
            checked = dagEnabled,
            enabled = dagEntitled && !dagSaving,
            saving = dagSaving,
            onCheckedChange = onDagEnabledChanged,
        )
        WebSwitchRow(
            icon = ProductIcon.ShieldCheck,
            title = "Modo Extra Kosher",
            description = "Difumina fotos de contenido y mantiene los videos bloqueados.",
            checked = dagExtraKosherEnabled,
            enabled = dagEnabled && dagEntitled && !dagExtraKosherSaving,
            saving = dagExtraKosherSaving,
            onCheckedChange = onDagExtraKosherEnabledChanged,
        )
        if (blocked && !protectionActive) {
            FeedbackBanner(
                "Protección web no activa: revisá VPN y Accesibilidad en el dispositivo.",
                isError = true,
            )
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
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF1F4F5))
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
                title = "Internet abierto",
                selected = !blocked,
                enabled = !saving,
                icon = ProductIcon.Web,
                onClick = { if (blocked) onBlockedChanged(false) },
            )
            InternetModeOption(
                title = "Internet bloqueado",
                selected = blocked,
                enabled = !saving,
                icon = ProductIcon.ShieldAlert,
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
    icon: ProductIcon,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .height(52.dp)
                .background(if (selected) ProductSky else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductGlyph(
            icon = icon,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
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
    icon: ProductIcon,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductGlyph(icon = icon, color = ProductSky, modifier = Modifier.size(24.dp))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
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
