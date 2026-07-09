package com.contentfilter.user.internet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductVisualPage

@Composable
fun InternetRoute(
    initialDomain: String?,
    onInitialDomainConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InternetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialDomain) {
        if (!initialDomain.isNullOrBlank()) {
            viewModel.selectDomain(initialDomain)
            onInitialDomainConsumed()
        }
        viewModel.refreshRecentDomains()
    }
    InternetScreen(
        state = state,
        onDomainChanged = viewModel::onDomainChanged,
        onRequestOneHour = { viewModel.requestOneHour() },
        onRequestAccess = { viewModel.requestAccess() },
        onRequestOneHourForDomain = { domain -> viewModel.requestOneHour(domain) },
        onRequestAccessForDomain = { domain -> viewModel.requestAccess(domain) },
        modifier = modifier,
    )
}

@Composable
private fun InternetScreen(
    state: InternetUiState,
    onDomainChanged: (String) -> Unit,
    onRequestOneHour: () -> Unit,
    onRequestAccess: () -> Unit,
    onRequestOneHourForDomain: (String) -> Unit,
    onRequestAccessForDomain: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProductVisualPage(
        modifier = modifier,
        title = "Internet",
        subtitle = "Pedidos de acceso web",
    ) {
        if (state.message.isNotBlank()) {
            PremiumFeedbackBanner(text = state.message, isError = state.message.startsWith("No se pudo"))
        }
        ProductLargeFeatureCard(
            title = "Acceso web",
            subtitle = "El bloqueo principal sigue enfocado en apps, pero podés pedir acceso a sitios bloqueados.",
            accent = ProductSky,
        )
        ProductCard {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.domainInput,
                onValueChange = onDomainChanged,
                label = { Text("Pegar link o dominio") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequestOneHour,
                    enabled = !state.isSending,
                ) {
                    Text("Pedir 1 hora")
                }
                OutlinedButton(
                    onClick = onRequestAccess,
                    enabled = !state.isSending,
                ) {
                    Text("Pedir acceso")
                }
            }
        }
        Text("Últimos sitios bloqueados", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.recentBlockedDomains.isEmpty()) {
                Text("Todavía no hay sitios bloqueados recientes.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.recentBlockedDomains.forEach { item ->
                    BlockedDomainRow(
                        item = item,
                        onRequestOneHour = { onRequestOneHourForDomain(item.domain) },
                        onRequestAccess = { onRequestAccessForDomain(item.domain) },
                        sending = state.isSending,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedDomainRow(
    item: BlockedDomainUiState,
    onRequestOneHour: () -> Unit,
    onRequestAccess: () -> Unit,
    sending: Boolean,
) {
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.domain, style = MaterialTheme.typography.titleSmall)
                if (item.pending) {
                    Text("Esperando respuesta adm", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onRequestOneHour,
                    enabled = !sending && !item.pending,
                ) {
                    Text("1 hora")
                }
                Button(
                    onClick = onRequestAccess,
                    enabled = !sending && !item.pending,
                ) {
                    Text("Acceso")
                }
            }
        }
    }
}
