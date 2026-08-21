package com.glosh.controlcenter

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private const val TRACKER_API_URL =
    "https://api.github.com/repos/rajamimnehmad-sudo/control-parental-dev/contents/docs/AI_TASK_TRACKER.json?ref=build%2Fglosh-control-center-v2"
private const val AUTO_REFRESH_MILLIS = 5 * 60 * 1000L

private enum class AppView(val label: String) {
    Priority("Prioridad"),
    Sections("Secciones"),
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

private data class Tracker(val updatedAt: String, val sections: List<TaskSection>)
private data class RoutedTask(val section: TaskSection, val task: TaskItem)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControlCenterApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlCenterApp() {
    var tracker by remember { mutableStateOf(parseTracker(DEFAULT_TRACKER_JSON)) }
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
                .onFailure { lastSyncLabel = "sin conexión · ${clockNow()}" }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(AUTO_REFRESH_MILLIS)
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
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = ::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Header(tracker, lastSyncLabel, refreshing, ::refresh)
                    ViewSelector(currentView) { selected ->
                        currentView = selected
                        expandedTaskId = null
                        expandedSectionId = null
                    }
                    StatusSummary(tracker.sections.flatMap { it.tasks })
                    when (currentView) {
                        AppView.Priority -> PriorityScreen(
                            tracker = tracker,
                            expandedTaskId = expandedTaskId,
                            onTask = { id -> expandedTaskId = if (expandedTaskId == id) null else id },
                        )
                        AppView.Sections -> SectionsScreen(
                            tracker = tracker,
                            expandedSectionId = expandedSectionId,
                            expandedTaskId = expandedTaskId,
                            onSection = { id -> expandedSectionId = if (expandedSectionId == id) null else id },
                            onTask = { id -> expandedTaskId = if (expandedTaskId == id) null else id },
                        )
                    }
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
            Text("Último cambio · ${tracker.updatedAt}", fontSize = 12.sp, color = Secondary)
            Text(
                "Sincronizado · ${if (refreshing) "actualizando…" else lastSyncLabel}",
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Surface(
            modifier = Modifier.size(42.dp).clickable(enabled = !refreshing, onClick = onRefresh),
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
private fun ViewSelector(current: AppView, onSelect: (AppView) -> Unit) {
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
                modifier = Modifier.weight(1f).clickable { onSelect(view) },
                shape = RoundedCornerShape(9.dp),
                color = if (current == view) Ink else Color.Transparent,
            ) {
                Text(
                    text = view.label,
                    modifier = Modifier.padding(vertical = 9.dp),
                    fontSize = 12.sp,
                    fontWeight = if (current == view) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current == view) Color.White else Secondary,
                    textAlign = TextAlign.Center,
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
    val active = routed
        .filter { it.task.state == TaskState.InProgress }
        .sortedWith(compareBy<RoutedTask> { priorityRank(it.task.priority) }.thenBy { it.task.title })
    val next = routed
        .filter { it.task.state == TaskState.Pending || it.task.state == TaskState.Blocked }
        .sortedWith(
            compareBy<RoutedTask> { priorityRank(it.task.priority) }
                .thenBy { if (it.task.state == TaskState.Blocked) 1 else 0 }
                .thenBy { it.task.title },
        )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeading("Ahora", "Lo que está activo") }
        if (active.isEmpty()) {
            item { EmptyLine("Nada en progreso ahora") }
        } else {
            items(active, key = { it.task.id }) { routedTask ->
                TaskRow(
                    task = routedTask.task,
                    sectionTitle = routedTask.section.title,
                    expanded = expandedTaskId == routedTask.task.id,
                    onClick = { onTask(routedTask.task.id) },
                )
            }
        }

        item { ListHeading("Después", "Siguiente ruta") }
        if (next.isEmpty()) {
            item { EmptyLine("No quedan tareas pendientes") }
        } else {
            items(next, key = { it.task.id }) { routedTask ->
                TaskRow(
                    task = routedTask.task,
                    sectionTitle = routedTask.section.title,
                    expanded = expandedTaskId == routedTask.task.id,
                    onClick = { onTask(routedTask.task.id) },
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
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
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(10.dp)) }
        items(tracker.sections, key = { it.id }) { section ->
            val expanded = expandedSectionId == section.id
            SectionRow(section, expanded) { onSection(section.id) }
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
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun ListHeading(title: String, context: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (context.isNotBlank()) Text(context, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), fontSize = 13.sp, color = Muted)
}

@Composable
private fun SectionRow(section: TaskSection, expanded: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.White) {
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
        MiniStateCount(TaskState.Pending, tasks.count { it.state == TaskState.Pending })
        MiniStateCount(TaskState.InProgress, tasks.count { it.state == TaskState.InProgress })
        MiniStateCount(TaskState.Done, tasks.count { it.state == TaskState.Done })
        if (tasks.any { it.state == TaskState.Blocked }) {
            MiniStateCount(TaskState.Blocked, tasks.count { it.state == TaskState.Blocked })
        }
    }
}

@Composable
private fun MiniStateCount(state: TaskState, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        TaskStateIcon(state, 10.dp)
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
    val priority = priorityLabel(task.priority)
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.White) {
        Column(
            modifier = Modifier.padding(
                start = if (inset) 34.dp else 20.dp,
                end = 20.dp,
                top = 13.dp,
                bottom = if (expanded) 14.dp else 13.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskStateIcon(task.state, 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                    if (sectionTitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(sectionTitle, fontSize = 11.sp, color = Muted)
                    }
                }
                PriorityIndicator(task.priority)
                Spacer(Modifier.width(10.dp))
                Text(if (expanded) "⌃" else "›", fontSize = 17.sp, color = Muted)
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (task.detail.isNotBlank()) {
                    Text(
                        task.detail,
                        modifier = Modifier.padding(start = 28.dp),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TaskStateIcon(task.state, 12.dp)
                        Text(task.state.label, fontSize = 11.sp, color = Ink)
                    }
                    Text(priority, fontSize = 11.sp, color = priorityColor(task.priority))
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = if (inset) 34.dp else 20.dp), color = Divider)
}

@Composable
private fun PriorityIndicator(priority: String) {
    val color = priorityColor(priority)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(priorityLabel(priority), fontSize = 10.sp, color = color)
    }
}

@Composable
private fun StatusSummary(tasks: List<TaskItem>) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryItem(TaskState.Pending, "Pendientes", tasks.count { it.state == TaskState.Pending }, Modifier.weight(1f))
        SummaryItem(
            TaskState.InProgress,
            "En proceso",
            tasks.count { it.state == TaskState.InProgress },
            Modifier.weight(1f),
        )
        SummaryItem(TaskState.Done, "Hechas", tasks.count { it.state == TaskState.Done }, Modifier.weight(1f))
        SummaryItem(TaskState.Blocked, "Bloqueadas", tasks.count { it.state == TaskState.Blocked }, Modifier.weight(1f))
    }
    HorizontalDivider(color = Divider)
}

@Composable
private fun SummaryItem(state: TaskState, label: String, count: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskStateIcon(state, 14.dp)
            Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Muted, maxLines = 1)
    }
}

@Composable
private fun TaskStateIcon(state: TaskState, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val diameter = size.minDimension
        val strokeWidth = (diameter * 0.13f).coerceAtLeast(1.5f)
        when (state) {
            TaskState.Done -> {
                drawCircle(Ink)
                drawLine(
                    Color.White,
                    Offset(diameter * 0.27f, diameter * 0.52f),
                    Offset(diameter * 0.44f, diameter * 0.69f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(diameter * 0.44f, diameter * 0.69f),
                    Offset(diameter * 0.75f, diameter * 0.34f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
            TaskState.InProgress -> repeat(8) { segment ->
                drawArc(
                    Ink,
                    startAngle = -90f + segment * 45f,
                    sweepAngle = 25f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
            }
            TaskState.Pending -> drawCircle(Ink, style = Stroke(strokeWidth))
            TaskState.Blocked -> {
                drawCircle(Ink, style = Stroke(strokeWidth))
                drawLine(
                    Ink,
                    Offset(diameter * 0.31f, diameter * 0.50f),
                    Offset(diameter * 0.69f, diameter * 0.50f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
        }
    }
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

private fun priorityColor(priority: String): Color = when (priorityRank(priority)) {
    0 -> CriticalRed
    1 -> ImportantAmber
    else -> NormalGreen
}

private fun clockNow(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private suspend fun loadRemoteTracker(): Tracker = withContext(Dispatchers.IO) {
    val connection = (URL(TRACKER_API_URL).openConnection() as HttpURLConnection).apply {
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
    return Tracker(root.optString("updatedAt", "sin fecha"), sections)
}

private const val DEFAULT_TRACKER_JSON =
    """{"updatedAt":"hoy","sections":[{"id":"coordination","title":"Coordinación","context":"Estado y ruta de trabajo","tasks":[{"id":"control-center-v3","title":"Control Center","detail":"Tablero compacto sincronizado con la ruta técnica.","status":"in_progress","priority":"important"}]},{"id":"delivery","title":"Entrega","context":"Próximos gates","tasks":[{"id":"apk-release-gates","title":"Gate de APK","detail":"Pendiente de validación.","status":"pending","priority":"normal"}]}]}"""

private val Page = Color(0xFFF7F7F8)
private val Ink = Color(0xFF202123)
private val Secondary = Color(0xFF4B5563)
private val Muted = Color(0xFF8A8F98)
private val Divider = Color(0xFFE7E7E8)
private val CriticalRed = Color(0xFFE5484D)
private val ImportantAmber = Color(0xFFF59E0B)
private val NormalGreen = Color(0xFF30A46C)
