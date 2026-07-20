package com.contentfilter.admin.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.StatusChip
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminAlertsRoute(viewModel: AdminAlertsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminAlertsScreen(state = state, onRefresh = viewModel::refresh)
}

@Composable
private fun AdminAlertsScreen(
    state: AdminAlertsUiState,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Acá aparecen desactivaciones confirmadas, posibles desinstalaciones y pedidos de mantenimiento. Los intentos bloqueados quedan en Super Admin.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh,
            enabled = !state.loading,
        ) {
            Text(if (state.loading) "Actualizando..." else "Actualizar alertas")
        }
        if (state.message.isNotBlank()) {
            PremiumFeedbackBanner(
                text = state.message,
                isError = state.message.startsWith("No se pudo"),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.alerts, key = AdminAlertUiState::id) { alert ->
                ProductCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(
                            if (alert.alertType == "possible_uninstall") {
                                "ALERTA MÁXIMA"
                            } else {
                                "Requiere atención"
                            },
                            MaterialTheme.colorScheme.error,
                        )
                        Text(alert.title, style = MaterialTheme.typography.titleMedium)
                        Text(alert.body, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(alert.createdAtEpochMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
