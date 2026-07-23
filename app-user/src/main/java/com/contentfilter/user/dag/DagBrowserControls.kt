package com.contentfilter.user.dag

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun DagAnalysisFrame(
    analyzing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    content: @Composable () -> Unit,
) {
    if (!analyzing || !dagAnimationsEnabled()) {
        Box(modifier = modifier) { content() }
        return
    }
    val transition = rememberInfiniteTransition(label = "dag-analysis-glow")
    val angle by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1_800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "dag-analysis-angle",
        )
    val visibleProgress = progress.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        content()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                DagNeonCyan.copy(alpha = 0.03f),
                                DagNeonViolet.copy(alpha = 0.13f),
                                Color(0xFFFF4FD8).copy(alpha = 0.06f),
                            ),
                    ),
                size = androidx.compose.ui.geometry.Size(size.width * visibleProgress, size.height),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                    ),
            )
            val radians = Math.toRadians(angle.toDouble())
            val direction =
                Offset(
                    x = (cos(radians) * size.width).toFloat(),
                    y = (sin(radians) * size.height).toFloat(),
                )
            drawRoundRect(
                brush =
                    Brush.linearGradient(
                        colors = listOf(DagNeonCyan, DagNeonViolet, Color(0xFFFF4FD8), DagNeonCyan),
                        start = center - direction,
                        end = center + direction,
                    ),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                    ),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun DagAnalyzingText() {
    if (!dagAnimationsEnabled()) {
        Text("Analizando…")
        return
    }
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350L)
            dots = dots % 3 + 1
        }
    }
    Text("Analizando${".".repeat(dots)}")
}

@Composable
private fun dagAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

@Composable
internal fun DagTabSwitcher(
    tabs: List<DagTab>,
    activeTabId: String,
    currentSnapshot: DagTabSnapshot,
    onDismiss: () -> Unit,
    onOpen: (DagTab) -> Unit,
    onClose: (DagTab) -> Unit,
    onNew: () -> Unit,
    onCloseAll: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pestañas recientes", style = MaterialTheme.typography.titleLarge)
                        Text("${tabs.size} de $MaximumTabs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("Listo") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCloseAll, enabled = tabs.size > 1) { Text("Cerrar todas") }
                    TextButton(
                        onClick = onNew,
                        enabled =
                            tabs.size < MaximumTabs ||
                                currentSnapshot.isEmptyTab() ||
                                tabs.any { it.snapshot.isEmptyTab() },
                    ) { Text("＋ Nueva") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(tabs.sortedByDescending(DagTab::lastUsedAtEpochMillis), key = { it.id }) { tab ->
                        val snapshot = if (tab.id == activeTabId) currentSnapshot else tab.snapshot
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(tab.copy(snapshot = snapshot)) },
                            shape = RoundedCornerShape(16.dp),
                            border =
                                if (tab.id == activeTabId) {
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                            tonalElevation = 2.dp,
                        ) {
                            Column {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    tab.preview?.let { preview ->
                                        Image(
                                            bitmap = preview,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } ?: Text(
                                        text = "DAG",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    if (tab.id == activeTabId) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                "Actual",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { onClose(tab) },
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .semantics { contentDescription = "Cerrar pestaña" },
                                    ) { Text("×") }
                                }
                                Text(
                                    snapshot.tabLabel(tabs.indexOf(tab) + 1),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal const val TabPreviewWidth = 240
internal const val TabPreviewHeight = 360
internal const val MaximumTabs = 8
internal const val MaxVisibleReviewRequests = 50
internal const val TabPersistenceDebounceMillis = 500L

private fun DagTabSnapshot.tabLabel(position: Int): String =
    when (view) {
        DagView.Browser -> DagContentClassifier.domainFrom(requestedUrl.orEmpty()).ifBlank { "Pestaña $position" }
        DagView.Results -> address.ifBlank { "Resultados" }
        DagView.History -> "Historial"
        DagView.Reviews -> "Revisiones"
        DagView.Start -> "Nueva pestaña"
    }
