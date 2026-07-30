package com.contentfilter.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.alerts.AdminAlertsRoute
import com.contentfilter.admin.announcements.AdminAnnouncementsRoute
import com.contentfilter.admin.auth.AdminAuthRoute
import com.contentfilter.admin.dashboard.DashboardRoute
import com.contentfilter.admin.push.AdminProtectionAlertPayload
import com.contentfilter.admin.push.AdminPushViewModel
import com.contentfilter.admin.requests.AdminRequestsRoute
import com.contentfilter.admin.rules.RulesEntryMode
import com.contentfilter.admin.rules.RulesRoute
import com.contentfilter.admin.updates.AdminLocalAccessRoute
import com.contentfilter.admin.updates.AdminUpdatesRoute
import com.contentfilter.admin.updates.AdminUpdatesStatus
import com.contentfilter.admin.updates.AdminUpdatesViewModel
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductNavGlyph
import kotlinx.coroutines.launch

@Composable
internal fun AdminAppRoot(
    modifier: Modifier = Modifier,
    protectionAlertPayload: AdminProtectionAlertPayload? = null,
    announcementOpenRequest: Int = 0,
    onProtectionAlertConsumed: () -> Unit = {},
    onAnnouncementOpenConsumed: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.Home) }
    var section by rememberSaveable { mutableStateOf<AdminSection?>(null) }
    var requestedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var createUserRequestKey by rememberSaveable { mutableStateOf(0) }
    var showUserListRequestKey by rememberSaveable { mutableStateOf(0) }
    var requestsRefreshKey by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val rootViewModel: AdminRootViewModel = hiltViewModel()
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val pushViewModel: AdminPushViewModel = hiltViewModel()
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pushViewModel.registerIfReady()
        }
    val updatesViewModel: AdminUpdatesViewModel = hiltViewModel()
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    AdminSystemBars(
        darkHeader =
            !rootState.loading &&
                rootState.activated &&
                tab == AdminTab.Home &&
                section == null,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatesViewModel.resumePendingInstallAfterPermission()
    }
    LaunchedEffect(rootState.activated) {
        if (rootState.activated) {
            updatesViewModel.autoCheckAndDownload()
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pushViewModel.registerIfReady()
            }
        }
    }
    LaunchedEffect(rootState.activated, protectionAlertPayload?.eventId) {
        if (rootState.activated && protectionAlertPayload != null) {
            tab = AdminTab.Home
            section = AdminSection.Alerts
            onProtectionAlertConsumed()
        }
    }
    LaunchedEffect(rootState.activated, announcementOpenRequest) {
        if (rootState.activated && announcementOpenRequest > 0) {
            tab = AdminTab.Home
            section = AdminSection.Announcements
            onAnnouncementOpenConsumed()
        }
    }
    LaunchedEffect(rootState.activated) {
        if (!rootState.activated) {
            tab = AdminTab.Home
            section = null
        }
    }
    if (rootState.loading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!rootState.activated) {
        Box(modifier = modifier) {
            AdminAuthRoute()
        }
        return
    }
    BackHandler(enabled = section != null) {
        section = null
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor =
            if (tab == AdminTab.Home && section == null) {
                AdminHomeStatusBarColor
            } else {
                Color.White
            },
        bottomBar = {
            NavigationBar {
                AdminTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item && section == null,
                        onClick = {
                            if (item == AdminTab.Users) {
                                requestedUserId = null
                                showUserListRequestKey += 1
                            }
                            tab = item
                            section = null
                        },
                        icon = { ProductNavGlyph(icon = item.icon, selected = tab == item && section == null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val currentSection = section) {
                AdminSection.Panel ->
                    SectionContainer(
                        title = "Panel administrador",
                        subtitle = "Estado general, comunidad y sincronización",
                        onBack = { section = null },
                    ) {
                        DashboardRoute()
                    }
                AdminSection.AccountDetails ->
                    SectionContainer(
                        title = "Cuenta y comunidad",
                        subtitle = "Identidad, licencia y sincronización",
                        onBack = { section = null },
                    ) {
                        AdminAccountDetailsScreen()
                    }
                AdminSection.Contact ->
                    SectionContainer(
                        title = "Contacto adulto",
                        subtitle = "Número asociado a la comunidad",
                        onBack = { section = null },
                    ) {
                        AdminContactSettingsRoute()
                    }
                AdminSection.Feedback ->
                    SectionContainer(
                        title = "Tu opinión",
                        subtitle = "Ayudanos a mejorar App Administrador",
                        onBack = { section = null },
                    ) {
                        AdminFeedbackSettingsRoute()
                    }
                AdminSection.LocalAdmin ->
                    SectionContainer(
                        title = "Administrador de este teléfono",
                        subtitle = "Acceso local y cambio de administrador",
                        onBack = { section = null },
                    ) {
                        AdminLocalAccessRoute()
                    }
                AdminSection.Apps -> RulesRoute(entryMode = RulesEntryMode.Apps, onBack = { section = null })
                AdminSection.Web -> RulesRoute(entryMode = RulesEntryMode.Web, onBack = { section = null })
                AdminSection.ManageUsers ->
                    RulesRoute(
                        entryMode = RulesEntryMode.ManageUsers,
                        onBack = { section = null },
                    )
                AdminSection.Requests ->
                    AdminRequestsRoute(
                        refreshKey = requestsRefreshKey,
                        onBack = { section = null },
                    )
                AdminSection.Alerts ->
                    SectionContainer(
                        title = "Alertas de seguridad",
                        subtitle = "Desactivaciones, posibles desinstalaciones y mantenimiento",
                        onBack = { section = null },
                    ) {
                        AdminAlertsRoute()
                    }
                AdminSection.ProtectionStatus ->
                    SectionContainer(
                        title = "Estado de protección",
                        subtitle = "Usuarios que requieren atención",
                        onBack = { section = null },
                    ) {
                        ProtectionStatusRoute(
                            onOpenUser = { deviceId ->
                                requestedUserId = deviceId
                                section = null
                                tab = AdminTab.Users
                            },
                            onOpenAlerts = { section = AdminSection.Alerts },
                        )
                    }
                AdminSection.Announcements ->
                    SectionContainer(
                        title = "Avisos",
                        subtitle = "Mensajes de tu comunidad",
                        onBack = { section = null },
                    ) {
                        AdminAnnouncementsRoute()
                    }
                AdminSection.Updates ->
                    SectionContainer(
                        title = "Actualizaciones",
                        subtitle = "Versiones y administrador local",
                        onBack = { section = null },
                    ) {
                        AdminUpdatesRoute()
                    }
                AdminSection.Help ->
                    AdminHelpRoute(
                        onBack = { section = null },
                        onAction = { action ->
                            section = null
                            tab =
                                when (action) {
                                    HelpAction.Apps,
                                    HelpAction.Web,
                                    HelpAction.Security,
                                    HelpAction.Recovery,
                                    -> AdminTab.Users
                                    HelpAction.Settings -> AdminTab.Account
                                }
                        },
                    )
                null ->
                    when (tab) {
                        AdminTab.Home ->
                            HomeTab(
                                onCreateUser = {
                                    requestedUserId = null
                                    showUserListRequestKey += 1
                                    createUserRequestKey += 1
                                    tab = AdminTab.Users
                                },
                                onRequests = {
                                    requestsRefreshKey += 1
                                    tab = AdminTab.Requests
                                },
                                onProtectionStatus = { section = AdminSection.ProtectionStatus },
                                onAnnouncements = { section = AdminSection.Announcements },
                            )
                        AdminTab.Users ->
                            RulesRoute(
                                entryMode = RulesEntryMode.ManageUsers,
                                initialDeviceId = requestedUserId,
                                onInitialDeviceConsumed = { requestedUserId = null },
                                showUserListRequestKey = showUserListRequestKey,
                                createUserRequestKey = createUserRequestKey,
                                onCreateUserRequestConsumed = { createUserRequestKey = 0 },
                            )
                        AdminTab.Requests ->
                            AdminRequestsRoute(refreshKey = requestsRefreshKey)
                        AdminTab.Account ->
                            SettingsTab(
                                onAccount = { section = AdminSection.AccountDetails },
                                onContact = { section = AdminSection.Contact },
                                onPanel = { section = AdminSection.Panel },
                                onUpdates = { section = AdminSection.Updates },
                                onHelp = { section = AdminSection.Help },
                                onFeedback = { section = AdminSection.Feedback },
                                onLocalAdmin = { section = AdminSection.LocalAdmin },
                            )
                    }
            }
        }
    }
    if (updateState.status == AdminUpdatesStatus.NeedsInstallPermission) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Actualización lista") },
            text = { Text("Android necesita permiso para instalar APKs desde esta app.") },
            confirmButton = {
                Button(onClick = updatesViewModel::openInstallPermissionSettings) {
                    Text("Dar permiso")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    tab = AdminTab.Account
                    section = AdminSection.Updates
                }) {
                    Text("Ver")
                }
            },
        )
    } else if (updateState.status == AdminUpdatesStatus.ReadyToInstall) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Actualización descargada") },
            text = { Text("Confirma la instalación en Android para completar la actualización.") },
            confirmButton = {
                Button(onClick = updatesViewModel::installDownloadedUpdate) {
                    Text("Instalar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    tab = AdminTab.Account
                    section = AdminSection.Updates
                }) {
                    Text("Ver")
                }
            },
        )
    }
}

private enum class AdminTab(
    val label: String,
    val icon: ProductIcon,
) {
    Home("Home", ProductIcon.Home),
    Users("Usuarios", ProductIcon.People),
    Requests("Solicitudes", ProductIcon.Requests),
    Account("Ajustes", ProductIcon.Settings),
}

private enum class AdminSection {
    Panel,
    AccountDetails,
    Contact,
    Feedback,
    LocalAdmin,
    Apps,
    Web,
    ManageUsers,
    Requests,
    Alerts,
    ProtectionStatus,
    Announcements,
    Updates,
    Help,
}
