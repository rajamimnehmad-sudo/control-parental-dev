package com.contentfilter.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductListRow
import com.contentfilter.core.ui.ProductListSurface
import com.contentfilter.core.ui.ProductPageHeader
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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProductPageHeader(title = "Internet", subtitle = "Tu navegación protegida", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                UserInternetHero(
                    state = state,
                    vpnActive = vpnActive,
                )
            }
            item {
                Text("Protecciones", style = MaterialTheme.typography.titleSmall, color = InternetMuted)
            }
            item {
                InternetProtectionList(
                    state = state,
                    vpnActive = vpnActive,
                    onOpenDag = onOpenDag,
                    onActivateWebProtection = onActivateWebProtection,
                    onKeepSeparateDagLauncherChange = viewModel::setKeepSeparateDagLauncher,
                )
            }
        }
    }
}

@Composable
private fun UserInternetHero(
    state: com.contentfilter.user.internet.UserWebUiState,
    vpnActive: Boolean,
) {
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
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 154.dp).clip(shape)) {
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
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            }
        }
    }
}

@Composable
private fun InternetProtectionList(
    state: com.contentfilter.user.internet.UserWebUiState,
    vpnActive: Boolean,
    onOpenDag: () -> Unit,
    onActivateWebProtection: () -> Unit,
    onKeepSeparateDagLauncherChange: (Boolean) -> Unit,
) {
    ProductListSurface {
        InternetProtectionRow(
            icon = ProductIcon.ShieldCheck,
            label = "VPN",
            value = if (vpnActive) "Activa" else "Inactiva",
            active = vpnActive,
            trailing =
                if (vpnActive) {
                    null
                } else {
                    { TextButton(onClick = onActivateWebProtection) { Text("Reparar") } }
                },
        )
        InternetProtectionRow(
            icon = ProductIcon.Search,
            label = "SafeSearch",
            value = if (state.safeSearchEnabled) "Activo" else "Inactivo",
            active = state.safeSearchEnabled,
        )
        InternetProtectionRow(
            icon = ProductIcon.Web,
            label = "Solo resultados",
            value = if (state.onlyResultsEnabled) "Activo" else "Navegación autorizada",
            active = true,
        )
        InternetProtectionRow(
            icon = ProductIcon.Search,
            label = "Buscador DAG",
            value =
                when {
                    state.dagEnabled -> "Activo"
                    !state.dagEntitled -> "No incluido en la licencia"
                    else -> "Cerrado por el administrador"
                },
            active = state.dagEnabled,
            onClick = onOpenDag.takeIf { state.dagEnabled },
            showDivider = state.dagEnabled || state.schedule != null,
            navigation = state.dagEnabled,
        )
        if (state.dagEnabled) {
            ProductListRow(
                leading = {
                    ProductGlyph(
                        icon = ProductIcon.Search,
                        color = InternetAccent,
                        modifier = Modifier.size(24.dp),
                    )
                },
                headline = { Text("DAG como app separada", style = MaterialTheme.typography.titleMedium) },
                supporting = { Text("Mantener un acceso propio en el inicio del teléfono") },
                trailing = {
                    Switch(
                        checked = state.keepSeparateDagLauncher,
                        onCheckedChange = onKeepSeparateDagLauncherChange,
                    )
                },
                showDivider = state.schedule != null,
            )
        }
        state.schedule?.let { schedule ->
            InternetProtectionRow(
                icon = ProductIcon.Update,
                label = "Horario",
                value = schedule.summary,
                active = schedule.isAllowed,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun InternetProtectionRow(
    icon: ProductIcon,
    label: String,
    value: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    navigation: Boolean = false,
    showDivider: Boolean = true,
) {
    ProductListRow(
        leading = { ProductGlyph(icon = icon, color = InternetAccent, modifier = Modifier.size(24.dp)) },
        headline = { Text(label, style = MaterialTheme.typography.titleMedium) },
        supporting = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) InternetActive else InternetWarning,
            )
        },
        trailing =
            trailing
                ?: if (navigation) {
                    { ProductGlyph(icon = ProductIcon.ChevronRight, color = InternetMuted, modifier = Modifier.size(22.dp)) }
                } else {
                    null
                },
        onClick = onClick,
        showDivider = showDivider,
    )
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

private val InternetAccent = Color(0xFF008D93)
private val InternetActive = Color(0xFF18794E)
private val InternetWarning = Color(0xFF9A6700)
private val InternetMuted = Color(0xFF68758A)
