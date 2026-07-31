package com.contentfilter.user

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.user.updates.UserFeedbackViewModel

@Composable
internal fun UserSettingsTab(
    activationSummary: String,
    updateSummary: String,
    onProtection: () -> Unit,
    onUpdates: () -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
) {
    ProductVisualPage(
        title = "Ajustes",
        subtitle = "Elegí qué querés configurar",
    ) {
        ProductListSurface {
            SettingsIndexRow(
                icon = ProductIcon.ShieldCheck,
                title = "Protección y activación",
                subtitle = activationSummary.ifBlank { "Estado y código de emergencia" },
                onClick = onProtection,
            )
            SettingsIndexRow(
                icon = ProductIcon.Update,
                title = "Actualizaciones e instalaciones",
                subtitle = updateSummary,
                onClick = onUpdates,
            )
            SettingsIndexRow(
                icon = ProductIcon.Search,
                title = "Ayuda",
                subtitle = "Asistente y respuestas sobre la aplicación",
                onClick = onHelp,
            )
            SettingsIndexRow(
                icon = ProductIcon.People,
                title = "Tu opinión",
                subtitle = "Valorá App Usuario y dejanos un comentario",
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
        title = "Protección y activación",
        subtitle = "Estado y acceso de emergencia",
        onBack = onBack,
    ) {
        ProductListSurface {
            ProductListRow(
                leading = {
                    ProductGlyph(
                        ProductIcon.ShieldCheck,
                        MaterialTheme.colorScheme.primary,
                        Modifier.size(24.dp),
                    )
                },
                headline = { Text("Activación", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text(activationState.ifBlank { "Revisando…" }) },
                showDivider = false,
            )
        }
        ProductCard {
            Text("Código de emergencia", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ingresalo solamente si el administrador te dio un código para autorizar una desinstalación sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = recoveryCode,
                onValueChange = onRecoveryCodeChanged,
                label = { Text("Código de emergencia") },
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
        subtitle = "Ayudanos a mejorar App Usuario",
        onBack = onBack,
    ) {
        ProductCard {
            Text("Valorar App Usuario", style = MaterialTheme.typography.titleMedium)
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
                onClick = { viewModel.submit(rating, comment) },
            ) {
                Text(if (state.saving) "Enviando…" else "Enviar valoración")
            }
            if (state.ratingAvailableAtEpochMillis > System.currentTimeMillis()) {
                Text("Ya valoraste esta app. Podés volver a hacerlo en 7 días.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
        leading = {
            ProductGlyph(
                icon = icon,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        headline = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supporting = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        trailing = {
            ProductGlyph(
                ProductIcon.ChevronRight,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.size(22.dp),
            )
        },
        onClick = onClick,
        showDivider = showDivider,
    )
}
