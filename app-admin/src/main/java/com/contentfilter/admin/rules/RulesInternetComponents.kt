package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSurfaceCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(rule.target, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                    Text(
                        "${rule.scope.displayName()} · ${rule.action.displayName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GloshColors.Muted,
                    )
                }
                GloshV4Switch(
                    checked = rule.enabled,
                    enabled = enabled,
                    onCheckedChange = { onToggle() },
                )
            }
            dailyLimitMinutes?.let {
                Text("Límite diario: $it min", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
            TextButton(onClick = { confirmDelete = true }, enabled = enabled) {
                Text("Eliminar regla", color = GloshColors.Danger)
            }
        }
    }
    if (flat) {
        Column(modifier = Modifier.fillMaxWidth().background(GloshColors.Surface)) {
            content()
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = GloshColors.Line)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = GloshShapes.Card,
            colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
            border = BorderStroke(1.dp, GloshColors.Line),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { content() }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar regla") },
            text = { Text("Esta configuración se eliminará.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
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
    GloshSurfaceCard {
        Text("Permitir un sitio", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        Text(
            "Agregá una excepción. El límite diario es opcional.",
            style = MaterialTheme.typography.bodyMedium,
            color = GloshColors.Muted,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = domain,
            onValueChange = onDomainChanged,
            label = { Text("Sitio") },
            placeholder = { Text("ejemplo.com") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = minutes,
            onValueChange = onMinutesChanged,
            label = { Text("Límite diario opcional") },
            supportingText = { Text("El tiempo web es estimado.") },
            singleLine = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && domain.isNotBlank(),
            onClick = if (minutes.isBlank()) onAllow else onAllowWithLimit,
        ) {
            Text(if (minutes.isBlank()) "Permitir sitio" else "Permitir con límite")
        }
    }
}

@Composable
internal fun WebNavigationPanel(
    blocked: Boolean,
    onlyResultsEnabled: Boolean,
    protectedBrowserRequired: Boolean,
    presentation: WebPanelPresentation,
    navigationSaving: Boolean,
    onlyResultsSaving: Boolean,
    protectedBrowserSaving: Boolean,
    protectionActive: Boolean,
    protectedBrowserInstalled: Boolean,
    alternativeBrowsers: List<AppControlUiState>,
    onBlockedChanged: (Boolean) -> Unit,
    onOnlyResultsChanged: (Boolean) -> Unit,
    onProtectedBrowserRequiredChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Acceso a Internet", style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
            Text(
                "Primero elegí si puede navegar. Después ajustá el nivel de protección.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
        }
        InternetModeSelector(
            blocked = blocked,
            saving = navigationSaving,
            onBlockedChanged = onBlockedChanged,
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(presentation.headline, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(
                if (blocked) {
                    "Internet está pausado. La configuración queda guardada para cuando lo abras de nuevo."
                } else {
                    "La navegación segura se aplica automáticamente."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
        }

        AnimatedVisibility(visible = presentation.showLayers) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nivel de protección", style = MaterialTheme.typography.titleSmall, color = GloshColors.Graphite)
                WebSwitchRow(
                    icon = ProductIcon.Search,
                    title = "Solo resultados de búsqueda",
                    description = "Permite buscar, pero limita la apertura de sitios externos.",
                    checked = onlyResultsEnabled,
                    enabled = !onlyResultsSaving,
                    saving = onlyResultsSaving,
                    onCheckedChange = onOnlyResultsChanged,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = GloshColors.Line)
                WebSwitchRow(
                    icon = ProductIcon.ShieldCheck,
                    title = "Navegador protegido",
                    description = "Hace obligatorio el navegador de Glosh para contenido visual.",
                    checked = protectedBrowserRequired,
                    enabled = !protectedBrowserSaving,
                    saving = protectedBrowserSaving,
                    onCheckedChange = onProtectedBrowserRequiredChanged,
                )

                AnimatedVisibility(visible = protectedBrowserRequired) {
                    ProtectedBrowserSetup(
                        protectionActive = protectionActive,
                        protectedBrowserInstalled = protectedBrowserInstalled,
                        alternativeBrowsers = alternativeBrowsers,
                    )
                }
            }
        }

        if (!protectionActive) {
            FeedbackBanner(
                "La protección del teléfono necesita atención. Revisá Seguridad antes de terminar.",
                isError = true,
            )
        }
    }
}

@Composable
private fun ProtectedBrowserSetup(
    protectionActive: Boolean,
    protectedBrowserInstalled: Boolean,
    alternativeBrowsers: List<AppControlUiState>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GloshColors.SurfaceMuted, GloshShapes.Card)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Para terminar", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        SetupStatusLine(
            complete = protectedBrowserInstalled,
            text = if (protectedBrowserInstalled) "Navegador protegido instalado" else "Falta instalar el navegador protegido",
        )
        SetupStatusLine(
            complete = protectionActive,
            text = if (protectionActive) "Protección del teléfono activa" else "Falta reparar la protección del teléfono",
        )
        SetupStatusLine(
            complete = false,
            text = "Elegir el navegador protegido como predeterminado",
        )
        Text(
            if (alternativeBrowsers.isEmpty()) {
                "Glosh bloqueará otros navegadores que detecte cuando esta opción sea obligatoria."
            } else {
                "Otros navegadores detectados: " +
                    alternativeBrowsers
                        .map(AppControlUiState::appName)
                        .distinct()
                        .sorted()
                        .joinToString(", ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = GloshColors.Muted,
        )
    }
}

@Composable
private fun SetupStatusLine(
    complete: Boolean,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductGlyph(
            icon = if (complete) ProductIcon.ShieldCheck else ProductIcon.ShieldAlert,
            color = if (complete) GloshColors.Positive else GloshColors.Warning,
            modifier = Modifier.size(19.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Graphite)
    }
}

@Composable
private fun InternetModeSelector(
    blocked: Boolean,
    saving: Boolean,
    onBlockedChanged: (Boolean) -> Unit,
) {
    var dragDistance by remember { mutableStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(GloshColors.SurfaceMuted, GloshShapes.Pill)
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
                title = "Abierto",
                selected = !blocked,
                enabled = !saving,
                icon = ProductIcon.Web,
                onClick = { if (blocked) onBlockedChanged(false) },
            )
            InternetModeOption(
                title = "Bloqueado",
                selected = blocked,
                enabled = !saving,
                icon = ProductIcon.ShieldAlert,
                onClick = { if (!blocked) onBlockedChanged(true) },
            )
        }
        if (saving) {
            Text("Aplicando…", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
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
                .background(if (selected) GloshColors.Lime else Color.Transparent, GloshShapes.Pill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductGlyph(icon = icon, color = GloshColors.Graphite, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = GloshColors.Graphite,
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(GloshColors.Surface, GloshShapes.Small),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(icon = icon, color = GloshColors.Graphite, modifier = Modifier.size(21.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(description, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            if (saving) {
                Text("Guardando…", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
        }
        GloshV4Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun GloshV4Switch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = GloshColors.Lime,
                checkedTrackColor = GloshColors.Graphite,
                checkedBorderColor = GloshColors.Graphite,
                uncheckedThumbColor = GloshColors.Surface,
                uncheckedTrackColor = GloshColors.SurfaceMuted,
                uncheckedBorderColor = GloshColors.Line,
                disabledCheckedThumbColor = GloshColors.Lime.copy(alpha = 0.55f),
                disabledCheckedTrackColor = GloshColors.Graphite.copy(alpha = 0.45f),
            ),
    )
}
