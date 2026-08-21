package com.contentfilter.admin

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.announcements.AdminAnnouncementsViewModel
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.admin.dashboard.ProtectedUserHealthUiState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.GloshWordmark
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon

@Composable
internal fun RedesignedHomeTab(
    onCreateUser: () -> Unit,
    onRequests: () -> Unit,
    onProtectionStatus: () -> Unit,
    onAnnouncements: () -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val announcementsViewModel: AdminAnnouncementsViewModel = hiltViewModel()
    val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { announcementsViewModel.refresh() }

    val affectedCount = dashboardState.protectedUsers.count(ProtectedUserHealthUiState::hasConfirmedProblem)
    val pendingVerification = dashboardState.protectedUsers.count(ProtectedUserHealthUiState::requiresVerification)
    val possibleUninstall = dashboardState.protectedUsers.count(ProtectedUserHealthUiState::possibleUninstall)
    val attentionCount = (affectedCount + pendingVerification + possibleUninstall).coerceAtMost(dashboardState.protectedUsers.size)
    val healthy = attentionCount == 0 && dashboardState.licenseState.isOperational()

    Column(modifier = Modifier.fillMaxSize().background(GloshColors.Bone)) {
        AdminV4Header(
            administratorName = dashboardState.guideName.ifBlank { "Administrador" },
            communityName = dashboardState.communityName,
            announcementCount = announcementsState.unreadCount,
            onAnnouncements = onAnnouncements,
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = GloshSpacing.PageHorizontal,
                        top = 4.dp,
                        end = GloshSpacing.PageHorizontal,
                        bottom = 28.dp,
                    ),
        ) {
            AdminProtectionHero(
                healthy = healthy,
                totalUsers = dashboardState.activeUserCount,
                attentionCount = attentionCount,
                licenseState = dashboardState.licenseState,
                onClick = onProtectionStatus,
            )

            AdminMetricsLine(
                activeUsers = dashboardState.activeUserCount,
                pendingRequests = dashboardState.pendingRequests,
                onRequests = onRequests,
            )

            AdminSectionHeader("Ahora")
            AdminActionRow(
                icon = ProductIcon.Person,
                title = "Agregar usuario",
                subtitle = "Crear y vincular un nuevo usuario",
                trailing = null,
                onClick = onCreateUser,
            )
            AdminActionRow(
                icon = ProductIcon.Requests,
                title = if (dashboardState.pendingRequests == 0) "Solicitudes" else "${dashboardState.pendingRequests} solicitudes esperando",
                subtitle = if (dashboardState.pendingRequests == 0) "No hay decisiones pendientes" else "Resolver pedidos sin entrar usuario por usuario",
                trailing = if (dashboardState.pendingRequests > 0) "Revisar" else null,
                warning = dashboardState.pendingRequests > 0,
                onClick = onRequests,
            )
            AdminActionRow(
                icon = if (healthy) ProductIcon.ShieldCheck else ProductIcon.ShieldAlert,
                title = if (healthy) "Protección estable" else "Protección por revisar",
                subtitle =
                    when {
                        attentionCount > 0 -> "$attentionCount usuario${if (attentionCount == 1) "" else "s"} necesita${if (attentionCount == 1) "" else "n"} atención"
                        !dashboardState.licenseState.isOperational() -> "La licencia necesita revisión"
                        else -> "Todos los usuarios responden correctamente"
                    },
                trailing = if (healthy) "OK" else "Revisar",
                warning = !healthy,
                onClick = onProtectionStatus,
            )

            AdminSectionHeader("Cuenta")
            AdminFlatInfoRow(
                label = "Licencia",
                value = dashboardState.licenseState.adminLabel(),
                warning = !dashboardState.licenseState.isOperational(),
            )
            if (dashboardState.communityName.isNotBlank()) {
                AdminFlatInfoRow(label = "Comunidad", value = dashboardState.communityName)
            }
        }
    }
}

@Composable
private fun AdminV4Header(
    administratorName: String,
    communityName: String,
    announcementCount: Int,
    onAnnouncements: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = GloshSpacing.PageHorizontal,
                    top = 12.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 16.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GloshWordmark(modifier = Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = onAnnouncements,
                    modifier = Modifier.size(44.dp).background(GloshColors.Surface, CircleShape),
                ) {
                    ProductGlyph(ProductIcon.Bell, GloshColors.Graphite, Modifier.size(22.dp))
                }
                if (announcementCount > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(GloshColors.Lime, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            announcementCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = GloshColors.Graphite,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Hola, $administratorName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GloshColors.Graphite,
            )
            if (communityName.isNotBlank()) {
                Text(communityName, style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
            }
        }
    }
}

@Composable
private fun AdminProtectionHero(
    healthy: Boolean,
    totalUsers: Int,
    attentionCount: Int,
    licenseState: LicenseState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(9.dp)
                            .background(if (healthy) GloshColors.Lime else GloshColors.Warning, CircleShape),
                )
                Text(
                    if (healthy) "Protección estable" else "Necesita atención",
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GloshColors.Surface,
                )
                Text(
                    licenseState.adminLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = GloshColors.Surface.copy(alpha = 0.72f),
                )
            }
            Text(
                if (healthy) "$totalUsers usuario${if (totalUsers == 1) "" else "s"} protegido${if (totalUsers == 1) "" else "s"}" else "$attentionCount por revisar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GloshColors.Surface,
            )
            Text(
                if (healthy) "No hay problemas importantes ahora." else "Entrá para ver exactamente quién necesita una acción.",
                style = MaterialTheme.typography.bodyMedium,
                color = GloshColors.Surface.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun AdminMetricsLine(
    activeUsers: Int,
    pendingRequests: Int,
    onRequests: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AdminMetric(value = activeUsers.toString(), label = "usuarios activos", modifier = Modifier.weight(1f))
        AdminMetric(
            value = pendingRequests.toString(),
            label = "solicitudes",
            modifier = Modifier.weight(1f).clickable(onClick = onRequests),
            emphasized = pendingRequests > 0,
        )
    }
}

@Composable
private fun AdminMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) GloshColors.Warning else GloshColors.Graphite,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
    }
}

@Composable
private fun AdminSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = GloshColors.Graphite,
    )
}

@Composable
private fun AdminActionRow(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    trailing: String?,
    warning: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(GloshColors.Surface, GloshShapes.Small),
            contentAlignment = Alignment.Center,
        ) {
            ProductGlyph(icon, GloshColors.Graphite, Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
        }
        if (trailing != null) {
            Box(
                modifier =
                    Modifier
                        .background(if (warning) GloshColors.WarningSoft else GloshColors.PositiveSoft, GloshShapes.Pill)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (warning) GloshColors.Warning else GloshColors.Positive,
                )
            }
        } else {
            ProductGlyph(ProductIcon.ChevronRight, GloshColors.Muted, Modifier.size(21.dp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GloshColors.Line))
}

@Composable
private fun AdminFlatInfoRow(
    label: String,
    value: String,
    warning: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = GloshColors.Muted)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (warning) GloshColors.Warning else GloshColors.Graphite,
        )
    }
}

private fun LicenseState.isOperational(): Boolean =
    this == LicenseState.Active || this == LicenseState.ExpiringSoon || this == LicenseState.GracePeriod

private fun LicenseState.adminLabel(): String =
    when (this) {
        LicenseState.Active -> "Licencia activa"
        LicenseState.Scheduled -> "Licencia programada"
        LicenseState.ExpiringSoon -> "Licencia por vencer"
        LicenseState.PendingActivation -> "Activación pendiente"
        LicenseState.Expired -> "Licencia vencida"
        LicenseState.GracePeriod -> "Período de gracia"
        LicenseState.Suspended -> "Licencia suspendida"
    }
