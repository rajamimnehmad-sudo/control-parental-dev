package com.contentfilter.admin.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AdminAuthRoute(viewModel: AdminAuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminAuthScreen(
        state = state,
        onEmail = viewModel::onEmailChanged,
        onPassword = viewModel::onPasswordChanged,
        onCode = viewModel::onActivationCodeChanged,
        onName = viewModel::onDeviceNameChanged,
        onActivate = viewModel::activate,
    )
}

@Composable
private fun AdminAuthScreen(
    state: AdminAuthUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onCode: (String) -> Unit,
    onName: (String) -> Unit,
    onActivate: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Login / Activacion", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        if (state.activated) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
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
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.activationCode,
            onValueChange = onCode,
            label = { Text("Codigo de activacion") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.deviceName,
            onValueChange = onName,
            label = { Text("Nombre del administrador") },
            singleLine = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            onClick = onActivate,
        ) {
            Text("Activar")
        }
        Text(state.message)
    }
}
