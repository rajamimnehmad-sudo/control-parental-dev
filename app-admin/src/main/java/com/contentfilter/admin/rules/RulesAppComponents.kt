package com.contentfilter.admin.rules

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contentfilter.core.ui.StatusChip

@Composable
internal fun AppControlCard(
    app: AppControlUiState,
    scheduleConfigured: Boolean,
    onAllowedChanged: (Boolean) -> Unit,
    onLimitSaved: (String) -> Unit,
    onScheduleClick: () -> Unit,
) {
    var showLimitDialog by remember(app.packageName) { mutableStateOf(false) }
    var minutes by remember(app.packageName, app.dailyLimitMinutes) {
        mutableStateOf("")
    }
    LaunchedEffect(app.dailyLimitMinutes) {
        if (!showLimitDialog) minutes = ""
    }
    val status = app.status()
    val limitText =
        app.extraTimeRemainingMinutes?.let { "Tiempo extra: restan $it min" }
            ?: app.dailyLimitMinutes?.let { "Límite: $it min/día" }
            ?: "Sin límite"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.appName, app.iconBase64, size = 34.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        StatusChip(
                            text = if (app.isUpdating) "Guardando..." else status.label,
                            color = status.switchColor,
                        )
                    }
                    Text(
                        limitText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ),
                    enabled = !app.isUpdating,
                    onClick = {
                        minutes = app.dailyLimitMinutes?.toString().orEmpty()
                        showLimitDialog = true
                    },
                ) {
                    ClockIcon(
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Switch(
                    checked = app.allowed,
                    enabled = !app.isUpdating,
                    onCheckedChange = onAllowedChanged,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = status.switchColor,
                            checkedTrackColor = status.switchColor.copy(alpha = 0.45f),
                            uncheckedThumbColor = status.switchColor,
                            uncheckedTrackColor = status.switchColor.copy(alpha = 0.30f),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.groupLabel?.let { label ->
                    StatusChip(
                        text = label,
                        color = status.switchColor,
                    )
                }
                TextButton(onClick = onScheduleClick) {
                    Text(if (scheduleConfigured) "Horario activo" else "Agregar horario")
                }
            }
        }
    }
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Límite diario") },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutos diarios") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLimitSaved(minutes)
                        minutes = ""
                        showLimitDialog = false
                    },
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ClockIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        drawCircle(
            color = tint,
            radius = size.minDimension / 2f - strokeWidth / 2f,
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = tint,
            start = center,
            end = center.copy(y = center.y - size.minDimension * 0.24f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = center,
            end = center.copy(x = center.x + size.minDimension * 0.20f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private data class AppControlStatus(
    val label: String,
    val switchColor: Color,
)

private fun AppControlUiState.status(): AppControlStatus =
    when {
        extraTimeRemainingMinutes != null -> AppControlStatus("Extra ${extraTimeRemainingMinutes}m", WarningYellow)
        dailyLimitMinutes != null -> AppControlStatus("Con limite", WarningYellow)
        allowed -> AppControlStatus("Permitida", AllowedGreen)
        else -> AppControlStatus("Bloqueada", BlockedRed)
    }

private val AppControlUiState.groupLabel: String?
    get() =
        groupName?.let { name ->
            groupLimitMinutes?.let { minutes -> "$name · $minutes min compartidos" } ?: name
        }

private val AllowedGreen = Color(0xFF2E7D32)
private val BlockedRed = Color(0xFFC62828)
private val WarningYellow = Color(0xFFF9A825)

@Composable
internal fun AppIcon(
    name: String,
    iconBase64: String?,
    size: Dp = 42.dp,
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
                    .size(size)
                    .clip(CircleShape),
        )
    } else {
        FallbackAppIcon(name, size)
    }
}

@Composable
private fun FallbackAppIcon(
    name: String,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
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
