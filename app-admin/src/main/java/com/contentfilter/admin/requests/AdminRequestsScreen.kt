package com.contentfilter.admin.requests

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.core.ui.ProductSectionHeader
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.delay

@Composable
fun AdminRequestsRoute(
    refreshKey: Int = 0,
    onBack: (() -> Unit)? = null,
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
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val selectedUser = state.users.firstOrNull { it.deviceId == state.selectedDeviceId }
        ProductPageHeader(
            title = selectedUser?.name ?: "Solicitudes",
            subtitle =
                selectedUser?.let {
                    "${state.requests.size} pendientes · ${state.resolvedRequests.size} en historial"
                } ?: "${state.users.sumOf { it.pendingCount }} pendientes",
            onBack = if (selectedUser != null) viewModel::clearUserSelection else onBack,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = requestsRefreshStatus(state),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.lastSyncMessage.startsWith("No se pudo") || state.offlineMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = viewModel::refresh,
                enabled = !state.isLoading,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .semantics { contentDescription = "Actualizar solicitudes" },
            ) {
                ProductGlyph(
                    icon = ProductIcon.Refresh,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (selectedUser == null) {
            ProductSectionHeader("Usuarios", count = state.users.size)
            if (state.users.isEmpty()) {
                Text("No hay solicitudes para revisar.")
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(state.users, key = { it.deviceId }) { user ->
                    RequestUserCard(
                        user = user,
                        onClick = { viewModel.selectUser(user.deviceId) },
                    )
                }
            }
        } else {
            var showingHistory by remember(selectedUser.deviceId) { mutableStateOf(false) }
            var confirmClearHistory by remember(selectedUser.deviceId) { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showingHistory) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showingHistory = false }) {
                        Text("Pendientes (${state.requests.size})")
                    }
                    androidx.compose.material3.Button(modifier = Modifier.weight(1f), onClick = {}) {
                        Text("Historial (${state.resolvedRequests.size})")
                    }
                } else {
                    androidx.compose.material3.Button(modifier = Modifier.weight(1f), onClick = {}) {
                        Text("Pendientes (${state.requests.size})")
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showingHistory = true }) {
                        Text("Historial (${state.resolvedRequests.size})")
                    }
                }
            }
            if (showingHistory && state.resolvedRequests.isNotEmpty()) {
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = { confirmClearHistory = true },
                ) {
                    Text("Borrar historial")
                }
            }
            if (confirmClearHistory) {
                AlertDialog(
                    onDismissRequest = { confirmClearHistory = false },
                    title = { Text("Borrar historial") },
                    text = {
                        Text("Se ocultará el historial de este usuario en esta aplicación. Las solicitudes remotas no se borran.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearHistory(state.resolvedRequests.map { it.id }.toSet())
                                confirmClearHistory = false
                            },
                        ) {
                            Text("Borrar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearHistory = false }) {
                            Text("Cancelar")
                        }
                    },
                )
            }
            val displayedRequests = if (showingHistory) state.resolvedRequests else state.requests
            if (displayedRequests.isEmpty()) {
                Text(if (showingHistory) "Todavía no hay solicitudes resueltas." else "No hay solicitudes pendientes.")
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(displayedRequests, key = { it.id }) { request ->
                    RequestCard(
                        item = request,
                        pendingActionIds = state.pendingActionIds,
                        onApprove = { viewModel.approve(request.request.id) },
                        onReject = { viewModel.reject(request.request.id) },
                        onGrant = { minutes -> viewModel.grantTime(request.request, minutes) },
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
    ProductListRow(
        onClick = onClick,
        leading = {
            ProductGlyph(
                icon = ProductIcon.Requests,
                color = if (user.needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        headline = {
            Text(user.name, style = MaterialTheme.typography.titleMedium)
        },
        supporting = {
            Text(
                "${user.pendingCount} pendientes · ${user.resolvedCount} en historial",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            ProductGlyph(
                icon = ProductIcon.ChevronRight,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
private fun requestsRefreshStatus(state: AdminRequestsUiState): String {
    var nowEpochMillis by remember(state.lastRefreshedAtEpochMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lastRefreshedAtEpochMillis) {
        if (state.lastRefreshedAtEpochMillis != null) {
            while (true) {
                delay(60_000)
                nowEpochMillis = System.currentTimeMillis()
            }
        }
    }
    return state.refreshStatusText(nowEpochMillis)
}

internal fun AdminRequestsUiState.refreshStatusText(nowEpochMillis: Long): String =
    when {
        isLoading -> "Actualizando…"
        lastSyncMessage.startsWith("No se pudo") || offlineMode -> "No se pudo actualizar"
        lastRefreshedAtEpochMillis != null -> {
            val minutes = ((nowEpochMillis - lastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
            if (minutes == 0L) "Actualizado ahora" else "Actualizado hace $minutes min"
        }
        else -> "Listo para actualizar"
    }

@Composable
private fun RequestCard(
    item: AdminAccessRequestUiState,
    pendingActionIds: Set<String>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onGrant: (String) -> Unit,
) {
    val request = item.request
    val approveLoading = "${request.id}:approve" in pendingActionIds
    val grantLoading = "${request.id}:grant" in pendingActionIds
    val rejectLoading = "${request.id}:reject" in pendingActionIds
    val actionLoading = approveLoading || grantLoading || rejectLoading
    var grantMinutes by remember(request.id, request.requestedMinutes) {
        mutableStateOf(request.requestedMinutes?.toString().orEmpty())
    }
    ProductCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppRequestIcon(appName = item.appName, iconBase64 = item.iconBase64)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (request.requestType) {
                            AccessRequestType.DOMAIN_ACCESS -> "Solicitud de sitio"
                            AccessRequestType.APP_ACCESS -> "Solicitud de aplicación"
                            AccessRequestType.EXTRA_TIME -> "Solicitud de tiempo"
                            AccessRequestType.OTHER -> "Solicitud"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusChip(
                    request.status.displayName(),
                    when (request.status) {
                        RequestStatus.PendingLocal, RequestStatus.PendingRemote -> MaterialTheme.colorScheme.primary
                        RequestStatus.Approved -> Color(0xFF17895D)
                        RequestStatus.Rejected -> MaterialTheme.colorScheme.error
                        RequestStatus.Expired -> MaterialTheme.colorScheme.outline
                    },
                )
            }
            if (request.reason.isNotBlank()) {
                Text(request.reason, style = MaterialTheme.typography.bodySmall)
            }
            if (request.status.isPending()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (request.requestType != AccessRequestType.DOMAIN_ACCESS) {
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
                    }
                    ProgressActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onApprove,
                        enabled = !actionLoading,
                        loading = approveLoading,
                        loadingText = "Aprobando...",
                        successText = "Aprobada",
                        text = if (request.requestType == AccessRequestType.DOMAIN_ACCESS) "Permitir sitio" else "Acceso completo",
                    )
                    if (request.requestType != AccessRequestType.DOMAIN_ACCESS) {
                        ProgressActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onGrant(grantMinutes) },
                            enabled = !actionLoading && grantMinutes.toIntOrNull()?.let { it > 0 } == true,
                            loading = grantLoading,
                            loadingText = "Guardando...",
                            successText = "Tiempo dado",
                            text = "Dar tiempo",
                        )
                    }
                    ProgressActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onReject,
                        enabled = !actionLoading,
                        loading = rejectLoading,
                        loadingText = "Rechazando...",
                        successText = "Rechazada",
                        text = "Rechazar",
                        tone = ActionButtonTone.Destructive,
                    )
                }
            } else {
                Text("Solicitud ${request.status.displayName().lowercase()}.")
            }
        }
    }
}

@Composable
private fun AppRequestIcon(
    appName: String,
    iconBase64: String?,
) {
    val bitmap =
        remember(iconBase64) {
            iconBase64?.let {
                runCatching {
                    val normalized = it.substringAfter("base64,", it)
                    val bytes = Base64.decode(normalized, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF1F5F7)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            )
        } else {
            ProductGlyph(
                icon = ProductIcon.Apps,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

private fun RequestStatus.isPending(): Boolean =
    this == RequestStatus.PendingLocal || this == RequestStatus.PendingRemote

private fun RequestStatus.displayName(): String =
    when (this) {
        RequestStatus.PendingLocal,
        RequestStatus.PendingRemote,
        -> "Pendiente"
        RequestStatus.Approved -> "Aprobada"
        RequestStatus.Rejected -> "Rechazada"
        RequestStatus.Expired -> "Expirada"
    }
