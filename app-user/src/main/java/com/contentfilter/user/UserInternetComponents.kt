package com.contentfilter.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductLazyVisualPage
import com.contentfilter.user.internet.UserWebViewModel

@Composable
internal fun UserWebTab(
    onBack: (() -> Unit)?,
    onOpenDag: () -> Unit,
    vpnActive: Boolean,
    onActivateWebProtection: () -> Unit,
    viewModel: UserWebViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProductLazyVisualPage(
        title = "Internet",
        subtitle = "Tu navegación protegida",
        onBack = onBack,
    ) {
        item {
            UserInternetStatusCard(
                state = state,
                vpnActive = vpnActive,
                onOpenDag = onOpenDag,
                onActivateWebProtection = onActivateWebProtection,
            )
        }
    }
}

@Composable
private fun UserInternetStatusCard(
    state: com.contentfilter.user.internet.UserWebUiState,
    vpnActive: Boolean,
    onOpenDag: () -> Unit,
    onActivateWebProtection: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scheduleBlocked = state.schedule?.isAllowed == false
    val blocked = state.webNavigationBlocked || scheduleBlocked
    val status =
        when {
            blocked -> InternetVisualStatus.Blocked
            !vpnActive -> InternetVisualStatus.Review
            else -> InternetVisualStatus.Protected
        }
    val summary =
        when {
            state.webNavigationBlocked -> "El administrador pausó la navegación"
            scheduleBlocked -> state.schedule?.summary ?: "Fuera del horario permitido"
            !vpnActive -> "Activá la VPN para recuperar la protección Web"
            state.schedule != null -> state.schedule.summary
            else -> "SafeSearch activo${if (state.onlyResultsEnabled) " · Solo resultados" else ""}"
        }
    val shape = RoundedCornerShape(26.dp)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 238.dp).clip(shape)) {
            Image(
                painter = painterResource(R.drawable.user_internet_status_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    status.overlay.copy(alpha = 0.94f),
                                    status.overlay.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.42f),
                                ),
                            ),
                        ),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.86f),
                )
                Text(
                    text = state.compactProtectionSummary,
                    style = MaterialTheme.typography.labelLarge,
                    color = status.accent,
                )
                Text(
                    text = if (expanded) "Ocultar detalle  ⌃" else "Ver detalle  ⌄",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        InternetProtectionLine(
                            "VPN",
                            if (vpnActive) "Activa" else "Inactiva",
                            vpnActive,
                        )
                        InternetProtectionLine(
                            "SafeSearch",
                            if (state.safeSearchEnabled) "Activo" else "Inactivo",
                            state.safeSearchEnabled,
                        )
                        InternetProtectionLine(
                            "Solo resultados",
                            if (state.onlyResultsEnabled) "Activo" else "Navegación autorizada",
                            true,
                        )
                        InternetProtectionLine(
                            "Buscador DAG",
                            when {
                                state.dagEnabled -> "Activo"
                                !state.dagEntitled -> "No incluido en la licencia"
                                else -> "Cerrado por el administrador"
                            },
                            state.dagEnabled,
                        )
                        state.schedule?.let { schedule ->
                            InternetProtectionLine("Horario", schedule.summary, schedule.isAllowed)
                        }
                        Text(
                            text = "Configurado por tu administrador",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.66f),
                        )
                    }
                }
                if (!vpnActive) {
                    Button(onClick = onActivateWebProtection) {
                        Text("Reparar protección")
                    }
                } else if (state.dagEnabled) {
                    Button(onClick = onOpenDag) {
                        Text("Abrir DAG")
                    }
                }
            }
        }
    }
}

@Composable
private fun InternetProtectionLine(
    label: String,
    value: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color(0xFF8EF0C0) else Color(0xFFFFD27A),
        )
    }
}

private val com.contentfilter.user.internet.UserWebUiState.compactProtectionSummary: String
    get() =
        buildList {
            if (safeSearchEnabled) add("SafeSearch")
            if (onlyResultsEnabled) add("Solo resultados")
            if (dagEnabled) add("DAG activo")
        }.joinToString(" · ").ifBlank { "Protección Web configurada" }

private enum class InternetVisualStatus(
    val label: String,
    val overlay: Color,
    val accent: Color,
) {
    Protected("Internet protegido", Color(0xFF09283A), Color(0xFF8EF0C0)),
    Review("Internet por revisar", Color(0xFF443316), Color(0xFFFFD27A)),
    Blocked("Internet bloqueado", Color(0xFF321B25), Color(0xFFFFB0B7)),
}
