package com.contentfilter.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface

@Composable
internal fun SettingsTab(
    onPanel: () -> Unit,
    onUpdates: () -> Unit,
    onHelp: () -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(title = "Cuenta", subtitle = "Licencia, estado y versión")
            Text("Cuenta y comunidad", style = MaterialTheme.typography.titleSmall, color = MutedInk)
            ProductListSurface {
                AccountStatusRow(
                    icon = ProductIcon.People,
                    title = state.guideName.ifBlank { "Administrador" },
                    lines =
                        listOfNotNull(
                            "Rol: Administrador (ADM)",
                            state.communityName.takeIf(String::isNotBlank)?.let { "Comunidad: $it" },
                            "Superweb: ${syncStatusLabel(state.offlineMode, state.syncState)}",
                        ),
                )
                AccountStatusRow(
                    icon = ProductIcon.ShieldCheck,
                    title = licenseSummary(state.licenseState, state.licenseExpiresAtEpochMillis),
                    lines =
                        listOfNotNull(
                            state.licenseExpiresAtEpochMillis?.let { "Vencimiento: ${formatArgentinaDate(it)}" },
                            licenseEffectText(state.licenseState),
                        ),
                    showDivider = false,
                )
            }
            Text("Más", style = MaterialTheme.typography.titleSmall, color = MutedInk)
            ProductListSurface {
                SettingsNavigationRow(
                    icon = ProductIcon.Panel,
                    title = "Panel administrador",
                    subtitle = "Estado general, comunidad y sincronización",
                    onClick = onPanel,
                )
                SettingsNavigationRow(
                    icon = ProductIcon.Update,
                    title = "Actualizaciones",
                    subtitle = "Buscar versión y cambiar administrador local",
                    onClick = onUpdates,
                )
                SettingsNavigationRow(
                    icon = ProductIcon.Search,
                    title = "Ayuda",
                    subtitle = "Asistente interactivo según el estado actual",
                    onClick = onHelp,
                    showDivider = false,
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onUpdates) {
                Text("Ver novedades")
            }
        }
    }
}

@Composable
private fun AccountStatusRow(
    icon: ProductIcon,
    title: String,
    lines: List<String>,
    showDivider: Boolean = true,
) {
    ProductListRow(
        leading = { ProductGlyph(icon = icon, color = Teal, modifier = Modifier.size(24.dp)) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = Ink) },
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = MutedInk) }
            }
        },
        showDivider = showDivider,
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    ProductListRow(
        leading = { ProductGlyph(icon = icon, color = Teal, modifier = Modifier.size(24.dp)) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = Ink) },
        supporting = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk) },
        trailing = { ProductGlyph(icon = ProductIcon.ChevronRight, color = MutedInk, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
        showDivider = showDivider,
    )
}
