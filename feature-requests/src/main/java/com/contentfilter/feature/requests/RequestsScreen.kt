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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.ExtraTimeGrant
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductVisualPage
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
    ProductVisualPage(
        modifier = modifier,
        title = "Solicitudes",
        subtitle = "${state.pendingCount} pendientes",
        onBack = onBack,
        banner =
            if (state.message.isNotBlank()) {
                {
                    PremiumFeedbackBanner(text = state.message, isError = state.message.startsWith("No se pudo"))
                }
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) "Actualizando..." else "Actualizar")
            }
        }
        ProductListSurface {
            if (state.requests.isEmpty()) {
                Text(
                    "Todavía no hay solicitudes. Pedilas desde Mis apps.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.requests.forEachIndexed { index, request ->
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
