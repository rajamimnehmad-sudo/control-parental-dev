package com.contentfilter.admin.requests

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.core.ui.ProgressActionButton
import kotlinx.coroutines.delay

@Composable
fun AdminRequestsRoute(
    refreshKey: Int = 0,
    onBack: (() -> Unit)? = null,
    viewModel: AdminRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { viewModel.refresh() }
    val selectedUser = state.users.firstOrNull { it.deviceId == state.selectedDeviceId }
    var showingHistory by rememberSaveable(state.selectedDeviceId) { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable(state.selectedDeviceId) { mutableStateOf(false) }
    var selectedRequestId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 14.dp),
    ) {
        ProductPageHeader(
            title = "Solicitudes",
            subtitle =
                selectedUser?.let { "${it.name} · ${state.requests.size} pendientes" }
                    ?: "${state.requests.size} pendientes · ${state.users.size} usuarios",
            onBack = onBack,
        )

        RequestsRefreshRow(state = state, onRefresh = viewModel::refresh)

        RequestModeSelector(
            pendingCount = state.requests.size,
            historyCount = state.resolvedRequests.size,
            showingHistory = showingHistory,
            onPending = { showingHistory = false },
            onHistory = { showingHistory = true },
        )

        if (state.users.isNotEmpty()) {
            Text(
                "Filtrar por usuario",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = GloshColors.Muted,
            )
            UserFilterRow(
                users = state.users,
                selectedDeviceId = state.selectedDeviceId,
                showingHistory = showingHistory,
                onAll = viewModel::clearUserSelection,
                onUser = viewModel::selectUser,
            )
        }

        if (showingHistory && selectedUser != null && state.resolvedRequests.isNotEmpty()) {
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { confirmClearHistory = true },
            ) {
                Text("Borrar historial de ${selectedUser.name}", color = GloshColors.Danger)
            }
        }

        val displayed = if (showingHistory) state.resolvedRequests else state.requests
        if (displayed.isEmpty()) {
            EmptyRequestsState(showingHistory = showingHistory)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 12.dp),
            ) {
                items(displayed, key = { it.id }) { request ->
                    RequestListRow(
                        item = request,
                        onClick = { selectedRequestId = request.id },
                    )
                }
            }
        }
    }

    val selectedRequest =
        (state.requests + state.resolvedRequests).firstOrNull { it.id == selectedRequestId }
    if (selectedRequest != null) {
        RequestDecisionDialog(
            item = selectedRequest,
            pendingActionIds = state.pendingActionIds,
            onDismiss = { selectedRequestId = null },
            onApprove = { viewModel.approve(selectedRequest.request.id) },
            onReject = { viewModel.reject(selectedRequest.request.id) },
            onGrant = { minutes -> viewModel.grantTime(selectedRequest.request, minutes) },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Borrar historial") },
            text = {
                Text(
                    "Se ocultará el historial de este usuario en esta aplicación. Las solicitudes remotas no se borran.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory(state.resolvedRequests.map { it.id }.toSet())
                        confirmClearHistory = false
                    },
                ) {
                    Text("Borrar", color = GloshColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun RequestsRefreshRow(
    state: AdminRequestsUiState,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = requestsRefreshStatus(state),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (state.lastSyncMessage.startsWith("No se pudo") || state.offlineMode) {
                    GloshColors.Danger
                } else {
                    GloshColors.Muted
                },
        )
        IconButton(
            onClick = onRefresh,
            enabled = !state.isLoading,
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GloshColors.Surface)
                    .semantics { contentDescription = "Actualizar solicitudes" },
        ) {
            ProductGlyph(ProductIcon.Refresh, GloshColors.Graphite, Modifier.size(21.dp))
        }
    }
}

@Composable
private fun UserFilterRow(
    users: List<AdminRequestUserUiState>,
    selectedDeviceId: String?,
    showingHistory: Boolean,
    onAll: () -> Unit,
    onUser: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterPill(
                text = "Todos",
                count = if (showingHistory) users.sumOf { it.resolvedCount } else users.sumOf { it.pendingCount },
                selected = selectedDeviceId == null,
                onClick = onAll,
            )
        }
        items(users, key = { it.deviceId }) { user ->
            FilterPill(
                text = user.name,
                count = if (showingHistory) user.resolvedCount else user.pendingCount,
                selected = selectedDeviceId == user.deviceId,
                onClick = { onUser(user.deviceId) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(GloshShapes.Pill)
                .background(if (selected) GloshColors.Graphite else GloshColors.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) GloshColors.Surface else GloshColors.Graphite,
        )
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) GloshColors.Lime else GloshColors.Muted,
            )
        }
    }
}

@Composable
private fun RequestModeSelector(
    pendingCount: Int,
    historyCount: Int,
    showingHistory: Boolean,
    onPending: () -> Unit,
    onHistory: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .background(GloshColors.SurfaceMuted, GloshShapes.Pill)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RequestModeOption(
            modifier = Modifier.weight(1f),
            text = "Pendientes · $pendingCount",
            selected = !showingHistory,
            onClick = onPending,
        )
        RequestModeOption(
            modifier = Modifier.weight(1f),
            text = "Historial · $historyCount",
            selected = showingHistory,
            onClick = onHistory,
        )
    }
}

@Composable
private fun RequestModeOption(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(GloshShapes.Pill)
                .background(if (selected) GloshColors.Lime else GloshColors.SurfaceMuted)
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = GloshColors.Graphite)
    }
}

@Composable
private fun EmptyRequestsState(showingHistory: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(GloshShapes.Small).background(GloshColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(
                if (showingHistory) ProductIcon.Requests else ProductIcon.ShieldCheck,
                if (showingHistory) GloshColors.Graphite else GloshColors.Positive,
                Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (showingHistory) "Sin historial" else "Todo al día",
                style = MaterialTheme.typography.titleMedium,
                color = GloshColors.Graphite,
            )
            Text(
                if (showingHistory) "Todavía no hay solicitudes resueltas." else "No hay solicitudes esperando respuesta.",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
        }
    }
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
private fun RequestListRow(
    item: AdminAccessRequestUiState,
    onClick: () -> Unit,
) {
    val request = item.request
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppRequestIcon(appName = item.appName, iconBase64 = item.iconBase64)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.appName, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite, maxLines = 1)
            Text(
                "${item.userName} · ${request.requestType.requestTypeLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
                maxLines = 1,
            )
            if (request.reason.isNotBlank()) {
                Text(request.reason, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                request.status.displayName(),
                style = MaterialTheme.typography.labelSmall,
                color = request.status.statusColor(),
            )
            ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(20.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = GloshColors.Line)
}

@Composable
private fun RequestDecisionDialog(
    item: AdminAccessRequestUiState,
    pendingActionIds: Set<String>,
    onDismiss: () -> Unit,
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

    AlertDialog(
        onDismissRequest = { if (!actionLoading) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.appName)
                Text(
                    item.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Muted,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(request.requestType.requestTypeLabel(), style = MaterialTheme.typography.labelLarge)
                    Text(
                        request.status.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = request.status.statusColor(),
                    )
                }
                if (request.reason.isNotBlank()) {
                    Text(request.reason, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
                }
                if (request.status.isPending()) {
                    if (request.requestType != AccessRequestType.DOMAIN_ACCESS) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = grantMinutes,
                            onValueChange = { grantMinutes = it.filter(Char::isDigit) },
                            label = { Text("Minutos opcionales") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    ProgressActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onApprove,
                        enabled = !actionLoading,
                        loading = approveLoading,
                        loadingText = "Aprobando…",
                        successText = "Aprobada",
                        text = if (request.requestType == AccessRequestType.DOMAIN_ACCESS) "Permitir sitio" else "Permitir",
                    )
                    if (request.requestType != AccessRequestType.DOMAIN_ACCESS) {
                        ProgressActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onGrant(grantMinutes) },
                            enabled = !actionLoading && grantMinutes.toIntOrNull()?.let { it > 0 } == true,
                            loading = grantLoading,
                            loadingText = "Guardando…",
                            successText = "Tiempo dado",
                            text = "Dar tiempo",
                        )
                    }
                    ProgressActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onReject,
                        enabled = !actionLoading,
                        loading = rejectLoading,
                        loadingText = "Rechazando…",
                        successText = "Rechazada",
                        text = "Rechazar",
                        tone = ActionButtonTone.Destructive,
                    )
                } else {
                    Text(
                        "Esta solicitud ya fue resuelta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GloshColors.Muted,
                    )
                }
            }
        },
        confirmButton = {
            if (!request.status.isPending()) {
                Button(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            if (request.status.isPending()) {
                TextButton(enabled = !actionLoading, onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
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
                .size(44.dp)
                .clip(GloshShapes.Small)
                .background(GloshColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(40.dp).clip(GloshShapes.Small),
            )
        } else {
            ProductGlyph(ProductIcon.Apps, GloshColors.Graphite, Modifier.size(23.dp))
        }
    }
}

private fun AccessRequestType.requestTypeLabel(): String =
    when (this) {
        AccessRequestType.DOMAIN_ACCESS -> "Sitio web"
        AccessRequestType.APP_ACCESS -> "Acceso a app"
        AccessRequestType.EXTRA_TIME -> "Más tiempo"
        AccessRequestType.OTHER -> "Solicitud"
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

private fun RequestStatus.statusColor() =
    when (this) {
        RequestStatus.PendingLocal,
        RequestStatus.PendingRemote,
        -> GloshColors.Warning
        RequestStatus.Approved -> GloshColors.Positive
        RequestStatus.Rejected -> GloshColors.Danger
        RequestStatus.Expired -> GloshColors.Muted
    }
