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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onAccount: () -> Unit,
    onContact: () -> Unit,
    onPanel: () -> Unit,
    onUpdates: () -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
    onLocalAdmin: () -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(title = "Ajustes", subtitle = "Elegí qué querés configurar")
        ProductListSurface {
            SettingsNavigationRow(
                icon = ProductIcon.People,
                title = "Cuenta y comunidad",
                subtitle = state.communityName.ifBlank { "Identidad, comunidad y licencia" },
                onClick = onAccount,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Bell,
                title = "Contacto adulto",
                subtitle = "Número de contacto de la comunidad",
                onClick = onContact,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Panel,
                title = "Panel administrador",
                subtitle = "Estado general y sincronización",
                onClick = onPanel,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Update,
                title = "Actualizaciones",
                subtitle = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = onUpdates,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Search,
                title = "Ayuda",
                subtitle = "Asistente y respuestas sobre la aplicación",
                onClick = onHelp,
            )
            SettingsNavigationRow(
                icon = ProductIcon.People,
                title = "Tu opinión",
                subtitle = "Valorá App Administrador",
                onClick = onFeedback,
            )
            SettingsNavigationRow(
                icon = ProductIcon.ShieldAlert,
                title = "Administrador de este teléfono",
                subtitle = "Cambiar el acceso local guardado",
                onClick = onLocalAdmin,
                showDivider = false,
            )
        }
    }
}

@Composable
internal fun AdminAccountDetailsScreen() {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    SettingsDetailColumn {
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
    }
}

@Composable
internal fun AdminContactSettingsRoute(viewModel: AdminFeedbackViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.clearMessage()
    }
    SettingsDetailColumn {
        ProductCard {
            Text("Contacto adulto", style = MaterialTheme.typography.titleMedium)
            Text(
                "Este número queda asociado a la comunidad para comunicaciones administrativas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = phone,
                onValueChange = { phone = it.take(18) },
                label = { Text("WhatsApp, por ejemplo +549…") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
                onClick = { viewModel.savePhone(phone) },
            ) {
                Text(if (state.saving) "Guardando…" else "Guardar contacto")
            }
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun AdminFeedbackSettingsRoute(viewModel: AdminFeedbackViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.clearMessage()
    }
    SettingsDetailColumn {
        ProductCard {
            Text("Valorar App Administrador", style = MaterialTheme.typography.titleMedium)
            Text("Tu calificación ayuda a mejorar la aplicación.", style = MaterialTheme.typography.bodyMedium)
            Row {
                (1..5).forEach { value ->
                    IconButton(onClick = { rating = value }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "$value estrellas",
                            tint =
                                if (value <= rating) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
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
                enabled = rating > 0 && !state.saving,
                onClick = { viewModel.submitRating(rating, comment) },
            ) {
                Text(if (state.saving) "Enviando…" else "Enviar valoración")
            }
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsDetailColumn(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        content()
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
