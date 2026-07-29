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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.core.ui.ProductCard
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
    val feedbackViewModel: AdminFeedbackViewModel = hiltViewModel()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val feedbackState by feedbackViewModel.state.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
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
            Text("Contacto adulto", style = MaterialTheme.typography.titleSmall, color = MutedInk)
            ProductCard {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = phone,
                    onValueChange = { phone = it.take(18) },
                    label = { Text("WhatsApp, por ejemplo +549…") },
                    singleLine = true,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !feedbackState.saving,
                    onClick = { feedbackViewModel.savePhone(phone) },
                ) {
                    Text("Guardar contacto")
                }
            }
            Text("Tu opinión", style = MaterialTheme.typography.titleSmall, color = MutedInk)
            ProductCard {
                Text("Valorar App Administrador", style = MaterialTheme.typography.titleMedium)
                Row {
                    (1..5).forEach { value ->
                        IconButton(onClick = { rating = value }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "$value estrellas",
                                tint = if (value <= rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = comment,
                    onValueChange = { if (it.length <= 1000) comment = it },
                    label = { Text("Comentario opcional") },
                    minLines = 3,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = rating > 0 && !feedbackState.saving,
                    onClick = { feedbackViewModel.submitRating(rating, comment) },
                ) {
                    Text(if (feedbackState.saving) "Guardando…" else "Enviar valoración")
                }
                if (feedbackState.message.isNotBlank()) {
                    Text(feedbackState.message, style = MaterialTheme.typography.bodyMedium)
                }
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
