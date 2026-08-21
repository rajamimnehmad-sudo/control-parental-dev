package com.contentfilter.admin.rules

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshSurfaceCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import kotlinx.coroutines.delay

@Composable
internal fun UsersListContent(
    entryMode: RulesEntryMode,
    createUserRequestKey: Int,
    onCreateUserRequestConsumed: () -> Unit,
    state: RulesUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onBack: (() -> Unit)?,
    onRefreshDevices: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onPairingCodeCopied: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onShowArchivedUsers: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(createUserRequestKey) {
        if (createUserRequestKey > 0 && entryMode == RulesEntryMode.ManageUsers) {
            showCreateDialog = true
            onCreateUserRequestConsumed()
        }
    }
    var userSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val filteredDevices =
        remember(state.userDevices, userSearchQuery) {
            val normalized = userSearchQuery.trim().lowercase()
            if (normalized.isBlank()) {
                state.userDevices
            } else {
                state.userDevices.filter { device ->
                    device.name.lowercase().contains(normalized) ||
                        device.lastSeenLabel.lowercase().contains(normalized) ||
                        device.status.label.lowercase().contains(normalized)
                }
            }
        }

    Column(
        modifier = Modifier.fillMaxSize().background(GloshColors.Bone),
    ) {
        UsersHeader(
            entryMode = entryMode,
            searchQuery = userSearchQuery,
            searchExpanded = searchExpanded,
            totalCount = state.userDevices.size,
            refreshStatus = deviceRefreshStatusText(state),
            refreshStatusIsError = state.devicesRefreshError != null,
            onSearchChanged = { userSearchQuery = it },
            onSearchExpandedChanged = { expanded ->
                searchExpanded = expanded
                if (!expanded) userSearchQuery = ""
            },
            onCreateUser = { showCreateDialog = true },
            onRefresh = onRefreshDevices,
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = GloshSpacing.PageHorizontal,
                top = 4.dp,
                end = GloshSpacing.PageHorizontal,
                bottom = 24.dp,
            ),
        ) {
            if (filteredDevices.isEmpty()) {
                item {
                    EmptyUsersState(
                        text =
                            if (state.userDevices.isEmpty()) {
                                "Todavía no hay usuarios vinculados."
                            } else {
                                "No encontramos usuarios con esa búsqueda."
                            },
                        showCreate = entryMode == RulesEntryMode.ManageUsers && state.userDevices.isEmpty(),
                        onCreate = { showCreateDialog = true },
                    )
                }
            } else {
                item {
                    Text(
                        "Usuarios",
                        style = MaterialTheme.typography.labelLarge,
                        color = GloshColors.Muted,
                    )
                }
                items(filteredDevices, key = { it.id }) { device ->
                    ProtectedUserCard(device = device, onClick = { onDeviceSelected(device.id) })
                }
            }
            if (entryMode == RulesEntryMode.ManageUsers) {
                item(key = "archived-users") {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onShowArchivedUsers) {
                        Text("Usuarios archivados")
                    }
                }
            }
        }
    }

    if (showCreateDialog && entryMode == RulesEntryMode.ManageUsers) {
        NewUserDialog(
            state = state,
            onDismiss = { showCreateDialog = false },
            onPairingUserNameChanged = onPairingUserNameChanged,
            onGeneratePairingCode = onGeneratePairingCode,
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(state.pairingCode))
                onPairingCodeCopied()
            },
        )
    }
}

@Composable
private fun UsersHeader(
    entryMode: RulesEntryMode,
    searchQuery: String,
    searchExpanded: Boolean,
    totalCount: Int,
    refreshStatus: String,
    refreshStatusIsError: Boolean,
    onSearchChanged: (String) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onCreateUser: () -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val title =
        when (entryMode) {
            RulesEntryMode.ManageUsers -> "Usuarios"
            RulesEntryMode.Web -> "Internet"
            RulesEntryMode.Apps -> "Apps"
        }
    val subtitle =
        when (entryMode) {
            RulesEntryMode.ManageUsers -> "$totalCount vinculados"
            RulesEntryMode.Web -> "Elegí un usuario para configurar su Internet"
            RulesEntryMode.Apps -> "Elegí un usuario para configurar sus apps"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(
                    start = if (onBack == null) GloshSpacing.PageHorizontal else 8.dp,
                    top = 16.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 18.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    ProductGlyph(ProductIcon.Back, GloshColors.Graphite, contentDescription = "Volver")
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = GloshColors.Graphite)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar usuario") },
                    singleLine = true,
                    leadingIcon = {
                        ProductGlyph(ProductIcon.Search, GloshColors.Muted, Modifier.size(22.dp))
                    },
                    shape = GloshShapes.Card,
                )
            } else {
                Text(
                    text = refreshStatus,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (refreshStatusIsError) GloshColors.Danger else GloshColors.Muted,
                    maxLines = 2,
                )
            }
            HeaderIconButton(onClick = onRefresh) {
                ProductGlyph(
                    ProductIcon.Refresh,
                    GloshColors.Muted,
                    Modifier.size(22.dp).semantics { contentDescription = "Actualizar usuarios" },
                )
            }
            HeaderIconButton(onClick = { onSearchExpandedChanged(!searchExpanded) }) {
                ProductGlyph(
                    ProductIcon.Search,
                    GloshColors.Graphite,
                    Modifier.size(22.dp).semantics { contentDescription = "Buscar usuario" },
                )
            }
            if (entryMode == RulesEntryMode.ManageUsers) {
                UserCreateButton(onClick = onCreateUser)
            }
        }
    }
}

@Composable
private fun EmptyUsersState(
    text: String,
    showCreate: Boolean,
    onCreate: () -> Unit,
) {
    GloshSurfaceCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(ProductIcon.People)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Sin usuarios", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(text, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
        }
        if (showCreate) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onCreate) { Text("Agregar usuario") }
        }
    }
}

@Composable
private fun NewUserDialog(
    state: RulesUiState,
    onDismiss: () -> Unit,
    onPairingUserNameChanged: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onCopyToken: () -> Unit,
) {
    val hasToken = state.pairingCode.isNotBlank()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(hasToken) {
        if (!hasToken) focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasToken) "Token listo" else "Agregar usuario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (hasToken) {
                        "Compartí este token para vincular el teléfono del usuario."
                    } else {
                        "Poné un nombre y generá un token temporal para vincular su teléfono."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Muted,
                )
                if (hasToken) {
                    TokenReadyCard(code = state.pairingCode, expiresAt = state.pairingExpiresAt, onCopy = onCopyToken)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            modifier = Modifier.size(52.dp).semantics { contentDescription = "Compartir token por WhatsApp" },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                val message =
                                    "Código para activar Glosh Usuario:\n${state.pairingCode}\n" +
                                        "Usalo antes de ${state.pairingExpiresAt}."
                                val uri = Uri.parse("https://wa.me/?text=${Uri.encode(message)}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        ) {
                            Text("WA")
                        }
                    }
                } else {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        value = state.pairingUserName,
                        onValueChange = onPairingUserNameChanged,
                        label = { Text("Nombre del usuario") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (!hasToken) {
                ProgressActionButton(
                    onClick = onGeneratePairingCode,
                    enabled = !state.pairingLoading,
                    loading = state.pairingLoading,
                    loadingText = "Generando…",
                    successText = "Token listo",
                    text = "Generar token",
                    modifier = Modifier,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
internal fun TokenReadyCard(
    code: String,
    expiresAt: String,
    onCopy: () -> Unit,
) {
    GloshSurfaceCard {
        StatusChip("Listo", GloshColors.Positive)
        Text(code, style = MaterialTheme.typography.headlineMedium, color = GloshColors.Graphite)
        if (expiresAt.isNotBlank()) {
            Text("Vence: $expiresAt", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onCopy) { Text("Copiar token") }
    }
}

@Composable
private fun ProtectedUserCard(
    device: UserDeviceUiState,
    onClick: () -> Unit,
) {
    val healthy = device.status == UserDeviceStatus.Active && device.protectionComplete
    val attentionLevel = device.securityAttentionLevel()
    ProductListSurface {
        ProductListRow(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            leading = {
                GloshIconBubble(
                    icon = if (healthy) ProductIcon.Person else ProductIcon.ShieldAlert,
                    accent = if (healthy) GloshColors.Lime else attentionLevel.color,
                )
            },
            headline = {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            },
            supporting = {
                Text(
                    text = device.listSummary(healthy),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (attentionLevel == SecurityAttentionLevel.Critical) GloshColors.Danger else GloshColors.Muted,
                    maxLines = 2,
                )
            },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecurityAttentionGlyph(level = attentionLevel)
                    ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(22.dp))
                }
            },
            showDivider = false,
        )
    }
}

@Composable
internal fun SecurityAttentionGlyph(
    level: SecurityAttentionLevel,
    modifier: Modifier = Modifier,
) {
    if (level == SecurityAttentionLevel.None) return
    val description =
        when (level) {
            SecurityAttentionLevel.Critical -> "Error de seguridad"
            SecurityAttentionLevel.Warning -> "Seguridad pendiente de verificar"
            SecurityAttentionLevel.None -> return
        }
    ProductGlyph(
        icon = ProductIcon.ShieldAlert,
        color = level.color,
        modifier = modifier.size(18.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun UserCreateButton(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Agregar usuario" },
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Lime),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            ProductGlyph(ProductIcon.UserPlus, GloshColors.Graphite, Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) { content() }
    }
}

private fun UserDeviceUiState.listSummary(healthy: Boolean): String =
    if (healthy) {
        "$lastSeenLabel · $appCount apps"
    } else {
        protectionAlert
            ?: when (status) {
                UserDeviceStatus.Unprotected -> "La protección requiere atención"
                UserDeviceStatus.Inactive -> "Sin comunicación reciente"
                UserDeviceStatus.Unknown -> "Verificando configuración"
                UserDeviceStatus.Active -> "Falta completar la protección"
            }
    }

@Composable
private fun deviceRefreshStatusText(state: RulesUiState): String {
    var nowEpochMillis by remember(state.devicesLastRefreshedAtEpochMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(state.devicesLastRefreshedAtEpochMillis) {
        if (state.devicesLastRefreshedAtEpochMillis != null) {
            while (true) {
                delay(60_000)
                nowEpochMillis = System.currentTimeMillis()
            }
        }
    }
    return state.deviceRefreshStatusText(nowEpochMillis)
}

internal fun RulesUiState.deviceRefreshStatusText(nowEpochMillis: Long): String =
    when {
        devicesRefreshing -> "Actualizando…"
        devicesRefreshError != null -> "No se pudo actualizar"
        devicesLastRefreshedAtEpochMillis != null -> {
            val elapsedMinutes = ((nowEpochMillis - devicesLastRefreshedAtEpochMillis).coerceAtLeast(0L) / 60_000L)
            if (elapsedMinutes == 0L) "Actualizado ahora" else "Actualizado hace $elapsedMinutes min"
        }
        offlineMode -> "Datos guardados"
        else -> "Listo para actualizar"
    }

private val UserDeviceStatus.label: String
    get() =
        when (this) {
            UserDeviceStatus.Active -> "Protegido"
            UserDeviceStatus.Unprotected -> "Requiere atención"
            UserDeviceStatus.Inactive -> "Sin conexión"
            UserDeviceStatus.Unknown -> "Verificando"
        }

internal enum class SecurityAttentionLevel {
    None,
    Warning,
    Critical,
}

internal fun UserDeviceUiState.securityAttentionLevel(): SecurityAttentionLevel =
    when {
        possibleUninstall || confirmedProtectionFailure -> SecurityAttentionLevel.Critical
        protectionVerificationPending || status == UserDeviceStatus.Inactive || status == UserDeviceStatus.Unknown -> SecurityAttentionLevel.Warning
        else -> SecurityAttentionLevel.None
    }

internal val SecurityAttentionLevel.color: Color
    get() =
        when (this) {
            SecurityAttentionLevel.Critical -> GloshColors.Danger
            SecurityAttentionLevel.Warning -> GloshColors.Warning
            SecurityAttentionLevel.None -> Color.Transparent
        }

internal fun UserDeviceUiState.detailAttentionSummary(): String =
    protectionAlert
        ?: when (status) {
            UserDeviceStatus.Unprotected -> "Hay componentes de protección que requieren atención"
            UserDeviceStatus.Inactive -> "No hay comunicación reciente con el teléfono"
            UserDeviceStatus.Unknown -> "Todavía falta verificar la configuración"
            UserDeviceStatus.Active -> "Falta completar la configuración de protección"
        }

internal val AdminSurface = GloshColors.Bone
internal val HeaderInk = GloshColors.Graphite
internal val HeaderMuted = GloshColors.Muted
internal val ActiveGreen = GloshColors.Positive
internal val CriticalRed = GloshColors.Danger
internal val PendingYellow = GloshColors.Warning
