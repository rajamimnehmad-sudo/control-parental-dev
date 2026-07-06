package com.contentfilter.admin.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
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
    refreshKey: Int = 0,
    viewModel: AdminRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) {
        viewModel.refresh()
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Solicitudes", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = viewModel::refresh,
                enabled = !state.isLoading,
            ) {
                Text(if (state.isLoading) "Cargando..." else "Actualizar")
            }
        }
        if (state.offlineMode) {
            Text("Modo Offline / Desarrollo", color = MaterialTheme.colorScheme.error)
        }
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        Text("Pendientes: ${state.users.sumOf { it.pendingCount }}", style = MaterialTheme.typography.bodyMedium)
        if (state.isLoading) {
            Text("Cargando solicitudes...", style = MaterialTheme.typography.bodySmall)
        }
        if (state.lastSyncMessage.isNotBlank()) {
            Text(state.lastSyncMessage, style = MaterialTheme.typography.bodySmall)
        }
        val selectedUser = state.users.firstOrNull { it.deviceId == state.selectedDeviceId }
        if (selectedUser == null) {
            Text("Usuarios con solicitudes: ${state.users.size}", style = MaterialTheme.typography.bodyMedium)
            if (state.users.isEmpty()) {
                Text("No hay solicitudes para revisar.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.users, key = { it.deviceId }) { user ->
                    RequestUserCard(
                        user = user,
                        onClick = { viewModel.selectUser(user.deviceId) },
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(selectedUser.name, style = MaterialTheme.typography.titleMedium)
                    Text("${state.requests.size} pendientes", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = viewModel::clearUserSelection) {
                    Text("Volver")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.requests, key = { it.id }) { request ->
                    RequestCard(
                        request = request,
                        onApprove = { viewModel.approve(request.id) },
                        onReject = { viewModel.reject(request.id) },
                        onGrant = { minutes -> viewModel.grantTime(request, minutes) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestUserCard(
    user: AdminRequestUserUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (user.needsAttention) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                )
            } else {
                Box(modifier = Modifier.size(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text("${user.pendingCount} solicitudes pendientes", style = MaterialTheme.typography.bodySmall)
            }
            Text("Ver", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RequestCard(
    request: AccessRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onGrant: (String) -> Unit,
) {
    var grantMinutes by remember(request.id, request.requestedMinutes) {
        mutableStateOf((request.requestedMinutes ?: 15).toString())
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(request.requestType.displayName(), style = MaterialTheme.typography.titleMedium)
            Text("Objetivo: ${request.displayTarget()}")
            if (request.reason.isNotBlank()) {
                Text("Motivo: ${request.reason}")
            }
            request.requestedMinutes?.let { minutes ->
                Text("Minutos pedidos: $minutes")
            }
            Text("Fecha: ${request.createdAtEpochMillis.toDisplayDate()}", style = MaterialTheme.typography.bodySmall)
            if (request.status.isPending()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = grantMinutes,
                        onValueChange = { grantMinutes = it.filter(Char::isDigit) },
                        label = { Text("Minutos") },
                        singleLine = true,
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                            ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onGrant(grantMinutes) }) { Text("Dar tiempo") }
                        Button(onClick = onApprove) { Text("Acceso completo") }
                        OutlinedButton(onClick = onReject) { Text("Rechazar") }
                    }
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
