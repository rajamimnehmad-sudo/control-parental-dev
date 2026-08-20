package com.glosh.controlcenter

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TrackerApiUrl =
    "https://api.github.com/repos/rajamimnehmad-sudo/control-parental-dev/contents/docs/AI_TASK_TRACKER.json?ref=build%2Fglosh-control-center-v2"

private enum class TaskState(val wire: String, val label: String) {
    Pending("pending", "Pendiente"),
    InProgress("in_progress", "En progreso"),
    Done("done", "Hecho"),
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
    val tasks: List<TaskItem>,
)

private data class Tracker(
    val updatedAt: String,
    val sections: List<TaskSection>,
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
    var filter by remember { mutableStateOf<TaskState?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf("Datos incluidos") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            runCatching { loadRemoteTracker() }
                .onSuccess {
                    tracker = it
                    sourceLabel = "Actualizado desde GitHub"
                }
                .onFailure {
                    sourceLabel = "Sin conexión · mostrando último estado"
                }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF111111),
            background = Color(0xFFF7F7F8),
            surface = Color.White,
            onSurface = Color(0xFF202123),
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Header(
                        tracker = tracker,
                        sourceLabel = sourceLabel,
                        refreshing = refreshing,
                        onRefresh = ::refresh,
                    )
                }

                item {
                    SummaryRow(tracker)
                }

                item {
                    FilterRow(filter = filter, onFilter = { filter = it })
                }

                tracker.sections.forEach { section ->
                    val visibleTasks = section.tasks.filter { filter == null || it.state == filter }
                    if (visibleTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = section.title,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(visibleTasks, key = { it.id }) { task ->
                            TaskCard(task)
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Header(
    tracker: Tracker,
    sourceLabel: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text(
            text = "Glosh",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6B7280),
        )
        Text(
            text = "Control Center",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tus tareas, ordenadas y siempre al día.",
            fontSize = 15.sp,
            color = Color(0xFF6B7280),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(sourceLabel, fontSize = 13.sp, color = Color(0xFF6B7280))
                Text(
                    text = "Estado: ${tracker.updatedAt}",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Actualizando…" else "Actualizar")
            }
        }
    }
}

@Composable
private fun SummaryRow(tracker: Tracker) {
    val all = tracker.sections.flatMap { it.tasks }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryCard("Pendientes", all.count { it.state == TaskState.Pending }, TaskState.Pending, Modifier.weight(1f))
        SummaryCard("En progreso", all.count { it.state == TaskState.InProgress }, TaskState.InProgress, Modifier.weight(1f))
        SummaryCard("Hechas", all.count { it.state == TaskState.Done }, TaskState.Done, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(
    label: String,
    count: Int,
    state: TaskState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(stateColor(state), CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(count.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun FilterRow(
    filter: TaskState?,
    onFilter: (TaskState?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("Todas") })
        FilterChip(selected = filter == TaskState.Pending, onClick = { onFilter(TaskState.Pending) }, label = { Text("Pendientes") })
        FilterChip(selected = filter == TaskState.InProgress, onClick = { onFilter(TaskState.InProgress) }, label = { Text("En progreso") })
        FilterChip(selected = filter == TaskState.Done, onClick = { onFilter(TaskState.Done) }, label = { Text("Hechas") })
    }
}

@Composable
private fun TaskCard(task: TaskItem) {
    val color = stateColor(task.state)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(13.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF202123),
                    )
                    if (task.priority.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            color = Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(99.dp),
                        ) {
                            Text(
                                text = task.priority,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5563),
                            )
                        }
                    }
                }
                if (task.detail.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(task.detail, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF6B7280))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = task.state.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}

private fun stateColor(state: TaskState): Color = when (state) {
    TaskState.Pending -> Color(0xFFDC2626)
    TaskState.InProgress -> Color(0xFFF59E0B)
    TaskState.Done -> Color(0xFF16A34A)
}

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
                            priority = taskJson.optString("priority"),
                        ),
                    )
                }
            }
            add(
                TaskSection(
                    id = sectionJson.getString("id"),
                    title = sectionJson.getString("title"),
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
  "updatedAt": "19 ago 2026",
  "sections": [
    {
      "id": "chrome-visual",
      "title": "Chrome Visual",
      "tasks": [
        {
          "id": "chrome-video-a23",
          "title": "Revalidar video en A23",
          "detail": "Confirmar que la corrección de tormenta de eventos eliminó las coberturas completas repetidas en YouTube.",
          "status": "pending",
          "priority": "P0"
        },
        {
          "id": "chrome-images",
          "title": "Confirmar filtrado de fotos en Chrome",
          "detail": "Validar físicamente imágenes estáticas, scroll y overlays localizados.",
          "status": "pending",
          "priority": "P0"
        }
      ]
    },
    {
      "id": "security",
      "title": "Seguridad",
      "tasks": [
        {
          "id": "exact-supabase-hosts",
          "title": "Cerrar allowlist amplia de Supabase",
          "detail": "Implementado por Spark; pendiente revisión y commit limpio con Codex.",
          "status": "in_progress",
          "priority": "P0"
        },
        {
          "id": "atomic-policy-sync",
          "title": "Sincronización atómica de políticas",
          "detail": "Implementado y testeado por Spark; pendiente reconciliación local con Codex.",
          "status": "in_progress",
          "priority": "P0"
        },
        {
          "id": "pairing-hardening",
          "title": "Endurecer pairing y activación",
          "detail": "Tokens de alta entropía preparados; falta revisión final del estado acumulado.",
          "status": "in_progress",
          "priority": "P0"
        },
        {
          "id": "device-token-scope",
          "title": "Restringir tokens por dispositivo",
          "detail": "Evitar que un token de un dispositivo pueda escribir datos de otro dispositivo de la misma cuenta.",
          "status": "pending",
          "priority": "P0"
        }
      ]
    },
    {
      "id": "coordination",
      "title": "Coordinación",
      "tasks": [
        {
          "id": "codex-local-inventory",
          "title": "Inventario local completo con Codex",
          "detail": "Antes de nuevos tickets: reconciliar cambios sin commit, ramas, migraciones y diferencias contra GitHub.",
          "status": "pending",
          "priority": "P0"
        },
        {
          "id": "pro-audit",
          "title": "Auditoría Pro y mapa técnico",
          "detail": "Dejar mapa estable, control center, tareas y orden de trabajo definitivo.",
          "status": "pending",
          "priority": "P1"
        },
        {
          "id": "control-center-app",
          "title": "Glosh Control Center",
          "detail": "APK elegante de tareas para seguimiento compartido entre usuario y ChatGPT.",
          "status": "in_progress",
          "priority": "P1"
        }
      ]
    }
  ]
}
""".trimIndent()
