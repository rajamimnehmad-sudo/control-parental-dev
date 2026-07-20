package com.contentfilter.admin.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.contentfilter.core.ui.ProductCard
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
    ProductCard {
        Text("Estado de conexión", style = MaterialTheme.typography.titleMedium)
        Text("Última conexión: ${device.lastSeenLabel}", style = MaterialTheme.typography.bodyMedium)
        Text("VPN: ${device.vpnState}", style = MaterialTheme.typography.bodyMedium)
        Text("Accesibilidad: ${device.accessibilityState}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Protección contra desinstalación: ${device.deviceAdminState}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Barrera reforzada", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (control?.armed == true) "Armada" else "Pendiente",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusChip(
                if (control?.armed == true) "Obligatoria" else "Requiere activación",
                if (control?.armed == true) ActiveGreen else MaterialTheme.colorScheme.error,
            )
        }
        if (control == null) {
            Text("El control se creará al activar la barrera.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Aplicación: revisión ${control.appliedRevision} de ${control.commandRevision}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (control?.armed != true) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onArmProtection) {
                Text("Activar protección obligatoria")
            }
        }
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Más opciones", style = MaterialTheme.typography.titleMedium, color = HeaderInk)
                    Text(
                        "Reenlace, desinstalación temporal, código de emergencia y archivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = HeaderMuted,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Cerrar más opciones" else "Abrir más opciones",
                    tint = HeaderMuted,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductCard {
                    Text("Desinstalación temporal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "La autorización vence automáticamente a los 30 minutos.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = control.authorizationStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = HeaderMuted,
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
                    Text("Archivar usuario", color = MaterialTheme.colorScheme.error)
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
                    loadingText = "Archivando...",
                    successText = "Archivado",
                    tone = ActionButtonTone.Destructive,
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmArchive = false }) {
                    Text("Cancelar")
                }
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
        Text("Volver a enlazar", style = MaterialTheme.typography.titleMedium)
        Text(
            "Genera un token de un solo uso por 30 minutos. El vínculo anterior sigue activo hasta que el nuevo teléfono sincronice correctamente.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.relinkCode.isBlank() || state.relinkDeviceId != device.id) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = device.id !in state.relinkLoadingDeviceIds,
                onClick = onGenerateRelinkCode,
            ) {
                Text(
                    if (device.id in state.relinkLoadingDeviceIds) {
                        "Generando token..."
                    } else {
                        "Generar token de reenlace"
                    },
                )
            }
        } else {
            Text(state.relinkCode, style = MaterialTheme.typography.headlineSmall)
            Text("Vence: ${state.relinkExpiresAt}", style = MaterialTheme.typography.bodySmall)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(state.relinkCode))
                    onRelinkCodeCopied()
                },
            ) {
                Text("Copiar y ocultar")
            }
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
        Text("Recuperación sin conexión", style = MaterialTheme.typography.titleMedium)
        Text(
            "Genera un código de un solo uso. En DEV sólo se guarda su verificador.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val recoveryCode = state.recoveryCodeFor(deviceId)
        if (recoveryCode.isBlank()) {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onGenerateRecoveryCode) {
                Text("Generar código de recuperación")
            }
        } else {
            Text(recoveryCode, style = MaterialTheme.typography.headlineSmall)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(recoveryCode))
                    onRecoveryCodeCopied()
                },
            ) {
                Text("Copiar y ocultar")
            }
        }
    }
}

private fun DeviceProtectionControl?.authorizationStatusLabel(
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val control = this ?: return "Sin permisos temporales activos."
    val expiresAt = control.authorizationExpiresAtEpochMillis ?: return "Sin permisos temporales activos."
    val remainingMillis = expiresAt - nowEpochMillis
    if (remainingMillis <= 0) return "Los permisos temporales vencieron; la protección se reactivó automáticamente."
    val remainingMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
    return when (control.authorizationScope) {
        ProtectionAuthorizationScope.Settings -> "Mantenimiento habilitado · quedan $remainingMinutes min."
        ProtectionAuthorizationScope.Removal -> "Desinstalación habilitada · quedan $remainingMinutes min."
        ProtectionAuthorizationScope.None -> "Sin permisos temporales activos."
    }
}
