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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsTab(
    onAccount: () -> Unit,
    onContact: () -> Unit,
    onPanel: () -> Unit,
    onUpdates: () -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
    onLocalAdmin: () -> Unit,
    hasPendingUpdate: Boolean,
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
                title = "Actualizar datos",
                subtitle = "Mail y número de celular",
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
                showBlueDot = hasPendingUpdate,
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
                title = "Borrar cuenta del administrador",
                subtitle = "Borra el acceso local solo en este teléfono",
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
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.contactLoaded) {
        if (state.contactLoaded) {
            contactEmail = state.contactEmail
            phone = state.phoneE164
        }
    }
    LaunchedEffect(Unit) {
        viewModel.clearMessage()
    }
    SettingsDetailColumn {
        ProductCard {
            Text("Actualizar datos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Estos datos se usan para comunicaciones administrativas. No cambian el mail de inicio de sesión.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = contactEmail,
                onValueChange = { contactEmail = it.take(160) },
                label = { Text("Mail de contacto") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = phone,
                onValueChange = { phone = it.take(18) },
                label = { Text("Número de celular, por ejemplo +549…") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && state.contactLoaded,
                onClick = { viewModel.saveContact(contactEmail, phone) },
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
                enabled = rating > 0 && !state.saving && state.ratingAvailableAtEpochMillis <= System.currentTimeMillis(),
                onClick = { viewModel.submitRating(rating, comment) },
            ) {
                Text(if (state.saving) "Enviando…" else "Enviar valoración")
            }
            if (state.ratingAvailableAtEpochMillis > System.currentTimeMillis()) {
                Text(ratingAvailabilityText(state.ratingAvailableAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(ratingAvailabilityText(0L), style = MaterialTheme.typography.bodySmall)
            }
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun ratingAvailabilityText(nextAvailableAtEpochMillis: Long): String {
    if (nextAvailableAtEpochMillis <= System.currentTimeMillis()) {
        return "Podés valorar una vez cada 7 días."
    }
    val dateTime = Instant.ofEpochMilli(nextAvailableAtEpochMillis).atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
    val locale = Locale.forLanguageTag("es-AR")
    val date = dateTime.format(DateTimeFormatter.ofPattern("d/M", locale))
    val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return "Podés volver a valorar el $date a las $time."
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
    showBlueDot: Boolean = false,
) {
    ProductListRow(
        leading = { ProductGlyph(icon = icon, color = Teal, modifier = Modifier.size(24.dp)) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = Ink) },
        supporting = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showBlueDot) {
                    Box(
                        modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                ProductGlyph(icon = ProductIcon.ChevronRight, color = MutedInk, modifier = Modifier.size(22.dp))
            }
        },
        onClick = onClick,
        showDivider = showDivider,
    )
}
