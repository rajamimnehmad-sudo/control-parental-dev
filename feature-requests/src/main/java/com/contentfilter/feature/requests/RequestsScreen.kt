package com.contentfilter.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.RequestStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RequestsRoute(
    modifier: Modifier = Modifier,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    RequestsScreen(
        state = state.value,
        modifier = modifier,
    )
}

@Composable
fun RequestsScreen(
    state: RequestsUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(text = "Mis solicitudes", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(text = "Pendientes: ${state.pendingCount}", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.requests.isEmpty()) {
            item {
                Text("No hay solicitudes pendientes. Pedilas desde Mis apps o Internet.")
            }
        } else {
            items(state.requests, key = { it.id }) { request ->
                UserRequestCard(
                    request = request,
                    grant = state.extraTimeGrants.firstOrNull { it.requestId == request.id },
                )
            }
        }
    }
}

@Composable
private fun UserRequestCard(
    request: AccessRequest,
    grant: ExtraTimeGrant?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(request.requestType.displayName(), style = MaterialTheme.typography.titleSmall)
            Text("Objetivo: ${request.displayTarget()}")
            if (request.reason.isNotBlank()) {
                Text("Motivo: ${request.reason}")
            }
            Text("Fecha: ${request.createdAtEpochMillis.toDisplayDate()}")
            Text("Estado: ${request.status.displayName()}")
            grant?.let {
                Text(
                    "Tiempo extra concedido: ${it.grantedMinutes} min hasta ${it.validUntilEpochMillis.toDisplayDate()}",
                )
            }
            if (request.status == RequestStatus.Approved) {
                Text("Respuesta del administrador: aprobada.")
            } else if (request.status == RequestStatus.Rejected) {
                Text("Respuesta del administrador: rechazada.")
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
        AccessRequestType.EXTRA_TIME -> "${requestedMinutes ?: 0} minutos"
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

private fun Long.toDisplayDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
