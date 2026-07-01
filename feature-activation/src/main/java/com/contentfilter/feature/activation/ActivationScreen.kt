package com.contentfilter.feature.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ActivationRoute(
    modifier: Modifier = Modifier,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    ActivationScreen(
        state = state.value,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onActivationCodeChanged = viewModel::onActivationCodeChanged,
        onDeviceNameChanged = viewModel::onDeviceNameChanged,
        onActivate = viewModel::activate,
        modifier = modifier,
    )
}

@Composable
fun ActivationScreen(
    state: ActivationUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onActivationCodeChanged: (String) -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Activación", style = MaterialTheme.typography.headlineSmall)
        if (state.activated) {
            Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        OutlinedTextField(state.email, onEmailChanged, Modifier.fillMaxWidth(), label = { Text("Email") })
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(state.activationCode, onActivationCodeChanged, Modifier.fillMaxWidth(), label = { Text("Código") })
        OutlinedTextField(state.deviceName, onDeviceNameChanged, Modifier.fillMaxWidth(), label = { Text("Nombre del dispositivo") })
        Button(onClick = onActivate, enabled = !state.isLoading) {
            Text(if (state.isLoading) "Activando" else "Activar")
        }
        Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
    }
}
