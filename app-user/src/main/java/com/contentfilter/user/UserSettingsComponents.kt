package com.contentfilter.user

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.updates.UserFeedbackViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun UserSettingsTab(
    activationSummary: String,
    updateSummary: String,
    onProtection: () -> Unit,
    onUpdates: () -> Unit,
    onContact: () -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
) {
    ProductVisualPage(
        title = "Ajustes",
        subtitle = "Protección, actualizaciones y ayuda",
    ) {
        ProductListSurface {
            SettingsIndexRow(
                icon = ProductIcon.ShieldCheck,
                title = "Protección",
                subtitle = activationSummary.ifBlank { "Estado y recuperación" },
                onClick = onProtection,
            )
            SettingsIndexRow(
                icon = ProductIcon.Update,
                title = "Actualizaciones",
                subtitle = updateSummary,
                onClick = onUpdates,
            )
            SettingsIndexRow(
                icon = ProductIcon.People,
                title = "Contacto",
                subtitle = "Datos opcionales para comunicaciones importantes",
                onClick = onContact,
            )
            SettingsIndexRow(
                icon = ProductIcon.Search,
                title = "Ayuda",
                subtitle = "Respuestas y asistencia sobre Glosh",
                onClick = onHelp,
            )
            SettingsIndexRow(
                icon = ProductIcon.Star,
                title = "Tu opinión",
                subtitle = "Contanos cómo podemos mejorar",
                onClick = onFeedback,
                showDivider = false,
            )
        }
    }
}

@Composable
internal fun UserProtectionSettingsScreen(
    activationState: String,
    recoveryCode: String,
    protectionMessage: String,
    onRecoveryCodeChanged: (String) -> Unit,
    onSubmitRecoveryCode: () -> Unit,
    onBack: () -> Unit,
) {
    ProductVisualPage(
        title = "Protección",
        subtitle = "Estado y acceso de emergencia",
        onBack = onBack,
    ) {
        ProductListSurface {
            ProductListRow(
                leading = { GloshIconBubble(ProductIcon.ShieldCheck) },
                headline = { Text("Estado de la protección", style = MaterialTheme.typography.titleMedium) },
                supporting = {
                    Text(
                        activationState.ifBlank { "Revisando…" },
                        color = GloshColors.Muted,
                    )
                },
                showDivider = false,
            )
        }
        ProductCard {
            Text("Código de emergencia", style = MaterialTheme.typography.titleMedium)
            Text(
                "Usalo solamente si tu administrador te dio un código para autorizar una desinstalación sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = recoveryCode,
                onValueChange = onRecoveryCodeChanged,
                label = { Text("Código") },
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = recoveryCode.isNotBlank(),
                onClick = onSubmitRecoveryCode,
            ) {
                Text("Validar código")
            }
            if (protectionMessage.isNotBlank()) {
                Text(protectionMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun UserFeedbackSettingsRoute(
    onBack: () -> Unit,
    viewModel: UserFeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    ProductVisualPage(
        title = "Tu opinión",
        subtitle = "Ayudanos a mejorar Glosh",
        onBack = onBack,
    ) {
        ProductCard {
            Text("¿Cómo viene funcionando?", style = MaterialTheme.typography.titleMedium)
            Text("Tu calificación nos ayuda a mejorar.", style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
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
                onClick = { viewModel.submit(rating, comment) },
            ) {
                Text(if (state.saving) "Enviando…" else "Enviar")
            }
            Text(ratingAvailabilityText(state.ratingAvailableAtEpochMillis), style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun UserContactSettingsRoute(
    onBack: () -> Unit,
    viewModel: UserFeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.contactLoaded) {
        if (state.contactLoaded) {
            contactEmail = state.contactEmail
            phone = state.phoneE164
        }
    }
    ProductVisualPage(
        title = "Contacto",
        subtitle = "Datos opcionales para comunicaciones importantes",
        onBack = onBack,
    ) {
        ProductCard {
            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Podés dejarlos vacíos. Se usan únicamente para comunicaciones relacionadas con este dispositivo.",
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
                singleLine = true,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.contactLoaded && !state.saving,
                onClick = { viewModel.saveContact(contactEmail, phone) },
            ) {
                Text(if (state.saving) "Guardando…" else "Guardar")
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
private fun SettingsIndexRow(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    ProductListRow(
        leading = { GloshIconBubble(icon) },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite) },
        supporting = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted) },
        trailing = {
            ProductGlyph(
                ProductIcon.ChevronRight,
                GloshColors.Muted,
                Modifier,
            )
        },
        onClick = onClick,
        showDivider = showDivider,
    )
}
