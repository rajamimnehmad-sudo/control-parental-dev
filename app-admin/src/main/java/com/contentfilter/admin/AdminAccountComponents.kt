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
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.GloshSpacing
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
                .background(GloshColors.Bone)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(title = "Ajustes", subtitle = "Cuenta, soporte y opciones de la app")
        ProductListSurface {
            SettingsNavigationRow(
                icon = ProductIcon.People,
                title = "Cuenta y comunidad",
                subtitle = state.communityName.ifBlank { "Identidad y licencia" },
                onClick = onAccount,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Bell,
                title = "Contacto",
                subtitle = "Mail y número para comunicaciones importantes",
                onClick = onContact,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Update,
                title = "Actualizaciones",
                subtitle = "Versión ${BuildConfig.VERSION_NAME}",
                onClick = onUpdates,
                showAttentionDot = hasPendingUpdate,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Search,
                title = "Ayuda",
                subtitle = "Respuestas y asistencia sobre Glosh",
                onClick = onHelp,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Star,
                title = "Tu opinión",
                subtitle = "Contanos cómo podemos mejorar",
                onClick = onFeedback,
            )
            SettingsNavigationRow(
                icon = ProductIcon.Panel,
                title = "Estado y diagnóstico",
                subtitle = "Información avanzada para soporte",
                onClick = onPanel,
            )
            SettingsNavigationRow(
                icon = ProductIcon.ShieldAlert,
                title = "Administrador de este teléfono",
                subtitle = "Cambiar el administrador vinculado localmente",
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
                        state.communityName.takeIf(String::isNotBlank)?.let { "Comunidad: $it" },
                        if (state.offlineMode) "Trabajando con datos guardados" else "Conectado con la comunidad",
                    ),
            )
            AccountStatusRow(
                icon = ProductIcon.ShieldCheck,
                title = licenseSummary(state.licenseState, state.licenseExpiresAtEpochMillis),
                lines =
                    listOfNotNull(
                        state.licenseExpiresAtEpochMillis?.let { "Vence: ${formatArgentinaDate(it)}" },
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
    LaunchedEffect(Unit) { viewModel.clearMessage() }
    SettingsDetailColumn {
        ProductCard {
            Text("Datos de contacto", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(
                "Son opcionales y se usan para comunicaciones administrativas. No cambian el acceso a tu cuenta.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = contactEmail,
                onValueChange = { contactEmail = it.take(160) },
                label = { Text("Mail") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = phone,
                onValueChange = { phone = it.take(18) },
                label = { Text("Número de celular") },
                placeholder = { Text("+54 9…") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && state.contactLoaded,
                onClick = { viewModel.saveContact(contactEmail, phone) },
            ) {
                Text(if (state.saving) "Guardando…" else "Guardar")
            }
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AdminFeedbackSettingsRoute(viewModel: AdminFeedbackViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.clearMessage() }
    SettingsDetailColumn {
        ProductCard {
            Text("¿Cómo viene funcionando?", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text("Tu calificación nos ayuda a mejorar Glosh.", style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            Row {
                (1..5).forEach { value ->
                    IconButton(onClick = { rating = value }) {
                        ProductGlyph(
                            icon = ProductIcon.Star,
                            color = if (value <= rating) GloshColors.Warning else MaterialTheme.colorScheme.outlineVariant,
                            contentDescription = "$value estrellas",
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
                Text(if (state.saving) "Enviando…" else "Enviar")
            }
            Text(
                ratingAvailabilityText(state.ratingAvailableAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun ratingAvailabilityText(nextAvailableAtEpochMillis: Long): String {
    if (nextAvailableAtEpochMillis <= System.currentTimeMillis()) return "Podés valorar una vez cada 7 días."
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
                .background(GloshColors.Bone)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 4.dp),
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
        leading = { GloshIconBubble(icon) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite) },
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted) }
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
    showAttentionDot: Boolean = false,
) {
    ProductListRow(
        leading = { GloshIconBubble(icon) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite) },
        supporting = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showAttentionDot) Box(modifier = Modifier.size(8.dp).background(GloshColors.Lime, CircleShape))
                ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(22.dp))
            }
        },
        onClick = onClick,
        showDivider = showDivider,
    )
}
