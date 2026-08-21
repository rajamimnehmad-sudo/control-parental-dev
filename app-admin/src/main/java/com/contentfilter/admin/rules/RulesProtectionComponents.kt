package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.ui.ActionButtonTone
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshIconBubble
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProgressActionButton
import com.contentfilter.core.ui.StatusChip

@Composable
internal fun ProtectionPanel(
    state: RulesUiState,
    device: UserDeviceUiState,
    onArmProtection: () -> Unit,
) {
    val control = state.protectionControls[device.id]
    val loading = device.id in state.protectionLoadingDeviceIds
    val attention = device.securityAttentionLevel()

    if (device.possibleUninstall) {
        ProductCard {
            StatusChip("Atención urgente", GloshColors.Danger)
            Text("La app de Glosh podría haberse quitado", style = MaterialTheme.typography.titleLarge, color = GloshColors.Graphite)
            Text(
                "El teléfono dejó de reportar después de quedar sin protección contra desinstalación.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Muted,
            )
            Text("Qué hacer", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text("1. Revisá si Glosh Usuario sigue instalado en el teléfono.", style = MaterialTheme.typography.bodyMedium)
            Text("2. Si falta, reinstalá la APK oficial.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Abrí Más opciones → Volver a enlazar y generá un token nuevo.", style = MaterialTheme.typography.bodyMedium)
            Text("4. En el teléfono, completá nuevamente los pasos de protección.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GloshIconBubble(
                icon = if (attention == SecurityAttentionLevel.None) ProductIcon.ShieldCheck else ProductIcon.ShieldAlert,
                accent = if (attention == SecurityAttentionLevel.None) GloshColors.Positive else attention.color,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (attention == SecurityAttentionLevel.None) "Protección completa" else "Protección por revisar",
                    style = MaterialTheme.typography.titleLarge,
                    color = GloshColors.Graphite,
                )
                Text("Última conexión: ${device.lastSeenLabel}", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
            StatusChip(
                if (attention == SecurityAttentionLevel.None) "Correcto" else "Atención",
                if (attention == SecurityAttentionLevel.None) GloshColors.Positive else attention.color,
            )
        }

        ProtectionComponentLine(
            label = "Internet protegido",
            active = device.vpnState == ActiveStateLabel,
        )
        ProtectionComponentLine(
            label = "Bloqueo de apps activo",
            active = device.accessibilityState == ActiveStateLabel,
        )
        ProtectionComponentLine(
            label = "Protección contra desinstalación",
            active = device.deviceAdminState == ActiveStateLabel,
        )
    }

    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Protección reforzada", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(
                    if (control?.armed == true) {
                        "Activa y obligatoria en este usuario."
                    } else {
                        "Todavía falta activarla."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = GloshColors.Muted,
                )
            }
            StatusChip(
                if (control?.armed == true) "Activa" else "Pendiente",
                if (control?.armed == true) GloshColors.Positive else GloshColors.Warning,
            )
        }
        if (control?.armed != true) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onArmProtection) {
                Text("Completar protección")
            }
        }
    }
}

@Composable
private fun ProtectionComponentLine(
    label: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductGlyph(
            icon = if (active) ProductIcon.ShieldCheck else ProductIcon.ShieldAlert,
            color = if (active) GloshColors.Positive else GloshColors.Warning,
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = GloshColors.Graphite,
        )
        Text(
            if (active) "Activo" else "Revisar",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) GloshColors.Positive else GloshColors.Warning,
        )
    }
}

@Composable
internal fun AdvancedUserOptions(
    state: RulesUiState,
    device: UserDeviceUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onAuthorizeRemoval: () -> Unit,
    onGenerateRecoveryCode: () -> Unit,
    onRecoveryCodeCopied: () -> Unit,
    onGenerateRelinkCode: () -> Unit,
    onRelinkCodeCopied: () -> Unit,
    onArchiveUser: () -> Unit,
) {
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    var confirmArchive by rememberSaveable(device.id) { mutableStateOf(false) }
    val control = state.protectionControls[device.id]
    val loading = device.id in state.protectionLoadingDeviceIds
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProductCard(onClick = { expanded = !expanded }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GloshIconBubble(ProductIcon.Settings)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Más opciones", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                    Text(
                        "Reenlace, desinstalación temporal, recuperación y archivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = GloshColors.Muted,
                    )
                }
                ProductGlyph(
                    icon = ProductIcon.ChevronRight,
                    color = GloshColors.Muted,
                    contentDescription = if (expanded) "Cerrar más opciones" else "Abrir más opciones",
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductCard {
                    Text("Desinstalación temporal", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                    Text(
                        "Autoriza la desinstalación durante 30 minutos y después vuelve a protegerse automáticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GloshColors.Muted,
                    )
                    Text(
                        text = control.authorizationStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = GloshColors.Muted,
                    )
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onAuthorizeRemoval) {
                        Text("Permitir desinstalación")
                    }
                }
                RelinkOptionCard(
                    state = state,
                    device = device,
                    clipboardManager = clipboardManager,
                    onGenerateRelinkCode = onGenerateRelinkCode,
                    onRelinkCodeCopied = onRelinkCodeCopied,
                )
                RecoveryOptionCard(
                    state = state,
                    deviceId = device.id,
                    loading = loading,
                    clipboardManager = clipboardManager,
                    onGenerateRecoveryCode = onGenerateRecoveryCode,
                    onRecoveryCodeCopied = onRecoveryCodeCopied,
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = device.id !in state.pendingDeviceDeleteIds,
                    onClick = { confirmArchive = true },
                ) {
                    Text("Archivar usuario", color = GloshColors.Danger)
                }
            }
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archivar usuario") },
            text = {
                Text(
                    "El usuario perderá acceso y saldrá de la lista activa. Su configuración se conservará para restaurarlo después.",
                )
            },
            confirmButton = {
                ProgressActionButton(
                    onClick = {
                        confirmArchive = false
                        onArchiveUser()
                    },
                    enabled = device.id !in state.pendingDeviceDeleteIds,
                    modifier = Modifier,
                    text = "Archivar usuario",
                    loadingText = "Archivando…",
                    successText = "Archivado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmArchive = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun RelinkOptionCard(
    state: RulesUiState,
    device: UserDeviceUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onGenerateRelinkCode: () -> Unit,
    onRelinkCodeCopied: () -> Unit,
) {
    ProductCard {
        Text("Volver a enlazar", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        Text(
            "Generá un token temporal para volver a vincular este mismo usuario sin crear uno nuevo.",
            style = MaterialTheme.typography.bodyMedium,
            color = GloshColors.Muted,
        )
        if (state.relinkCode.isBlank() || state.relinkDeviceId != device.id) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = device.id !in state.relinkLoadingDeviceIds,
                onClick = onGenerateRelinkCode,
            ) {
                Text(if (device.id in state.relinkLoadingDeviceIds) "Generando…" else "Generar token")
            }
        } else {
            Text(state.relinkCode, style = MaterialTheme.typography.headlineSmall)
            Text("Vence: ${state.relinkExpiresAt}", style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(state.relinkCode))
                    onRelinkCodeCopied()
                },
            ) { Text("Copiar token") }
        }
    }
}

@Composable
private fun RecoveryOptionCard(
    state: RulesUiState,
    deviceId: String,
    loading: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onGenerateRecoveryCode: () -> Unit,
    onRecoveryCodeCopied: () -> Unit,
) {
    ProductCard {
        Text("Recuperación sin conexión", style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
        Text(
            "Prepará códigos de un solo uso para poder recuperar el teléfono incluso cuando no tenga Internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = GloshColors.Muted,
        )
        val remaining = state.recoveryKitRemainingByDevice[deviceId] ?: 0
        val remoteKitPrepared = state.protectionControls[deviceId]?.recoveryKit?.isNotEmpty() == true
        Text(
            when {
                remaining > 0 -> "$remaining códigos disponibles"
                remoteKitPrepared -> "Kit activo, sin códigos disponibles en este Admin"
                else -> "Kit no preparado"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (remaining > 0) GloshColors.Positive else GloshColors.Muted,
        )
        val recoveryCode = state.recoveryCodeFor(deviceId)
        if (recoveryCode.isBlank()) {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onGenerateRecoveryCode) {
                Text(
                    when {
                        remaining > 0 -> "Mostrar próximo código"
                        remoteKitPrepared -> "Renovar kit"
                        else -> "Preparar recuperación"
                    },
                )
            }
        } else {
            Text(recoveryCode, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Compartilo solamente con el usuario correcto. Es de un solo uso.",
                style = MaterialTheme.typography.bodySmall,
                color = GloshColors.Muted,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(recoveryCode))
                    onRecoveryCodeCopied()
                },
            ) { Text("Copiar código") }
        }
    }
}

private fun DeviceProtectionControl?.authorizationStatusLabel(
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val control = this ?: return "Sin permisos temporales activos."
    val expiresAt = control.authorizationExpiresAtEpochMillis ?: return "Sin permisos temporales activos."
    val remainingMillis = expiresAt - nowEpochMillis
    if (remainingMillis <= 0) return "El permiso temporal ya venció."
    val remainingMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
    return when (control.authorizationScope) {
        ProtectionAuthorizationScope.Settings -> "Mantenimiento habilitado · quedan $remainingMinutes min."
        ProtectionAuthorizationScope.Removal -> "Desinstalación habilitada · quedan $remainingMinutes min."
        ProtectionAuthorizationScope.None -> "Sin permisos temporales activos."
    }
}

private const val ActiveStateLabel = "Activa"
