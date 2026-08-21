package com.contentfilter.admin.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshWordmark
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
fun AdminAuthRoute(viewModel: AdminAuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminAuthScreen(
        state = state,
        onCode = viewModel::onActivationCodeChanged,
        onEmail = viewModel::onEmailChanged,
        onPassword = viewModel::onPasswordChanged,
        onConfirmPassword = viewModel::onConfirmPasswordChanged,
        onActivate = viewModel::activate,
        onRequestReset = viewModel::requestResetLocalAdmin,
        onDismissReset = viewModel::dismissResetLocalAdmin,
        onConfirmReset = viewModel::resetLocalAdmin,
    )
}

@Composable
private fun AdminAuthScreen(
    state: AdminAuthUiState,
    onCode: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    onActivate: () -> Unit,
    onRequestReset: () -> Unit,
    onDismissReset: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    if (state.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text("Cambiar administrador") },
            text = { Text("Se quitará el administrador guardado en este teléfono para poder ingresar un token nuevo.") },
            confirmButton = {
                ProgressActionButton(
                    modifier = Modifier,
                    text = "Continuar",
                    loadingText = "Preparando…",
                    successText = "Listo",
                    onClick = onConfirmReset,
                    loading = state.loading,
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissReset) { Text("Cancelar") }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GloshWordmark()
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Activar Administrador", style = MaterialTheme.typography.headlineMedium, color = GloshColors.Graphite)
            Text(
                "Vinculá este teléfono con tu comunidad.",
                style = MaterialTheme.typography.bodyLarge,
                color = GloshColors.Muted,
            )
        }

        val bannerText = state.message.ifBlank { if (state.offlineMode) "Sin conexión. Intentá de nuevo cuando tengas Internet." else "" }
        if (bannerText.isNotBlank()) {
            FeedbackBanner(
                text = bannerText,
                isError = state.offlineMode || bannerText.startsWith("No se pudo"),
            )
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.activated) {
                ProductCard {
                    Text("Administrador activo", style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
                    Text(
                        "Este teléfono ya quedó vinculado y no necesita iniciar sesión cada vez que abrís la app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GloshColors.Muted,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        onClick = onRequestReset,
                    ) {
                        Text("Cambiar administrador")
                    }
                }
                return@Column
            }

            ProductCard {
                Text("Datos de activación", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    "Usá el token de administrador que recibiste para esta comunidad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.activationCode,
                    onValueChange = onCode,
                    label = { Text("Token") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.email,
                    onValueChange = onEmail,
                    label = { Text("Email del administrador") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.password,
                    onValueChange = onPassword,
                    label = { Text("Contraseña") },
                    supportingText = { Text("Mínimo 8 caracteres. Protege la cuenta de administrador.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.confirmPassword,
                    onValueChange = onConfirmPassword,
                    label = { Text("Repetir contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                ProgressActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    onClick = onActivate,
                    loading = state.loading,
                    loadingText = "Activando…",
                    successText = "Administrador activo",
                    text = "Activar administrador",
                )
            }
        }
    }
}
