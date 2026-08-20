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
    Priority("Prioridad"), Sections("Secciones"), Overview("Vista general"),
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
    var tracker by remember { mutableStateOf(parseTracker(DefaultTrackerJson)) }
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
            delay(AutoRefreshMillis)
            refresh()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, background = Page, surface = Color.White, onSurface = Ink),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Page) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = ::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Header(tracker, lastSyncLabel, refreshing, ::refresh)
                    ViewSelector(currentView) {
                        currentView = it
                        expandedTaskId = null
                        expandedSectionId = null
                    }
                    when (currentView) {
                        AppView.Priority -> PriorityScreen(tracker, expandedTaskId) {
                            expandedTaskId = if (expandedTaskId == it) null else it
                        }
                        AppView.Sections -> SectionsScreen(
                            tracker,
                            expandedSectionId,
                            expandedTaskId,
                            { expandedSectionId = if (expandedSectionId == it) null else it },
                            { expandedTaskId = if (expandedTaskId == it) null else it },
                        )
                        AppView.Overview -> OverviewScreen(tracker, expandedTaskId) {
                            expandedTaskId = if (expandedTaskId == it) null else it
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(tracker: Tracker, lastSyncLabel: String, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White)
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
        modifier = Modifier.fillMaxWidth().background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(Page, RoundedCornerShape(12.dp)).padding(3.dp),
    ) {
        AppView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelect(view) },
                shape = RoundedCornerShape(9.dp),
                color = if (current == view) Ink else Color.Transparent,
            ) {
                Text(
                    view.label,
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
private fun PriorityScreen(tracker: Tracker, expandedTaskId: String?, onTask: (String) -> Unit) {
    val routed = tracker.sections.flatMap { section -> section.tasks.map { RoutedTask(section, it) } }
    val now = routed.filter { it.task.state == TaskState.InProgress }
        .sortedWith(compareBy<RoutedTask> { priorityRank(it.task.priority) }.thenBy { it.task.title })
    val next = routed.filter { it.task.state == TaskState.Pending || it.task.state == TaskState.Blocked }
        .sortedWith(compareBy<RoutedTask> { priorityRank(it.task.priority) }.thenBy { it.task.title })
    val doneCount = routed.count { it.task.state == TaskState.Done }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ListHeading("Ahora", "Lo que está activo") }
        if (now.isEmpty()) item { EmptyLine("Nada en progreso ahora") }
        else items(now, key = { it.task.id }) { item ->
            TaskRow(item.task, item.section.title, expandedTaskId == item.task.id) { onTask(item.task.id) }
        }
        item { ListHeading("Después", "Siguiente ruta") }
        if (next.isEmpty()) item { EmptyLine("No quedan tareas pendientes") }
        else items(next, key = { it.task.id }) { item ->
            TaskRow(item.task, item.section.title, expandedTaskId == item.task.id) { onTask(item.task.id) }
        }
        if (doneCount > 0) item {
            Text(
                "$doneCount tareas terminadas",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                fontSize = 12.sp,
                color = Muted,
            )
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
    LazyColumn(Modifier.fillMaxSize()) {
        items(tracker.sections, key = { it.id }) { section ->
            val expanded = expandedSectionId == section.id
            SectionRow(section, expanded) { onSection(section.id) }
            if (expanded) {
                section.tasks.sortedWith(compareBy<TaskItem> { stateRank(it.state) }.thenBy { priorityRank(it.priority) })
                    .forEach { task ->
                        TaskRow(task, null, expandedTaskId == task.id, true) { onTask(task.id) }
                    }
            }
        }
    }
}

@Composable
private fun OverviewScreen(tracker: Tracker, expandedTaskId: String?, onTask: (String) -> Unit) {
    val all = tracker.sections.flatMap { it.tasks }
    LazyColumn(Modifier.fillMaxSize()) {
        item { StatusSummary(all) }
        tracker.sections.forEach { section ->
            item { ListHeading(section.title, section.context) }
            items(section.tasks, key = { it.id }) { task ->
                TaskRow(task, null, expandedTaskId == task.id) { onTask(task.id) }
            }
        }
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
    Text(text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), fontSize = 13.sp, color = Muted)
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
                        Text(section.context, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        if (tasks.any { it.state == TaskState.Blocked }) MiniCount(tasks.count { it.state == TaskState.Blocked }, BlockedGray)
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
    val priority = priorityLabel(task.priority)
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = Color.White) {
        Column(
            Modifier.padding(
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
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                    if (sectionTitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(sectionTitle, fontSize = 11.sp, color = Muted)
                    }
                }
                if (priority != "Normal") {
                    Text(priority, fontSize = 10.sp, color = if (priority == "Crítico") PendingRed else ProgressAmber)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (expanded) "⌃" else "›", fontSize = 17.sp, color = Muted)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (task.detail.isNotBlank()) {
                    Text(
                        task.detail,
                        modifier = Modifier.padding(start = 21.dp),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(start = 21.dp), horizontalArrangement =