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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshStatusPill
import com.contentfilter.core.ui.GloshSurfaceCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductPageHeader
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
    LaunchedEffect(refreshKey) { viewModel.refresh() }
    val selectedUser = state.users.firstOrNull { it.deviceId == state.selectedDeviceId }
    var showingHistory by remember(state.selectedDeviceId) { mutableStateOf(false) }
    var confirmClearHistory by remember(state.selectedDeviceId) { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProductPageHeader(
            title = "Solicitudes",
            subtitle =
                selectedUser?.let { "${it.name} · ${state.requests.size} pendientes" }
                    ?: "${state.requests.size} pendientes · ${state.users.size} usuarios",
            onBack = onBack,
        )

        RequestsRefreshRow(state = state, onRefresh = viewModel::refresh)

        if (state.users.isNotEmpty()) {
            UserFilterRow(
                users = state.users,
                selectedDeviceId = state.selectedDeviceId,
                onAll = viewModel::clearUserSelection,
                onUser = viewModel::selectUser,
            )
        }

        RequestModeSelector(
            pendingCount = state.requests.size,
            historyCount = state.resolvedRequests.size,
            showingHistory = showingHistory,
            onPending = { showingHistory = false },
            onHistory = { showingHistory = true },
        )

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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(displayed, key = { it.id }) { request ->
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
        modifier = Modifier.fillMaxWidth(),
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
    onAll: () -> Unit,
    onUser: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterPill(
                text = "Todos",
                count = users.sumOf { it.pendingCount },
                selected = selectedDeviceId == null,
                onClick = onAll,
            )
        }
        items(users, key = { it.deviceId }) { user ->
            FilterPill(
                text = user.name,
                count = user.pendingCount,
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
    if (selected) {
        Button(onClick = onClick, shape = GloshShapes.Pill) {
            Text("$text${if (count > 0) " · $count" else ""}")
        }
    } else {
        OutlinedButton(onClick = onClick, shape = GloshShapes.Pill) {
            Text("$text${if (count > 0) " · $count" else ""}")
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!showingHistory) {
            Button(modifier = Modifier.weight(1f), onClick = onPending) { Text("Pendientes ($pendingCount)") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onHistory) { Text("Historial ($historyCount)") }
        } else {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onPending) { Text("Pendientes ($pendingCount)") }
            Button(modifier = Modifier.weight(1f), onClick = onHistory) { Text("Historial ($historyCount)") }
        }
    }
}

@Composable
private fun EmptyRequestsState(showingHistory: Boolean) {
    GloshSurfaceCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(if (showingHistory) ProductIcon.Requests else ProductIcon.ShieldCheck)
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
    GloshSurfaceCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppRequestIcon(appName = item.appName, iconBase64 = item.iconBase64)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.appName, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    item.userName,
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
                Text(
                    when (request.requestType) {
                        AccessRequestType.DOMAIN_ACCESS -> "Sitio web"
                        AccessRequestType.APP_ACCESS -> "Acceso a app"
                        AccessRequestType.EXTRA_TIME -> "Más tiempo"
                        AccessRequestType.OTHER -> "Solicitud"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = GloshColors.Graphite,
                )
            }
            StatusChip(
                request.status.displayName(),
                when (request.status) {
                    RequestStatus.PendingLocal, RequestStatus.PendingRemote -> GloshColors.Warning
                    RequestStatus.Approved -> GloshColors.Positive
                    RequestStatus.Rejected -> GloshColors.Danger
                    RequestStatus.Expired -> GloshColors.Muted
                },
            )
        }

        if (request.reason.isNotBlank()) {
            Text(request.reason, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
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
                .size(58.dp)
                .clip(GloshShapes.Card)
                .background(GloshColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(52.dp).clip(GloshShapes.Small),
            )
        } else {
            ProductGlyph(ProductIcon.Apps, GloshColors.Graphite, Modifier.size(28.dp))
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
