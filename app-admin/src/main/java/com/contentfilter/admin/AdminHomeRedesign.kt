package com.contentfilter.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.announcements.AdminAnnouncementsViewModel
import com.contentfilter.admin.dashboard.DashboardViewModel
import com.contentfilter.admin.dashboard.ProtectedUserHealthUiState
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.GloshShapes
import com.contentfilter.core.ui.GloshSpacing
import com.contentfilter.core.ui.ProductGlyph
import com.contentfilter.core.ui.ProductIcon

/**
 * Nuevo Home Admin. Vive separado del Home anterior mientras la dirección visual
 * esté en revisión, para poder volver atrás sin reconstruir la pantalla previa.
 */
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GloshColors.Bone),
    ) {
        GloshAdminHeader(
            administratorName = dashboardState.guideName.ifBlank { "Administrador" },
            communityName = dashboardState.communityName,
            licenseState = dashboardState.licenseState,
            licenseExpiresAtEpochMillis = dashboardState.licenseExpiresAtEpochMillis,
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
                        top = 8.dp,
                        end = GloshSpacing.PageHorizontal,
                        bottom = 28.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(GloshSpacing.Section),
        ) {
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.labelLarge,
                color = GloshColors.Muted,
            )

            GloshProtectionCard(
                users = dashboardState.protectedUsers,
                licenseState = dashboardState.licenseState,
                onClick = onProtectionStatus,
            )

            GloshActionCard(
                title = "Agregar usuario",
                subtitle = "Crear y vincular un nuevo usuario",
                onClick = onCreateUser,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GloshMetricCard(
                    value = dashboardState.activeUserCount.toString(),
                    label = "Usuarios activos",
                    modifier = Modifier.weight(1f),
                )
                GloshMetricCard(
                    value = dashboardState.pendingRequests.toString(),
                    label = "Solicitudes",
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onRequests),
                    emphasized = dashboardState.pendingRequests > 0,
                )
            }

            if (dashboardState.pendingRequests > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequests,
                    shape = GloshShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = GloshColors.LimeSoft),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProductGlyph(
                            icon = ProductIcon.Requests,
                            color = GloshColors.Graphite,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hay solicitudes esperando",
                                style = MaterialTheme.typography.titleSmall,
                                color = GloshColors.Graphite,
                            )
                            Text(
                                text = "Revisalas desde Solicitudes",
                                style = MaterialTheme.typography.bodySmall,
                                color = GloshColors.GraphiteSoft,
                            )
                        }
                        ProductGlyph(
                            icon = ProductIcon.ChevronRight,
                            color = GloshColors.Graphite,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GloshAdminHeader(
    administratorName: String,
    communityName: String,
    licenseState: LicenseState,
    licenseExpiresAtEpochMillis: Long?,
    announcementCount: Int,
    onAnnouncements: () -> Unit,
) {
    val licenseAccent = licenseAccent(licenseState)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GloshColors.Bone)
                .statusBarsPadding()
                .padding(
                    start = GloshSpacing.PageHorizontal,
                    top = 12.dp,
                    end = GloshSpacing.PageHorizontal,
                    bottom = 20.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "glosh",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.1).sp,
                color = GloshColors.Graphite,
            )
            Box {
                IconButton(
                    onClick = onAnnouncements,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(GloshColors.Surface, CircleShape)
                            .semantics { contentDescription = "Abrir avisos" },
                ) {
                    ProductGlyph(
                        icon = ProductIcon.Bell,
                        color = GloshColors.Graphite,
                        modifier = Modifier.size(23.dp),
                    )
                }
                if (announcementCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .background(GloshColors.Lime, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = announcementCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = GloshColors.Graphite,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Hola, $administratorName",
                style = MaterialTheme.typography.headlineSmall,
                color = GloshColors.Graphite,
            )
            if (communityName.isNotBlank()) {
                Text(
                    text = communityName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Muted,
                )
            }
        }

        Text(
            modifier =
                Modifier
                    .background(licenseAccent.copy(alpha = 0.10f), GloshShapes.Pill)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            text = licenseSummary(licenseState, licenseExpiresAtEpochMillis),
            style = MaterialTheme.typography.labelMedium,
            color = licenseAccent,
        )
    }
}

@Composable
private fun GloshProtectionCard(
    users: List<ProtectedUserHealthUiState>,
    licenseState: LicenseState,
    onClick: () -> Unit,
) {
    val affectedCount = users.count(ProtectedUserHealthUiState::hasConfirmedProblem)
    val pendingCount = users.count(ProtectedUserHealthUiState::requiresVerification)
    val criticalCount = users.count(ProtectedUserHealthUiState::possibleUninstall)
    val cardState =
        protectionCardState(
            licenseState = licenseState,
            userCount = users.size,
            affectedCount = affectedCount,
            pendingCount = pendingCount,
            criticalCount = criticalCount,
        )
    val accent = protectionAccent(cardState)
    val needsAttention = cardState != ProtectionCardState.Healthy

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(
                    icon = if (needsAttention) ProductIcon.ShieldAlert else ProductIcon.ShieldCheck,
                    color = accent,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = protectionTitle(cardState),
                    style = MaterialTheme.typography.titleLarge,
                    color = GloshColors.Graphite,
                )
                Text(
                    text = cardState.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GloshColors.Muted,
                )
            }
            ProductGlyph(
                icon = ProductIcon.ChevronRight,
                color = GloshColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun GloshActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = GloshShapes.Card,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(GloshColors.LimeSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(
                    icon = ProductIcon.Person,
                    color = GloshColors.Graphite,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GloshColors.Graphite)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GloshColors.Muted)
            }
            ProductGlyph(
                icon = ProductIcon.ChevronRight,
                color = GloshColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun GloshMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .background(
                    color = if (emphasized) GloshColors.LimeSoft else GloshColors.Surface,
                    shape = GloshShapes.Card,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = GloshColors.Graphite,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GloshColors.Muted,
        )
    }
}

private fun protectionTitle(state: ProtectionCardState): String =
    when (state) {
        ProtectionCardState.Healthy -> "Protección activa"
        ProtectionCardState.Critical -> "Atención inmediata"
        ProtectionCardState.NeedsAttention -> "Requiere atención"
        ProtectionCardState.PendingVerification -> "Verificando protección"
        ProtectionCardState.LicenseBlocked -> "Protección suspendida"
        ProtectionCardState.NoUsers -> "Empezá agregando un usuario"
    }

private fun protectionAccent(state: ProtectionCardState): Color =
    when (state) {
        ProtectionCardState.Healthy -> GloshColors.Positive
        ProtectionCardState.PendingVerification,
        ProtectionCardState.NoUsers,
        -> GloshColors.Warning
        ProtectionCardState.Critical,
        ProtectionCardState.NeedsAttention,
        ProtectionCardState.LicenseBlocked,
        -> GloshColors.Danger
    }

private fun licenseAccent(state: LicenseState): Color =
    when (state) {
        LicenseState.Active -> GloshColors.Positive
        LicenseState.ExpiringSoon,
        LicenseState.GracePeriod,
        LicenseState.Scheduled,
        -> GloshColors.Warning
        LicenseState.Expired,
        LicenseState.Suspended,
        LicenseState.PendingActivation,
        -> GloshColors.Danger
    }
