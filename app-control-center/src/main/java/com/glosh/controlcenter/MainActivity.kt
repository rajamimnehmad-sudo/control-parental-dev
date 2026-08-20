package com.glosh.controlcenter

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TrackerApiUrl =
    "https://api.github.com/repos/rajamimnehmad-sudo/control-parental-dev/contents/docs/AI_TASK_TRACKER.json?ref=build%2Fglosh-control-center-v2"
private const val AutoRefreshMillis = 5 * 60 * 1000L

private enum class AppView(val label: String) {
    Priority("Prioridad"),
    Sections("Secciones"),
    Overview("Vista general"),
}

private enum class TaskState(val wire: String, val label: String) {
    Pending("pending", "Pendiente"),
    InProgress("in_progress", "En progreso"),
    Done("done", "Hecho"),
    Blocked("blocked", "Bloqueado"),
}

private data class TaskItem(
    val id: String,
    val title: String,
    val detail: String,
    val state: TaskState,
    val priority: String,
)

private data class TaskSection(
    val id: String,
    val title: String,
    val context: String,
    val tasks: List<TaskItem>,
)

private data class Tracker(
    val updatedAt: String,
    val sections: List<TaskSection>,
)

private data class RoutedTask(
    val section: TaskSection,
    val task: TaskItem,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControlCenterApp() }
    }
}

@Composable
private fun ControlCenterApp() {
    val fallback = remember { parseTracker(DefaultTrackerJson) }
    var tracker by remember { mutableStateOf(fallback) }
    var currentView by rememberSaveable { mutableStateOf(AppView.Priority) }
    var expandedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var lastSyncLabel by remember { mutableStateOf("datos incluidos") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            runCatching { loadRemoteTracker() }
                .onSuccess {
                    tracker = it
                    lastSyncLabel = "ahora · ${clockNow()}"
                }
                .onFailure {
                    lastSyncLabel = "sin conexión · ${clockNow()}"
                }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(AutoRefreshMillis)
            refresh()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            background = Page,
            surface = Color.White,
            onSurface = Ink,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.fillMaxSize()) {
                Header(
                    tracker = tracker,
                    lastSyncLabel = lastSyncLabel,
                    refreshing = refreshing,
                    onRefresh = ::refresh,
                )
                ViewSelector(
                    current = currentView,
                    onSelect = {
                        currentView = it
                        expandedTaskId = null
                        expandedSectionId = null
                    },
                )
                when (currentView) {
                    AppView.Priority -> PriorityScreen(
                        tracker = tracker,
                        expandedTaskId = expandedTaskId,
                        onTask = { expandedTaskId = if (expandedTaskId == it) null else it },
                    )
                    AppView.Sections -> SectionsScreen(
                        tracker = tracker,
                        expandedSectionId = expandedSectionId,
                        expandedTaskId = expandedTaskId,
                        onSection = { expandedSectionId = if (expandedSectionId == it) null else it },
                        onTask = { expandedTaskId = if (expandedTaskId == it) null else it },
                    )
                    AppView.Overview -> OverviewScreen(
                        tracker = tracker,
                        expandedTaskId = expandedTaskId,
                        onTask = { expandedTaskId = if (expandedTaskId == it) null else it },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    tracker: Tracker,
    lastSyncLabel: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 20.dp, end = 14.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Glosh", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Ruta técnica", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Último cambio · ${tracker.updatedAt}",
                fontSize = 12.sp,
                color = Secondary,
            )
            Text(
                text = "Sincronizado · ${if (refreshing) "actualizando…" else lastSyncLabel}",
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable(enabled = !refreshing, onClick = onRefresh),
            shape = CircleShape,
            color = Page,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("↻", fontSize = 23.sp, color = if (refreshing) Muted else Ink)
            }
        }
    }
}

@Composable
private fun ViewSelector(
    current: AppView,
    onSelect: (AppView) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(Page, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        AppView.entries.forEach { view ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(view) },
                shape = RoundedCornerShape(9.dp),
                color = if (current == view) Ink else Color.Transparent,
            ) {
                Text(
                    text = view.label,
                    modifier = Modifier.padding(vertical = 9.dp),
                    fontSize = 12.sp,
                    fontWeight = if (current == view) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current == view) Color.White else Secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
    HorizontalDivider(color = Divider)
}

@Composable
private fun PriorityScreen(
    tracker: Tracker,
    expandedTaskId: String?,
    onTask: (String) -> Unit,
) {
    val routed = tracker.sections.flatMap { section -> section.tasks.map { RoutedTask(section, it) } }
    val now = routed
        .filter { it.task.state == TaskState.InProgress }
        .sortedWith(compareBy<RoutedTask> { priorityRank(it.task.priority) }.thenBy { it.task.title })
    val next = routed
        .filter { it.task.state == TaskState.Pending || it.task.state == TaskState.Blocked }
        .sortedWith(
            compareBy<RoutedTask> { priorityRank(it.task.priority) }
                .thenBy { if (it.task.state == TaskState.Blocked) 1 else 0 }
                .thenBy { it.task.title },
        )
    val doneCount = routed.count { it.task.state == TaskState.Done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item { ListHeading("Ahora", "Lo que está activo") }
        if (now.isEmpty()) {
            item { EmptyLine("Nada en progreso ahora") }
        } else {
            items(now, key = { it.task.id }) { item ->
                TaskRow(
                    task = item.task,
                    sectionTitle = item.section.title,
                    expanded = expandedTaskId == item.task.id,
                    onClick = { onTask(item.task.id) },
                )
            }
        }

        item { ListHeading("Después", "Siguiente ruta") }
        if (next.isEmpty()) {
            item { EmptyLine("No quedan tareas pendientes") }
        } else {
            items(next, key = { it.task.id }) { item ->
                TaskRow(
                    task = item.task,
                    sectionTitle = item.section.title,
                    expanded = expandedTaskId == item.task.id,
                    onClick = { onTask(item.task.id) },
                )
            }
        }

        if (doneCount > 0) {
            item {
                Text(
                    text = "$doneCount tareas terminadas",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun SectionsScreen(
    tracker: Tracker,
    expandedSectionId: String?,
    expandedTaskId: String?,
    onSection: (String) -> Unit,
    onTask: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp, bottom = 28.dp),
    ) {
        items(tracker.sections, key = { it.id }) { section ->
            val expanded = expandedSectionId == section.id
            SectionRow(section = section, expanded = expanded, onClick = { onSection(section.id) })
            if (expanded) {
                section.tasks
                    .sortedWith(compareBy<TaskItem> { stateRank(it.state) }.thenBy { priorityRank(it.priority) })
                    .forEach { task ->
                        TaskRow(
                            task = task,
                            sectionTitle = null,
                            expanded = expandedTaskId == task.id,
                            inset = true,
                            onClick = { onTask(task.id) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    tracker: Tracker,
    expandedTaskId: String?,
    onTask: (String) -> Unit,
) {
    val all = tracker.sections.flatMap { it.tasks }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item {
            StatusSummary(all)
        }
        tracker.sections.forEach { section ->
            item { ListHeading(section.title, section.context) }
            items(section.tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    sectionTitle = null,
                    expanded = expandedTaskId == task.id,
                    onClick = { onTask(task.id) },
                )
            }
        }
    }
}

@Composable
private fun ListHeading(title: String, context: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (context.isNotBlank()) {
            Text(context, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        fontSize = 13.sp,
        color = Muted,
    )
}

@Composable
private fun SectionRow(
    section: TaskSection,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (section.context.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            section.context,
                            fontSize = 12.sp,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                MiniCounts(section.tasks)
                Spacer(Modifier.width(10.dp))
                Text(if (expanded) "⌃" else "⌄", fontSize = 14.sp, color = Muted)
            }
            HorizontalDivider(color = Divider)
        }
    }
}

@Composable
private fun MiniCounts(tasks: List<TaskItem>) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniCount(tasks.count { it.state == TaskState.Pending }, PendingRed)
        MiniCount(tasks.count { it.state == TaskState.InProgress }, ProgressAmber)
        MiniCount(tasks.count { it.state == TaskState.Done }, DoneGreen)
        if (tasks.any { it.state == TaskState.Blocked }) {
            MiniCount(tasks.count { it.state == TaskState.Blocked }, BlockedGray)
        }
    }
}

@Composable
private fun MiniCount(count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(count.toString(), fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    sectionTitle: String?,
    expanded: Boolean,
    inset: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(
                start = if (inset) 34.dp else 20.dp,
                end = 20.dp,
                top = 13.dp,
                bottom = if (expanded) 14.dp else 13.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(stateColor(task.state), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                    )
                    if (sectionTitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(sectionTitle, fontSize = 11.sp, color = Muted)
                    }
                }
                val priority = priorityLabel(task.priority)
                if (priority != "Normal") {
                    Text(
                        text = priority,
                        fontSize = 10.sp,
                        color = if (priority == "Crítico") PendingRed else ProgressAmber,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (expanded) "⌃" else "›", fontSize = 17.sp, color = Muted)
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (task.detail.isNotBlank()) {
                    Text(
                        text = task.detail,
                        modifier = Modifier.padding(start = 21.dp),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 21.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(task.state.label, fontSize = 11.sp, color = stateColor(task.state))
                    Text(priority, fontSize = 11.sp, color = Muted)
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = if (inset) 34.dp else 20.dp), color = Divider)
}

@Composable
private fun StatusSummary(tasks: List<TaskItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SummaryText("Pendientes", tasks.count { it.state == TaskState.Pending }, PendingRed)
        SummaryText("En progreso", tasks.count { it.state == TaskState.InProgress }, ProgressAmber)
        SummaryText("Hechas", tasks.count { it.state == TaskState.Done }, DoneGreen)
        SummaryText("Bloqueadas", tasks.count { it.state == TaskState.Blocked }, BlockedGray)
    }
}

@Composable
private fun SummaryText(label: String, count: Int, color: Color) {
    Column {
        Text(count.toString(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

private fun stateColor(state: TaskState): Color = when (state) {
    TaskState.Pending -> PendingRed
    TaskState.InProgress -> ProgressAmber
    TaskState.Done -> DoneGreen
    TaskState.Blocked -> BlockedGray
}

private fun stateRank(state: TaskState): Int = when (state) {
    TaskState.InProgress -> 0
    TaskState.Pending -> 1
    TaskState.Blocked -> 2
    TaskState.Done -> 3
}

private fun priorityRank(priority: String): Int = when (priority.lowercase()) {
    "p0", "critical", "critico", "crítico" -> 0
    "p1", "important", "importante" -> 1
    else -> 2
}

private fun priorityLabel(priority: String): String = when (priorityRank(priority)) {
    0 -> "Crítico"
    1 -> "Importante"
    else -> "Normal"
}

private fun clockNow(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private suspend fun loadRemoteTracker(): Tracker = withContext(Dispatchers.IO) {
    val connection = (URL(TrackerApiUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Glosh-Control-Center")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) error("GitHub HTTP $code")
        val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val encoded = envelope.getString("content").replace("\n", "")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        parseTracker(decoded)
    } finally {
        connection.disconnect()
    }
}

private fun parseTracker(raw: String): Tracker {
    val root = JSONObject(raw)
    val sectionsJson = root.getJSONArray("sections")
    val sections = buildList {
        for (sectionIndex in 0 until sectionsJson.length()) {
            val sectionJson = sectionsJson.getJSONObject(sectionIndex)
            val tasksJson = sectionJson.getJSONArray("tasks")
            val tasks = buildList {
                for (taskIndex in 0 until tasksJson.length()) {
                    val taskJson = tasksJson.getJSONObject(taskIndex)
                    add(
                        TaskItem(
                            id = taskJson.getString("id"),
                            title = taskJson.getString("title"),
                            detail = taskJson.optString("detail"),
                            state = TaskState.entries.firstOrNull { it.wire == taskJson.getString("status") }
                                ?: TaskState.Pending,
                            priority = taskJson.optString("priority", "normal"),
                        ),
                    )
                }
            }
            add(
                TaskSection(
                    id = sectionJson.getString("id"),
                    title = sectionJson.getString("title"),
                    context = sectionJson.optString("context"),
                    tasks = tasks,
                ),
            )
        }
    }
    return Tracker(
        updatedAt = root.optString("updatedAt", "sin fecha"),
        sections = sections,
    )
}

private val DefaultTrackerJson = """
{
  "updatedAt": "hoy 23:15",
  "sections": [
    {
      "id": "coordination",
      "title": "Coordinación",
      "context": "Estado local y ruta de trabajo",
      "tasks": [
        {
          "id": "codex-local-inventory",
          "title": "Inventario local completo con Codex",
          "detail": "Reconciliar cambios sin commit, ramas, migraciones y diferencias contra GitHub antes de nuevos tickets.",
          "status": "pending",
          "priority": "critical"
        },
        {
          "id": "control-center-v2",
          "title": "Control Center V2",
          "detail": "Tablero solo lectura, compacto y sincronizado con la ruta técnica.",
          "status": "in_progress",
          "priority": "normal"
        }
      ]
    },
    {
      "id": "chrome-visual",
      "title": "Chrome Visual",
      "context": "Filtrado directo en Chrome",
      "tasks": [
        {
          "id": "chrome-video-a23",
          "title": "Revalidar video en A23",
          "detail": "Confirmar físicamente que no reaparezcan coberturas completas en YouTube después del arreglo de eventos.",
          "status": "pending",
          "priority": "critical"
        },
        {
          "id": "chrome-images",
          "title": "Confirmar fotos estáticas en Chrome",
          "detail": "Verificar imágenes normales, scroll y overlays localizados por separado del video.",
          "status": "pending",
          "priority": "important"
        }
      ]
    },
    {
      "id": "security",
      "title": "Seguridad",
      "context": "Tokens, permisos y hardening",
      "tasks": [
        {
          "id": "spark-review",
          "title": "Revisar cambios acumulados de Spark",
          "detail": "Codex debe reconstruir y validar los cambios candidatos antes de continuar desarrollo nuevo.",
          "status": "pending",
          "priority": "critical"
        },
        {
          "id": "exact-hosts",
          "title": "Allowlist exacta de Supabase",
          "detail": "Cambio preparado y testeado por Spark; pendiente revisión y commit limpio con Codex.",
          "status": "in_progress",
          "priority": "important"
        },
        {
          "id": "atomic-sync",
          "title": "Sync atómico de políticas",
          "detail": "Cambio preparado con tests de last-known-good; pendiente reconciliación local.",
          "status": "in_progress",
          "priority": "important"
        },
        {
          "id": "pairing-hardening",
          "title": "Hardening de pairing",
          "detail": "Tokens fuertes y lookup por hash preparados; falta revisión final de migración y transición legacy.",
          "status": "in_progress",
          "priority": "important"
        },
        {
          "id": "device-token-scope",
          "title": "Scope de token por dispositivo",
          "detail": "Restringir escrituras para que un dispositivo no opere sobre otro de la misma cuenta.",
          "status": "pending",
          "priority": "important"
        }
      ]
    }
  ]
}
""".trimIndent()

private val Page = Color(0xFFF7F7F8)
private val Ink = Color(0xFF202123)
private val Secondary = Color(0xFF4B5563)
private val Muted = Color(0xFF8A8F98)
private val Divider = Color(0xFFE7E7E8)
private val PendingRed = Color(0xFFE5484D)
private val ProgressAmber = Color(0xFFF59E0B)
private val DoneGreen = Color(0xFF30A46C)
private val BlockedGray = Color(0xFF7D828A)
