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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ActivationRoute(
    modifier: Modifier = Modifier,
    notice: String = "",
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    ActivationScreen(
        state = state.value,
        onActivationCodeChanged = viewModel::onActivationCodeChanged,
        onDeviceNameChanged = viewModel::onDeviceNameChanged,
        onActivate = viewModel::activate,
        notice = notice,
        modifier = modifier,
    )
}

@Composable
fun ActivationScreen(
    state: ActivationUiState,
    onActivationCodeChanged: (String) -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    onActivate: () -> Unit,
    notice: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Enlazar dispositivo", style = MaterialTheme.typography.headlineSmall)
        if (state.activated) {
            Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (notice.isNotBlank()) {
            Text(text = notice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = state.deviceName,
            onValueChange = onDeviceNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.activationCode,
            onValueChange = onActivationCodeChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código del administrador") },
            singleLine = true,
        )
        Button(onClick = onActivate, enabled = !state.isLoading) {
            Text(if (state.isLoading) "Enlazando" else "Enlazar")
        }
        Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
    }
}
