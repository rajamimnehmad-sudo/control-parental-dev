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
import androidx.compose.material3.Button
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
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductHeader

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
            title = { Text("Resetear esta app") },
            text = { Text("Se borrará el admin guardado en este teléfono para poder ingresar un token nuevo.") },
            confirmButton = {
                ProgressActionButton(
                    modifier = Modifier,
                    text = "Resetear",
                    loadingText = "Reseteando...",
                    successText = "Listo",
                    onClick = onConfirmReset,
                    loading = state.loading,
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissReset) {
                    Text("Cancelar")
                }
            },
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProductHeader(
            title = "Activación Admin",
            subtitle = "Ingresá el token de administrador para activar este panel",
        )
        if (state.offlineMode) {
            FeedbackBanner("Sin conexion. Mostrando datos guardados.", isError = true)
        }
        if (state.activated) {
            FeedbackBanner(state.message)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
                onClick = onRequestReset,
            ) {
                Text("Ingresar nuevo token")
            }
            return@Column
        }
        ProductCard {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.activationCode,
                onValueChange = onCode,
                label = { Text("Token de administrador") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.email,
                onValueChange = onEmail,
                label = { Text("Email") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Contraseña") },
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
                loadingText = "Activando...",
                successText = "Admin activado",
                text = "Activar",
            )
        }
        FeedbackBanner(state.message, isError = state.message.startsWith("No se pudo"))
    }
}
