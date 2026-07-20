package com.contentfilter.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
                .background(AppBackground),
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
            AccountStatusCard(
                title = state.guideName.ifBlank { "Administrador" },
                lines =
                    listOfNotNull(
                        "Rol: Administrador (ADM)",
                        state.communityName.takeIf(String::isNotBlank)?.let { "Comunidad: $it" },
                        "Superweb: ${syncStatusLabel(state.offlineMode, state.syncState)}",
                    ),
                accent = Violet,
            )
            AccountStatusCard(
                title = licenseSummary(state.licenseState, state.licenseExpiresAtEpochMillis),
                lines =
                    listOfNotNull(
                        state.licenseExpiresAtEpochMillis?.let { "Vencimiento: ${formatArgentinaDate(it)}" },
                        licenseEffectText(state.licenseState),
                    ),
                accent = state.licenseState.accountColor,
            )
            FeatureTile(
                icon = Icons.Filled.Settings,
                title = "Panel administrador",
                subtitle = "Estado general, comunidad y sincronización",
                accent = Teal,
                onClick = onPanel,
            )
            FeatureTile(
                icon = Icons.Filled.Refresh,
                title = "Actualizaciones",
                subtitle = "Buscar versión y cambiar administrador local",
                accent = Sun,
                onClick = onUpdates,
            )
            FeatureTile(
                icon = Icons.Filled.Search,
                title = "Ayuda",
                subtitle = "Asistente interactivo según el estado actual",
                accent = Violet,
                onClick = onHelp,
            )
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
private fun AccountStatusCard(
    title: String,
    lines: List<String>,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.size(12.dp).background(accent, CircleShape))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                }
            }
        }
    }
}
