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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.contentfilter.core.ui.GloshColors
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
    var minutes by remember(app.packageName, app.dailyLimitMinutes) { mutableStateOf("") }
    LaunchedEffect(app.dailyLimitMinutes) {
        if (!showLimitDialog) minutes = ""
    }
    val status = app.status()
    val limitText =
        app.extraTimeRemainingMinutes?.let { "Tiempo extra: quedan $it min" }
            ?: app.dailyLimitMinutes?.let { "Límite: $it min por día" }
            ?: "Sin límite"

    Column(modifier = Modifier.fillMaxWidth().background(GloshColors.Surface)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.appName, app.iconBase64, size = 36.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            color = GloshColors.Graphite,
                            maxLines = 1,
                        )
                        StatusChip(
                            text =
                                when {
                                    app.isUpdating -> "Guardando…"
                                    app.isPendingApproval -> "Pendiente"
                                    else -> status.label
                                },
                            color = status.color,
                        )
                    }
                    Text(limitText, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
                }
                IconButton(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .border(1.dp, GloshColors.Line, CircleShape),
                    enabled = !app.isUpdating,
                    onClick = {
                        minutes = app.dailyLimitMinutes?.toString().orEmpty()
                        showLimitDialog = true
                    },
                ) {
                    ClockIcon(tint = GloshColors.Graphite, modifier = Modifier.size(20.dp))
                }
                GloshV4Switch(
                    checked = app.allowed,
                    enabled = !app.isUpdating,
                    onCheckedChange = onAllowedChanged,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.groupLabel?.let { label -> StatusChip(text = label, color = GloshColors.Warning) }
                TextButton(onClick = onScheduleClick) {
                    Text(if (scheduleConfigured) "Editar horario" else "Agregar horario")
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = GloshColors.Line)
    }

    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Límite diario") },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutos por día") },
                    supportingText = { Text("Dejalo vacío para quitar el límite.") },
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
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Cancelar") }
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

private data class AppControlStatus(
    val label: String,
    val color: Color,
)

private fun AppControlUiState.status(): AppControlStatus =
    when {
        extraTimeRemainingMinutes != null -> AppControlStatus("Extra ${extraTimeRemainingMinutes}m", GloshColors.Warning)
        dailyLimitMinutes != null -> AppControlStatus("Con límite", GloshColors.Warning)
        allowed -> AppControlStatus("Permitida", GloshColors.Positive)
        else -> AppControlStatus("Bloqueada", GloshColors.Danger)
    }

private val AppControlUiState.groupLabel: String?
    get() = groupName?.let { name -> groupLimitMinutes?.let { "$name · $it min compartidos" } ?: name }

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
            modifier = Modifier.size(size).clip(CircleShape),
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
        modifier = Modifier.size(size).clip(CircleShape).background(GloshColors.LimeSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = GloshColors.Graphite,
        )
    }
}
