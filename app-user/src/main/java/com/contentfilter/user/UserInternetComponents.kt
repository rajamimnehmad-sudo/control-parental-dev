package com.contentfilter.user

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.user.internet.UserWebUiState
import com.contentfilter.user.internet.UserWebViewModel

@Composable
internal fun UserWebTab(
    onBack: (() -> Unit)?,
    onOpenProtectedBrowser: () -> Unit,
    onInstallProtectedBrowser: () -> Unit,
    protectedBrowserAvailable: Boolean,
    vpnActive: Boolean,
    onActivateWebProtection: () -> Unit,
    viewModel: UserWebViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 14.dp),
    ) {
        ProductPageHeader(
            title = "Internet",
            subtitle = "Estado, horario y protecciones",
            onBack = onBack,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                InternetStatusBlock(
                    state = state,
                    vpnActive = vpnActive,
                    onRepair = onActivateWebProtection,
                )
            }
            item { InternetSectionTitle("Protecciones") }
            if (!vpnActive) {
                item {
                    InternetFlatRow(
                        icon = ProductIcon.ShieldAlert,
                        title = "Protección de Internet",
                        subtitle = "Necesita volver a activarse",
                        status = "Reparar",
                        statusTone = InternetTone.Warning,
                        onClick = onActivateWebProtection,
                    )
                }
            }
            item {
                InternetFlatRow(
                    icon = ProductIcon.Search,
                    title = "Búsquedas seguras",
                    subtitle = if (state.safeSearchEnabled) "Aplicadas automáticamente" else "Por revisar",
                    status = if (state.safeSearchEnabled) "OK" else "Revisar",
                    statusTone = if (state.safeSearchEnabled) InternetTone.Positive else InternetTone.Warning,
                )
            }
            item {
                InternetFlatRow(
                    icon = ProductIcon.Web,
                    title = "Navegación",
                    subtitle = if (state.onlyResultsEnabled) "Solo resultados de búsqueda" else "Sitios permitidos habilitados",
                    status = if (state.webNavigationBlocked) "Bloqueada" else "Activa",
                    statusTone = if (state.webNavigationBlocked) InternetTone.Neutral else InternetTone.Positive,
                )
            }
            if (protectedBrowserAvailable || state.protectedBrowserRequired) {
                item {
                    InternetFlatRow(
                        icon = ProductIcon.ShieldCheck,
                        title = "Navegador protegido",
                        subtitle =
                            when {
                                state.protectedBrowserRequired && protectedBrowserAvailable -> "Listo para usar"
                                state.protectedBrowserRequired -> "Falta instalarlo"
                                else -> "Disponible"
                            },
                        status = if (protectedBrowserAvailable) "Abrir" else "Instalar",
                        statusTone = if (protectedBrowserAvailable) InternetTone.Positive else InternetTone.Warning,
                        onClick = if (protectedBrowserAvailable) onOpenProtectedBrowser else onInstallProtectedBrowser,
                    )
                }
            }
            item { InternetSectionTitle("Acceso") }
            state.schedule?.let { schedule ->
                item {
                    InternetFlatRow(
                        icon = ProductIcon.Update,
                        title = "Horario",
                        subtitle = schedule.summary,
                        status = if (schedule.isAllowed) "Activo" else "Fuera de horario",
                        statusTone = if (schedule.isAllowed) InternetTone.Positive else InternetTone.Warning,
                    )
                }
            }
            item {
                InternetFlatRow(
                    icon = ProductIcon.Web,
                    title = "Detalles técnicos",
                    subtitle = "VPN y diagnóstico solo cuando hacen falta",
                    status = null,
                    statusTone = InternetTone.Neutral,
                    onClick = if (!vpnActive) onActivateWebProtection else null,
                    showChevron = !vpnActive,
                )
            }
        }
    }
}

@Composable
private fun InternetStatusBlock(
    state: UserWebUiState,
    vpnActive: Boolean,
    onRepair: () -> Unit,
) {
    val scheduleBlocked = state.schedule?.isAllowed == false
    val status =
        when {
            state.webNavigationBlocked || scheduleBlocked -> InternetVisualStatus.Blocked
            !vpnActive -> InternetVisualStatus.Review
            else -> InternetVisualStatus.Protected
        }
    val summary =
        when {
            state.webNavigationBlocked -> "El administrador pausó la navegación."
            scheduleBlocked -> state.schedule?.summary ?: "Ahora estás fuera del horario permitido."
            !vpnActive -> "La protección de Internet necesita volver a activarse."
            state.schedule != null -> state.schedule.summary
            else -> "Podés navegar con las protecciones configuradas por tu administrador."
        }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "ESTADO ACTUAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GloshColors.Muted,
                )
                Text(
                    status.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GloshColors.Graphite,
                )
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            }
            StatusTag(
                text = status.tag,
                background = status.softColor,
                foreground = status.color,
            )
        }
        if (!vpnActive && !state.webNavigationBlocked && !scheduleBlocked) {
            Button(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), onClick = onRepair) {
                Text("Reparar protección")
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = GloshColors.Line,
        )
    }
}

@Composable
private fun InternetSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = GloshColors.Graphite,
    )
}

@Composable
private fun InternetFlatRow(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    status: String?,
    statusTone: InternetTone,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(GloshShapes.Small).background(GloshColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(icon, GloshColors.Graphite, Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        if (status != null) {
            StatusTag(
                text = status,
                background = statusTone.background,
                foreground = statusTone.foreground,
            )
        } else if (showChevron) {
            ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(21.dp))
        }
    }
    HorizontalDivider(color = GloshColors.Line)
}

@Composable
private fun StatusTag(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier =
            Modifier
                .clip(GloshShapes.Pill)
                .background(background)
                .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = foreground)
    }
}

private enum class InternetTone(
    val foreground: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
) {
    Positive(GloshColors.Positive, GloshColors.PositiveSoft),
    Warning(GloshColors.Warning, GloshColors.WarningSoft),
    Neutral(GloshColors.Muted, GloshColors.SurfaceMuted),
}

private enum class InternetVisualStatus(
    val label: String,
    val tag: String,
    val color: androidx.compose.ui.graphics.Color,
    val softColor: androidx.compose.ui.graphics.Color,
) {
    Protected("Internet abierto", "Protegido", GloshColors.Positive, GloshColors.PositiveSoft),
    Review("Protección por revisar", "Revisar", GloshColors.Warning, GloshColors.WarningSoft),
    Blocked("Internet bloqueado", "Bloqueado", GloshColors.Graphite, GloshColors.SurfaceMuted),
}
