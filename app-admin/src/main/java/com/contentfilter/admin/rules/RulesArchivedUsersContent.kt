package com.contentfilter.admin.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLazyVisualPage
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip
import com.contentfilter.core.ui.PremiumFeedbackBanner as FeedbackBanner

@Composable
internal fun ArchivedUsersContent(
    state: RulesUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (String) -> Unit,
    onTokenCopied: () -> Unit,
    onTokenDismissed: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredUsers =
        remember(state.archivedUsers, searchQuery) {
            val normalized = searchQuery.trim().lowercase()
            if (normalized.isBlank()) {
                state.archivedUsers
            } else {
                state.archivedUsers.filter { user -> user.name.lowercase().contains(normalized) }
            }
        }
    val bannerText = state.message.ifBlank { if (state.offlineMode) "Sin conexión. Mostrando datos guardados." else "" }

    ProductLazyVisualPage(
        title = "Usuarios anteriores",
        subtitle = "${state.archivedUsers.size} archivados · búsqueda por nombre",
        onBack = onBack,
        banner =
            if (bannerText.isNotBlank()) {
                {
                    FeedbackBanner(
                        text = bannerText,
                        isError = state.offlineMode || bannerText.startsWith("No se pudo"),
                    )
                }
            } else {
                null
            },
    ) {
        item(key = "archived-search") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por nombre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item(key = "archived-refresh") {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRefresh,
                enabled = !state.archivedUsersLoading,
            ) {
                Text(if (state.archivedUsersLoading) "Actualizando..." else "Actualizar")
            }
        }
        if (!state.archivedUsersLoading && filteredUsers.isEmpty()) {
            item(key = "archived-empty") {
                EmptySectionText(
                    if (state.archivedUsers.isEmpty()) {
                        "Todavía no hay usuarios archivados desde App Admin."
                    } else {
                        "No hay usuarios anteriores que coincidan con ese nombre."
                    },
                )
            }
        }
        items(filteredUsers, key = { it.archiveId }) { user ->
            ArchivedUserCard(
                user = user,
                restoring = user.archiveId in state.restoreLoadingArchiveIds,
                onRestore = { onRestore(user.archiveId) },
            )
        }
    }

    if (state.restorePairingCode.isNotBlank()) {
        AlertDialog(
            onDismissRequest = onTokenDismissed,
            title = { Text("Restaurar ${state.restorePairingUserName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ingresá este token en App Usuario. La configuración se restaurará recién cuando el token se use.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TokenReadyCard(
                        code = state.restorePairingCode,
                        expiresAt = state.restorePairingExpiresAt,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(state.restorePairingCode))
                            onTokenCopied()
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = onTokenDismissed) {
                    Text("Cerrar")
                }
            },
        )
    }
}

@Composable
private fun ArchivedUserCard(
    user: ArchivedUserUiState,
    restoring: Boolean,
    onRestore: () -> Unit,
) {
    ProductCard {
        Text(user.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Archivado: ${user.archivedAtLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (user.canRestore) {
            Text(
                "Configuración segura disponible.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ProgressActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Restaurar con token nuevo",
                loadingText = "Generando token...",
                successText = "Token listo",
                onClick = onRestore,
                loading = restoring,
                enabled = !restoring,
            )
        } else {
            StatusChip("Revisión necesaria", PendingYellow)
            Text(
                "Este archivo es anterior al respaldo seguro. No se restaurará automáticamente sin una decisión explícita.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
