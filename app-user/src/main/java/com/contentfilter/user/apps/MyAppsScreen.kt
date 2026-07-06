package com.contentfilter.user.apps

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle

@Composable
fun MyAppsRoute(
    modifier: Modifier = Modifier,
    viewModel: MyAppsViewModel = hiltViewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshApps()
    }
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    MyAppsScreen(
        state = state.value,
        onSearchChanged = viewModel::onSearchChanged,
        onRequestAccess = viewModel::requestAccess,
        onRequestMoreTime = viewModel::requestMoreTime,
        modifier = modifier,
    )
}

@Composable
private fun MyAppsScreen(
    state: MyAppsUiState,
    onSearchChanged: (String) -> Unit,
    onRequestAccess: (String) -> Unit,
    onRequestMoreTime: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Mis aplicaciones", style = MaterialTheme.typography.headlineSmall)
        if (state.message.isNotBlank()) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = onSearchChanged,
            label = { Text("Buscar app") },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.apps.isEmpty()) {
                item {
                    Text("No hay apps detectadas todavía.")
                }
            }
            items(state.apps, key = { it.packageName }) { app ->
                MyAppRow(
                    app = app,
                    onRequestAccess = { onRequestAccess(app.packageName) },
                    onRequestMoreTime = { onRequestMoreTime(app.packageName) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MyAppRow(
    app: MyAppItemUiState,
    onRequestAccess: () -> Unit,
    onRequestMoreTime: () -> Unit,
) {
    val canRequestAccess =
        app.status == AppAccessStatus.Blocked ||
            app.status == AppAccessStatus.RequiresAuthorization
    val canRequestMoreTime =
        app.status == AppAccessStatus.LimitReached
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.name, app.iconBase64)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            StatusLabel(app.status)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(app.limitText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (app.isRequesting) {
                    Text("Enviando...", style = MaterialTheme.typography.bodySmall)
                }
                if (canRequestAccess) {
                    Button(
                        onClick = onRequestAccess,
                        enabled = !app.isRequesting,
                    ) {
                        Text("Acceso completo")
                    }
                }
                if (canRequestMoreTime) {
                    OutlinedButton(
                        onClick = onRequestMoreTime,
                        enabled = !app.isRequesting,
                    ) {
                        Text("Pedir tiempo")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: AppAccessStatus) {
    val color = status.statusColor()
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun AppIcon(
    name: String,
    iconBase64: String?,
) {
    val bitmap =
        remember(iconBase64) {
            iconBase64?.let {
                runCatching {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape),
        )
    } else {
        FallbackIcon(name)
    }
}

@Composable
private fun FallbackIcon(name: String) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun AppAccessStatus.displayName(): String =
    when (this) {
        AppAccessStatus.Allowed -> "Permitida"
        AppAccessStatus.Limited -> "Con límite"
        AppAccessStatus.LimitReached -> "Límite agotado"
        AppAccessStatus.ExtraTime -> "Tiempo extra activo"
        AppAccessStatus.Blocked -> "Bloqueada"
        AppAccessStatus.RequiresAuthorization -> "Requiere autorización"
        AppAccessStatus.WaitingAuthorization -> "Esperando autorización"
        AppAccessStatus.WaitingExtraTime -> "Esperando más tiempo"
    }

private fun AppAccessStatus.statusColor(): Color =
    when (this) {
        AppAccessStatus.Allowed,
        AppAccessStatus.ExtraTime,
        -> AllowedGreen
        AppAccessStatus.Limited,
        AppAccessStatus.LimitReached,
        AppAccessStatus.WaitingExtraTime,
        -> WarningYellow
        AppAccessStatus.Blocked,
        AppAccessStatus.RequiresAuthorization,
        AppAccessStatus.WaitingAuthorization,
        -> BlockedRed
    }

private val AllowedGreen = Color(0xFF2E7D32)
private val BlockedRed = Color(0xFFC62828)
private val WarningYellow = Color(0xFFF9A825)
