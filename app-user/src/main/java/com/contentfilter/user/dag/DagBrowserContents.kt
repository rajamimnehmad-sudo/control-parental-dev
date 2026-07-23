package com.contentfilter.user.dag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.RequestStatus
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DagStartContent(
    address: String,
    suggestions: List<String>,
    analyzing: Boolean,
    analysisProgress: Float,
    onAddressChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBeginEdit: () -> Unit,
    recentHistory: List<DagHistoryEntry>,
    onRecentSiteSelected: (DagHistoryEntry) -> Unit,
) {
    val keyboardVisible = WindowInsets.isImeVisible
    val recentSites =
        remember(recentHistory) {
            recentHistory
                .asSequence()
                .filter { it.type == DagHistoryType.Page && !it.url.isNullOrBlank() }
                .distinctBy { DagContentClassifier.domainFrom(it.url.orEmpty()) }
                .take(MaximumRecentSites)
                .toList()
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(if (keyboardVisible) 48.dp else 88.dp))
        Text("DAG", style = MaterialTheme.typography.displaySmall)
        Text(
            "Internet kosher con protección local",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        DagAnalysisFrame(
            analyzing = analyzing,
            progress = if (analyzing) analysisProgress else 0f,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        if (analyzing) {
                            stateDescription = "${(analysisProgress * 100).roundToInt()} por ciento analizado"
                        }
                    },
            cornerRadius = 28.dp,
        ) {
            OutlinedTextField(
                value = if (analyzing) "" else address,
                onValueChange = onAddressChanged,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged { if (it.isFocused && analyzing) onBeginEdit() },
                placeholder = { if (analyzing) DagAnalyzingText() else Text("Buscar en Internet") },
                readOnly = false,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (analyzing) Color.Transparent else MaterialTheme.colorScheme.outline,
                        focusedContainerColor = if (analyzing) DagNeonCyan.copy(alpha = 0.10f) else Color.Transparent,
                        unfocusedContainerColor =
                            if (analyzing) {
                                DagNeonViolet.copy(
                                    alpha = 0.08f,
                                )
                            } else {
                                Color.Transparent
                            },
                    ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onSubmit() }),
                trailingIcon =
                    if (analyzing) {
                        null
                    } else {
                        { TextButton(onClick = onSubmit, enabled = address.isNotBlank()) { Text("Buscar") } }
                    },
            )
        }
        if (suggestions.isNotEmpty() && !analyzing) {
            DagSearchSuggestions(suggestions, onSuggestionSelected)
        }
        if (!keyboardVisible && !analyzing && recentSites.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Sitios recientes",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(recentSites, key = DagHistoryEntry::id) { entry ->
                    val domain = DagContentClassifier.domainFrom(entry.url.orEmpty())
                    Column(
                        modifier = Modifier.width(64.dp).clickable { onRecentSiteSelected(entry) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    dagSiteGlyph(domain),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            dagSiteLabel(domain),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Las páginas y sus imágenes se analizan antes de mostrarse.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DagSearchSuggestions(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                Text(
                    text = suggestion,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (index != suggestions.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
internal fun DagResultsContent(
    results: List<DagSearchResult>,
    query: String,
    canLoadMore: Boolean,
    loading: Boolean,
    onOpen: (DagSearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onCorrectedSearch: (String) -> Unit,
) {
    val correctedQuery = remember(query) { dagDidYouMeanSuggestion(query) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "results-header") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Resultados", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${results.size} resultados filtrados por DAG",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
        correctedQuery?.let { correction ->
            item(key = "did-you-mean") {
                TextButton(
                    onClick = { onCorrectedSearch(correction) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Tal vez quisiste decir: $correction")
                }
            }
        }
        items(results, key = { it.url }) { result ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = result.classification.decision != DagClassification.Blocked) {
                            onOpen(result)
                        }
                        .heightIn(min = 88.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                dagSiteGlyph(result.domain),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        result.domain,
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.description.isNotBlank()) {
                    Text(
                        result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (result.classification.decision == DagClassification.Uncertain) {
                    Text(
                        "DAG analizará la página antes de mostrarla",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
        if (canLoadMore) {
            item(key = "more-results") {
                TextButton(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Text(if (loading) "Buscando…" else "Más resultados · consume 1 búsqueda")
                }
            }
        }
    }
}

private const val MaximumRecentSites = 6

private fun dagSiteGlyph(domain: String): String {
    val normalized = domain.lowercase()
    return when {
        normalized.endsWith("instagram.com") -> "IG"
        normalized.endsWith("mercadolibre.com.ar") -> "ML"
        normalized.endsWith("argentina.gob.ar") -> "AR"
        normalized.endsWith("fravega.com") -> "F"
        normalized.endsWith("hm.com") -> "H&M"
        normalized.endsWith("zara.com") -> "Z"
        else -> dagSiteLabel(normalized).firstOrNull()?.uppercaseChar()?.toString() ?: "↗"
    }
}

private fun dagSiteLabel(domain: String): String {
    val parts = domain.lowercase().split(".").filter(String::isNotBlank)
    val firstUseful =
        parts.firstOrNull { part ->
            part != "m" && part != "mobile" && !part.matches(Regex("www\\d*"))
        }
    return firstUseful?.ifBlank { "Sitio" } ?: "Sitio"
}

@Composable
internal fun DagReviewRequestsContent(
    requests: List<AccessRequest>,
    onOpenApproved: (AccessRequest) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Revisiones de DAG", style = MaterialTheme.typography.titleLarge)
            Text(
                "Estados guardados en este teléfono",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (requests.isEmpty()) {
            Text(
                "Todavía no pediste revisiones de sitios.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(requests.take(MaxVisibleReviewRequests), key = AccessRequest::id) { request ->
                    val approved = request.status == RequestStatus.Approved
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = approved) { onOpenApproved(request) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            request.targetDomain ?: request.target,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(dagReviewStatusLabel(request.status), style = MaterialTheme.typography.labelLarge)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(request.createdAtEpochMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (approved) {
                            Text(
                                "Tocá para abrir el sitio con la protección de DAG",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

internal fun dagReviewStatusLabel(status: RequestStatus): String =
    when (status) {
        RequestStatus.PendingLocal -> "Pendiente de envío"
        RequestStatus.PendingRemote -> "Pendiente de revisión"
        RequestStatus.Approved -> "Aprobada"
        RequestStatus.Rejected -> "Rechazada"
        RequestStatus.Expired -> "Expirada"
    }

@Composable
internal fun DagHistoryContent(
    history: List<DagHistoryEntry>,
    onOpen: (DagHistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Historial", style = MaterialTheme.typography.titleLarge)
            Text(
                "Guardado solo en este teléfono",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Borrar todo") }
    }
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Todavía no hay búsquedas ni páginas visitadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.visitedAtEpochMillis))
                val previousDate =
                    history.getOrNull(index - 1)?.let {
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.visitedAtEpochMillis))
                    }
                if (date != previousDate) {
                    Text(
                        date,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.width(38.dp).height(38.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (entry.type == DagHistoryType.Search) "⌕" else "↗")
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            entry.title ?: entry.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (entry.type == DagHistoryType.Search) {
                                "Búsqueda"
                            } else {
                                DagContentClassifier.domainFrom(entry.url.orEmpty())
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.visitedAtEpochMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onDelete(entry.id) },
                        modifier = Modifier.semantics { contentDescription = "Borrar del historial" },
                    ) { Text("×") }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 66.dp))
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán del teléfono todas las búsquedas y páginas guardadas en DAG.") },
            confirmButton = {
                Button(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("Borrar todo") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
