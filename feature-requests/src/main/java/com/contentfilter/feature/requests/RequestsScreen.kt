package com.contentfilter.feature.requests

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductPageHeader
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RequestsRoute(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    RequestsScreen(
        state = state.value,
        onBack = onBack,
        onRefresh = viewModel::refreshRequests,
        modifier = modifier,
    )
}

@Composable
fun RequestsScreen(
    state: RequestsUiState,
    onBack: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductPageHeader(
            title = "Solicitudes",
            subtitle = "${state.pendingCount} pendientes",
            onBack = onBack,
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
                color = if (state.message.isRefreshError()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing,
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
        if (state.requests.isEmpty()) {
            Text(
                "Todavía no hay solicitudes. Pedilas desde Mis apps.",
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(state.requests, key = { _, request -> request.id }) { index, request ->
                    UserRequestRow(
                        request = request,
                        grant = state.extraTimeGrants.firstOrNull { it.requestId == request.id },
                        showDivider = index < state.requests.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun requestsRefreshStatus(state: RequestsUiState): String {
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

internal fun RequestsUiState.refreshStatusText(nowEpochMillis: Long): String =
    when {
        isRefreshing -> "Actualizando…"
        message.isRefreshError() -> "No se pudo actualizar"
        lastRefreshedAtEpochMillis != null -> {
            val minutes = ((nowEpochMillis - lastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
            if (minutes == 0L) "Actualizado ahora" else "Actualizado hace $minutes min"
        }
        else -> "Listo para actualizar"
    }

private fun String.isRefreshError(): Boolean = startsWith("No se pudo") || startsWith("Sin conexión")

@Composable
private fun UserRequestRow(
    request: AccessRequest,
    grant: ExtraTimeGrant?,
    showDivider: Boolean,
) {
    val target = rememberRequestTarget(request)
    val statusColor =
        when (request.status) {
            RequestStatus.PendingLocal,
            RequestStatus.PendingRemote,
            -> MaterialTheme.colorScheme.primary
            RequestStatus.Approved -> MaterialTheme.colorScheme.secondary
            RequestStatus.Rejected -> MaterialTheme.colorScheme.error
            RequestStatus.Expired -> MaterialTheme.colorScheme.outline
        }
    ProductListRow(
        leading = { RequestIcon(target) },
        headline = {
            Text(target.title, style = MaterialTheme.typography.titleSmall)
        },
        supporting = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "${request.requestType.displayName()} · ${request.createdAtEpochMillis.toDisplayDate()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (request.reason.isNotBlank()) {
                    Text(
                        request.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                grant?.let {
                    Text(
                        "Tiempo extra: ${it.grantedMinutes} min · hasta ${it.validUntilEpochMillis.toDisplayDate()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailing = {
            Text(
                request.status.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        },
        showDivider = showDivider,
    )
}

private fun AccessRequestType.displayName(): String =
    when (this) {
        AccessRequestType.APP_ACCESS -> "Solicitud de app"
        AccessRequestType.DOMAIN_ACCESS -> "Solicitud no disponible"
        AccessRequestType.EXTRA_TIME -> "Solicitud de tiempo extra"
        AccessRequestType.OTHER -> "Solicitud"
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

@Composable
private fun rememberRequestTarget(request: AccessRequest): RequestTargetUi {
    val context = LocalContext.current
    return remember(request.id, request.target, request.targetPackageName, request.targetDomain) {
        val packageName = request.targetPackageName ?: request.target.takeIf { it.contains(".") }
        if (request.requestType == AccessRequestType.DOMAIN_ACCESS) {
            RequestTargetUi(
                title = "Solicitud no disponible",
                icon = null,
                web = true,
            )
        } else {
            val packageManager = context.packageManager
            val appInfo =
                packageName?.let {
                    runCatching { packageManager.getApplicationInfo(it, 0) }.getOrNull()
                }
            RequestTargetUi(
                title =
                    appInfo
                        ?.loadLabel(packageManager)
                        ?.toString()
                        ?.takeIf { it.isNotBlank() && !it.startsWith("com.") }
                        ?: request.requestType.fallbackTitle(request.requestedMinutes),
                icon = appInfo?.loadIcon(packageManager),
                web = false,
            )
        }
    }
}

@Composable
private fun RequestIcon(target: RequestTargetUi) {
    val bitmap =
        remember(target.icon) {
            target.icon?.let { runCatching { it.toBitmap(width = 96, height = 96) }.getOrNull() }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (target.web) "WEB" else "APP", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class RequestTargetUi(
    val title: String,
    val icon: Drawable?,
    val web: Boolean,
)

private fun AccessRequestType.fallbackTitle(requestedMinutes: Int?): String =
    when (this) {
        AccessRequestType.APP_ACCESS -> "App"
        AccessRequestType.DOMAIN_ACCESS -> "Solicitud no disponible"
        AccessRequestType.EXTRA_TIME -> "Tiempo extra ${requestedMinutes ?: 0} min"
        AccessRequestType.OTHER -> "Solicitud"
    }

private fun Drawable.toBitmap(
    width: Int,
    height: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
