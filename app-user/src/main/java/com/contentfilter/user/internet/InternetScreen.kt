package com.contentfilter.user.internet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Internet", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.message.isNotBlank()) {
            item {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.domainInput,
                onValueChange = onDomainChanged,
                label = { Text("Pegar link o dominio") },
                singleLine = true,
            )
        }
        item {
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
        item {
            Text("Últimos sitios bloqueados", style = MaterialTheme.typography.titleMedium)
        }
        if (state.recentBlockedDomains.isEmpty()) {
            item {
                Text("Todavía no hay sitios bloqueados recientes.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(state.recentBlockedDomains, key = { it.domain }) { item ->
            BlockedDomainRow(
                item = item,
                onRequestOneHour = { onRequestOneHourForDomain(item.domain) },
                onRequestAccess = { onRequestAccessForDomain(item.domain) },
                sending = state.isSending,
            )
            HorizontalDivider()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
