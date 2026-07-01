package com.contentfilter.admin.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.RequestStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AdminRequestsRoute(
    viewModel: AdminRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Solicitudes", style = MaterialTheme.typography.headlineSmall)
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        Text("Solicitudes locales: ${state.requests.size}", style = MaterialTheme.typography.bodyMedium)
        if (state.lastSyncMessage.isNotBlank()) {
            Text(state.lastSyncMessage, style = MaterialTheme.typography.bodySmall)
        }
        if (state.requests.isEmpty()) {
            Text("No hay solicitudes para revisar.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.requests, key = { it.id }) { request ->
                RequestCard(
                    request = request,
                    onApprove = { viewModel.approve(request.id) },
                    onReject = { viewModel.reject(request.id) },
                    onGrant = { viewModel.grantTime(request) },
                )
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: AccessRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(request.requestType.displayName(), style = MaterialTheme.typography.titleMedium)
            Text("Objetivo: ${request.displayTarget()}")
            Text("Estado: ${request.status.displayName()}")
            Text("Fecha: ${request.createdAtEpochMillis.toDisplayDate()}")
            if (request.reason.isNotBlank()) {
                Text("Motivo: ${request.reason}")
            }
            request.requestedMinutes?.let { minutes ->
                Text("Minutos pedidos: $minutes")
            }
            if (request.status.isPending()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (request.requestType == AccessRequestType.EXTRA_TIME) {
                        Button(onClick = onGrant) { Text("Conceder tiempo") }
                    } else {
                        Button(onClick = onApprove) { Text("Aprobar") }
                    }
                    OutlinedButton(onClick = onReject) { Text("Rechazar") }
                }
            } else {
                Text("Solicitud ${request.status.displayName().lowercase()}.")
            }
        }
    }
}

private fun AccessRequestType.displayName(): String =
    when (this) {
        AccessRequestType.APP_ACCESS -> "Solicitud de app"
        AccessRequestType.DOMAIN_ACCESS -> "Solicitud de sitio web"
        AccessRequestType.EXTRA_TIME -> "Solicitud de tiempo extra"
        AccessRequestType.OTHER -> "Solicitud"
    }

private fun AccessRequest.displayTarget(): String =
    when (requestType) {
        AccessRequestType.APP_ACCESS -> targetPackageName ?: target
        AccessRequestType.DOMAIN_ACCESS -> targetDomain ?: target
        AccessRequestType.EXTRA_TIME -> targetPackageName ?: targetDomain ?: target
        AccessRequestType.OTHER -> target
    }

private fun RequestStatus.displayName(): String =
    when (this) {
        RequestStatus.PendingLocal,
        RequestStatus.PendingRemote,
        -> "Pendiente"
        RequestStatus.Approved -> "Aprobada"
        RequestStatus.Rejected -> "Rechazada"
        RequestStatus.Expired -> "Expirada"
    }

private fun RequestStatus.isPending(): Boolean =
    this == RequestStatus.PendingLocal || this == RequestStatus.PendingRemote

private fun Long.toDisplayDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
