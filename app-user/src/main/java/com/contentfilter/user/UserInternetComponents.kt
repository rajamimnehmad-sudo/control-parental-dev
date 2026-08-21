package com.contentfilter.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductPageHeader
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
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProductPageHeader(
            title = "Internet",
            subtitle = "Estado de tu navegación",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                UserInternetStatusCard(
                    state = state,
                    vpnActive = vpnActive,
                    onRepair = onActivateWebProtection,
                )
            }
            item {
                Text(
                    "Cómo está configurado",
                    style = MaterialTheme.typography.labelLarge,
                    color = GloshColors.Muted,
                )
            }
            item {
                InternetProtectionList(
                    state = state,
                    vpnActive = vpnActive,
                    onOpenProtectedBrowser = onOpenProtectedBrowser,
                    onInstallProtectedBrowser = onInstallProtectedBrowser,
                    protectedBrowserAvailable = protectedBrowserAvailable,
                    onActivateWebProtection = onActivateWebProtection,
                )
            }
        }
    }
}

@Composable
private fun UserInternetStatusCard(
    state: com.contentfilter.user.internet.UserWebUiState,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(status.softColor, GloshShapes.Card),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(
                    icon = status.icon,
                    color = status.color,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.label, style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            }
            if (!vpnActive && !state.webNavigationBlocked && !scheduleBlocked) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onRepair) {
                    Text("Reparar protección")
                }
            }
        }
    }
}

@Composable
private fun InternetProtectionList(
    state: com.contentfilter.user.internet.UserWebUiState,
    vpnActive: Boolean,
    onOpenProtectedBrowser: () -> Unit,
    onInstallProtectedBrowser: () -> Unit,
    protectedBrowserAvailable: Boolean,
    onActivateWebProtection: () -> Unit,
) {
    ProductListSurface {
        if (!vpnActive) {
            InternetProtectionRow(
                icon = ProductIcon.ShieldAlert,
                label = "Protección de Internet",
                value = "Requiere atención",
                active = false,
                trailing = { TextButton(onClick = onActivateWebProtection) { Text("Reparar") } },
            )
        }
        InternetProtectionRow(
            icon = ProductIcon.Search,
            label = "Búsquedas seguras",
            value = if (state.safeSearchEnabled) "Activas" else "Por revisar",
            active = state.safeSearchEnabled,
        )
        InternetProtectionRow(
            icon = ProductIcon.Web,
            label = "Navegación",
            value = if (state.onlyResultsEnabled) "Solo resultados de búsqueda" else "Sitios permitidos habilitados",
            active = true,
        )
        if (protectedBrowserAvailable || state.protectedBrowserRequired) {
            InternetProtectionRow(
                icon = ProductIcon.ShieldCheck,
                label = "Navegador protegido",
                value =
                    when {
                        state.protectedBrowserRequired && protectedBrowserAvailable -> "Listo para usar"
                        state.protectedBrowserRequired -> "Falta instalarlo"
                        else -> "Disponible"
                    },
                active = protectedBrowserAvailable,
                onClick = if (protectedBrowserAvailable) onOpenProtectedBrowser else onInstallProtectedBrowser,
                showDivider = state.schedule != null,
                navigation = true,
            )
        }
        state.schedule?.let { schedule ->
            InternetProtectionRow(
                icon = ProductIcon.Update,
                label = "Horario",
                value = schedule.summary,
                active = schedule.isAllowed,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun InternetProtectionRow(
    icon: ProductIcon,
    label: String,
    value: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    navigation: Boolean = false,
    showDivider: Boolean = true,
) {
    ProductListRow(
        leading = {
            Box(
                modifier = Modifier.size(40.dp).background(GloshColors.LimeSoft, GloshShapes.Small),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(icon = icon, color = GloshColors.Graphite, modifier = Modifier.size(21.dp))
            }
        },
        headline = { Text(label, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite) },
        supporting = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) GloshColors.Positive else GloshColors.Warning,
            )
        },
        trailing =
            trailing
                ?: if (navigation) {
                    { ProductGlyph(icon = ProductIcon.ChevronRight, color = GloshColors.Muted, modifier = Modifier.size(22.dp)) }
                } else {
                    null
                },
        onClick = onClick,
        showDivider = showDivider,
    )
}

private enum class InternetVisualStatus(
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val softColor: androidx.compose.ui.graphics.Color,
    val icon: ProductIcon,
) {
    Protected("Internet protegido", GloshColors.Positive, GloshColors.PositiveSoft, ProductIcon.ShieldCheck),
    Review("Protección por revisar", GloshColors.Warning, GloshColors.WarningSoft, ProductIcon.ShieldAlert),
    Blocked("Internet bloqueado", GloshColors.Graphite, GloshColors.SurfaceMuted, ProductIcon.Web),
}
