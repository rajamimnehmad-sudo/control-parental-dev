package com.contentfilter.admin.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isScheduleRule
import com.contentfilter.core.domain.model.PolicyWeekdays
import com.contentfilter.core.ui.ProductCard

@Composable
internal fun GlobalScheduleButton(
    title: String,
    rules: List<PolicyRule>,
    saving: Boolean = false,
    onSave: (List<AllowedScheduleWindowInput>) -> Unit,
) {
    val scheduleFingerprint =
        remember(rules) {
            rules
                .filter { it.isScheduleRule() }
                .joinToString("|") { "${it.id}:${it.activeWindow}:${it.activeDaysMask}:${it.enabled}" }
        }
    var visible by rememberSaveable(scheduleFingerprint) { mutableStateOf(false) }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !saving,
        onClick = { visible = true },
    ) {
        Text("Configurar horarios")
    }
    if (visible) {
        Dialog(
            onDismissRequest = { if (!saving) visible = false },
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF2F8F7),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        IconButton(
                            enabled = !saving,
                            onClick = { visible = false },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
                    ) {
                        item {
                            AllowedScheduleEditor(
                                title = "Franjas permitidas",
                                rules = rules,
                                saving = saving,
                                forceExpanded = true,
                                onSave = onSave,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AllowedScheduleEditor(
    title: String,
    rules: List<PolicyRule>,
    saving: Boolean = false,
    forceExpanded: Boolean = false,
    onSave: (List<AllowedScheduleWindowInput>) -> Unit,
) {
    val scheduleRules =
        remember(rules) { rules.filter { it.isScheduleRule() }.sortedBy(PolicyRule::id) }
    val rulesFingerprint =
        scheduleRules.joinToString("|") { rule ->
            "${rule.id}:${rule.activeWindow}:${rule.activeDaysMask}:${rule.enabled}"
        }
    var expanded by rememberSaveable(rulesFingerprint) {
        mutableStateOf(forceExpanded || scheduleRules.isNotEmpty())
    }
    var drafts by rememberSaveable(rulesFingerprint, stateSaver = ScheduleDraftsSaver) {
        mutableStateOf(
            scheduleRules.map { rule ->
                ScheduleWindowDraft(
                    id = rule.id,
                    start = rule.activeWindow?.startMinuteOfDay.toScheduleTime(),
                    end = rule.activeWindow?.endMinuteOfDay.toScheduleTime(),
                    activeDaysMask = rule.activeDaysMask,
                )
            },
        )
    }
    ProductCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (scheduleRules.isEmpty()) {
                "Sin horario: permitido todo el día."
            } else {
                "${scheduleRules.size} franja(s) permitida(s). Fuera de ellas se bloquea."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Hora oficial de Argentina · si también alcanza el límite diario, se aplica el bloqueo más restrictivo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!expanded) {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) {
                Text("Configurar horario")
            }
            return@ProductCard
        }
        drafts.forEachIndexed { index, draft ->
            ScheduleWindowRow(
                index = index,
                draft = draft,
                onChanged = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } },
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            onClick = { drafts = drafts + ScheduleWindowDraft() },
        ) {
            Text("Agregar franja")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && drafts.all(ScheduleWindowDraft::isValid),
            onClick = { onSave(drafts.map(ScheduleWindowDraft::toInput)) },
        ) {
            Text(
                when {
                    saving -> "Guardando horario..."
                    drafts.isEmpty() -> "Quitar horario"
                    else -> "Guardar horario"
                },
            )
        }
    }
}

@Composable
private fun ScheduleWindowRow(
    index: Int,
    draft: ScheduleWindowDraft,
    onChanged: (ScheduleWindowDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Franja ${index + 1}", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onRemove) { Text("Quitar") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draft.start,
                onValueChange = { onChanged(draft.copy(start = it.take(MaxTimeLength))) },
                label = { Text("Desde") },
                supportingText = { Text("HH:mm") },
                singleLine = true,
                isError = parseScheduleMinute(draft.start) == null,
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draft.end,
                onValueChange = { onChanged(draft.copy(end = it.take(MaxTimeLength))) },
                label = { Text("Hasta") },
                supportingText = { Text("HH:mm · bloquea desde esa hora") },
                singleLine = true,
                isError = parseScheduleMinute(draft.end) == null,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Weekdays.forEach { day ->
                val bit = PolicyWeekdays.bit(day.isoDayOfWeek)
                FilterChip(
                    selected = draft.activeDaysMask and bit != 0,
                    onClick = { onChanged(draft.copy(activeDaysMask = draft.activeDaysMask xor bit)) },
                    label = { Text(day.label) },
                )
            }
        }
        TextButton(onClick = { onChanged(draft.copy(activeDaysMask = PolicyWeekdays.All)) }) {
            Text("Copiar a todos los días")
        }
    }
}

internal fun parseScheduleMinute(value: String): Int? {
    val match = ScheduleTimeRegex.matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return hour * MinutesPerHour + minute
}

private fun Int?.toScheduleTime(): String {
    val value = this ?: return ""
    return "%02d:%02d".format(value / MinutesPerHour, value % MinutesPerHour)
}

internal data class ScheduleWindowDraft(
    val id: String? = null,
    val start: String = "08:00",
    val end: String = "00:00",
    val activeDaysMask: Int = PolicyWeekdays.All,
) {
    fun isValid(): Boolean =
        parseScheduleMinute(start) != null &&
            parseScheduleMinute(end) != null &&
            activeDaysMask in 1..PolicyWeekdays.All

    fun toInput(): AllowedScheduleWindowInput =
        AllowedScheduleWindowInput(
            id = id,
            startMinuteOfDay = requireNotNull(parseScheduleMinute(start)),
            endMinuteOfDay = requireNotNull(parseScheduleMinute(end)),
            activeDaysMask = activeDaysMask,
        )
}

private val ScheduleDraftsSaver =
    listSaver<List<ScheduleWindowDraft>, String>(
        save = { encodeScheduleDrafts(it) },
        restore = { decodeScheduleDrafts(it) },
    )

internal fun encodeScheduleDrafts(drafts: List<ScheduleWindowDraft>): List<String> =
    drafts.map { draft ->
        listOf(
            draft.id.orEmpty(),
            draft.start,
            draft.end,
            draft.activeDaysMask.toString(),
        ).joinToString("\t")
    }

internal fun decodeScheduleDrafts(saved: List<String>): List<ScheduleWindowDraft> =
    saved.mapNotNull { encoded ->
        val parts = encoded.split('\t')
        if (parts.size != 4) return@mapNotNull null
        ScheduleWindowDraft(
            id = parts[0].ifBlank { null },
            start = parts[1],
            end = parts[2],
            activeDaysMask = parts[3].toIntOrNull() ?: return@mapNotNull null,
        )
    }

private data class WeekdayUi(
    val isoDayOfWeek: Int,
    val label: String,
)

private val Weekdays =
    listOf(
        WeekdayUi(1, "L"),
        WeekdayUi(2, "M"),
        WeekdayUi(3, "X"),
        WeekdayUi(4, "J"),
        WeekdayUi(5, "V"),
        WeekdayUi(6, "S"),
        WeekdayUi(7, "D"),
    )

private val ScheduleTimeRegex = Regex("([01]\\d|2[0-3]):([0-5]\\d)")
private const val MinutesPerHour = 60
private const val MaxTimeLength = 5
