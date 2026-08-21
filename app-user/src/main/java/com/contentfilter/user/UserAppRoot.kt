package com.contentfilter.user

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.ui.GloshColors
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductNavGlyph
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.announcements.UserAnnouncementsRoute
import com.contentfilter.user.apps.MyAppsRoute
import com.contentfilter.user.browser.ProtectedBrowserLauncher
import com.contentfilter.user.protection.BatteryOptimizationController
import com.contentfilter.user.protection.ProtectionViewModel
import com.contentfilter.user.push.UserPushViewModel
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesViewModel
import com.contentfilter.user.updates.settingsSummary

@Composable
internal fun UserAppRoot(
    modifier: Modifier = Modifier,
    announcementOpenRequest: Int = 0,
    onAnnouncementOpenConsumed: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(UserDestination.Home) }
    var backStack by rememberSaveable { mutableStateOf<List<UserDestination>>(emptyList()) }
    var showAccessibilityDialog by rememberSaveable { mutableStateOf(false) }
    var accessibilityReminderDeferred by remember { mutableStateOf(false) }
    var showVpnDialog by rememberSaveable { mutableStateOf(false) }
    var showDeviceAdminDialog by rememberSaveable { mutableStateOf(false) }
    var showBatteryOptimizationDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var batteryOptimizationExempt by rememberSaveable {
        mutableStateOf(BatteryOptimizationController.isExempt(context))
    }

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (VpnController.prepareIntent(context) == null) VpnController.start(context)
        }
    val deviceAdminLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            showDeviceAdminDialog = false
        }
    val batteryOptimizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            batteryOptimizationExempt = BatteryOptimizationController.isExempt(context)
            showBatteryOptimizationDialog = false
        }

    val rootViewModel: UserRootViewModel = hiltViewModel()
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val updatesViewModel: UpdatesViewModel = hiltViewModel()
    val pushViewModel: UserPushViewModel = hiltViewModel()
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pushViewModel.registerIfReady()
        }
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()

    UserSystemBars(
        darkHeader =
            !rootState.checkingActivation &&
                !rootState.needsActivation &&
                destination == UserDestination.Home,
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatesViewModel.resumePendingInstallAfterPermission()
    }
    LaunchedEffect(Unit) { updatesViewModel.autoCheckAndDownload() }
    LaunchedEffect(announcementOpenRequest) {
        if (announcementOpenRequest > 0) {
            backStack = listOf(UserDestination.Home)
            destination = UserDestination.Announcements
            onAnnouncementOpenConsumed()
        }
    }
    LaunchedEffect(rootState.needsActivation) {
        if (!rootState.needsActivation) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pushViewModel.registerIfReady()
            }
        }
    }
    LaunchedEffect(destination) {
        showAccessibilityDialog =
            !accessibilityReminderDeferred &&
                !AccessibilityController.isEnabled(context)
    }
    LaunchedEffect(rootState.needsActivation) {
        if (!rootState.needsActivation) {
            val permissionIntent = VpnController.prepareIntent(context)
            if (permissionIntent == null) {
                VpnController.start(context)
            } else {
                showVpnDialog = true
            }
        }
    }
    LaunchedEffect(rootState.recentlyActivated) {
        if (rootState.recentlyActivated && !DeviceAdminController.isEnabled(context)) {
            showDeviceAdminDialog = true
        }
    }
    LaunchedEffect(rootState.needsActivation) {
        if (!rootState.needsActivation && BatteryOptimizationController.shouldPrompt(context)) {
            BatteryOptimizationController.markPromptShown(context)
            showBatteryOptimizationDialog = true
        }
    }

    if (rootState.checkingActivation) {
        Box(modifier = modifier.padding(24.dp)) {
            Text("Preparando Glosh…", style = MaterialTheme.typography.bodyLarge, color = GloshColors.Graphite)
        }
        return
    }
    if (rootState.needsActivation) {
        ActivationRoute(modifier = modifier, notice = rootState.activationNotice)
        return
    }

    fun navigateTo(target: UserDestination) {
        if (target == destination) return
        backStack = backStack + destination
        destination = target
    }

    fun selectTopLevel(target: UserDestination) {
        if (target == destination) return
        backStack = emptyList()
        destination = target
    }

    fun openSettingsSection(target: UserDestination) {
        backStack = listOf(UserDestination.Settings)
        destination = target
    }

    fun goBack() {
        val previous = backStack.lastOrNull() ?: UserDestination.Home
        backStack = backStack.dropLast(1)
        destination = previous
    }

    BackHandler(enabled = destination != UserDestination.Home) { goBack() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = GloshColors.Bone,
        bottomBar = {
            NavigationBar(
                containerColor = GloshColors.Bone,
                tonalElevation = 0.dp,
            ) {
                UserDestination.entries.filter { it.showInNav }.forEach { item ->
                    NavigationBarItem(
                        selected = destination.topLevel() == item,
                        onClick = { selectTopLevel(item) },
                        icon = {
                            ProductNavGlyph(
                                icon = item.icon,
                                selected = destination.topLevel() == item,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                UserDestination.Home ->
                    UserHomeRoute(
                        onRequests = { navigateTo(UserDestination.Requests) },
                        onAnnouncements = { navigateTo(UserDestination.Announcements) },
                        onMyApps = { selectTopLevel(UserDestination.MyApps) },
                        updateState = updateState,
                        onUpdateNow = updatesViewModel::downloadUpdate,
                        onActivateVpn = {
                            val permissionIntent = VpnController.prepareIntent(context)
                            if (permissionIntent == null) {
                                VpnController.start(context)
                            } else {
                                vpnPermissionLauncher.launch(permissionIntent)
                            }
                        },
                        onActivateAccessibility = {
                            accessibilityReminderDeferred = true
                            AccessibilityController.openSettings(context)
                        },
                        onActivateDeviceAdmin = {
                            deviceAdminLauncher.launch(DeviceAdminController.activationIntent(context))
                        },
                        onOpenSettings = { selectTopLevel(UserDestination.Settings) },
                    )

                UserDestination.MyApps -> MyAppsRoute()
                UserDestination.Requests -> RequestsRoute(onBack = ::goBack)
                UserDestination.Announcements -> UserAnnouncementsRoute(onBack = ::goBack)

                UserDestination.Web -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    UserWebTab(
                        onBack = null,
                        onOpenProtectedBrowser = { ProtectedBrowserLauncher.open(context) },
                        onInstallProtectedBrowser = {
                            updatesViewModel.prepareDagInstall()
                            openSettingsSection(UserDestination.Updates)
                        },
                        protectedBrowserAvailable =
                            BuildConfig.DAG_BROWSER_V3_BRIDGE_AVAILABLE &&
                                ProtectedBrowserLauncher.isInstalled(context),
                        vpnActive = statusState.isVpnActive,
                        onActivateWebProtection = {
                            val permissionIntent = VpnController.prepareIntent(context)
                            if (permissionIntent == null) {
                                VpnController.start(context)
                            } else {
                                vpnPermissionLauncher.launch(permissionIntent)
                            }
                        },
                    )
                }

                UserDestination.Settings -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    UserSettingsTab(
                        activationSummary = statusState.activationState,
                        updateSummary = updateState.status.settingsSummary(),
                        onProtection = { navigateTo(UserDestination.ProtectionSettings) },
                        onUpdates = { navigateTo(UserDestination.Updates) },
                        onContact = { navigateTo(UserDestination.ContactSettings) },
                        onHelp = { navigateTo(UserDestination.Help) },
                        onFeedback = { navigateTo(UserDestination.FeedbackSettings) },
                    )
                }

                UserDestination.ProtectionSettings -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    val protectionViewModel: ProtectionViewModel = hiltViewModel()
                    val protectionState by protectionViewModel.uiState.collectAsStateWithLifecycle()
                    UserProtectionSettingsScreen(
                        activationState = statusState.activationState,
                        recoveryCode = protectionState.recoveryCode,
                        protectionMessage = protectionState.message,
                        onRecoveryCodeChanged = protectionViewModel::onRecoveryCodeChanged,
                        onSubmitRecoveryCode = protectionViewModel::submitRecoveryCode,
                        onBack = ::goBack,
                    )
                }

                UserDestination.Updates ->
                    UpdatesRoute(viewModel = updatesViewModel, onBack = ::goBack)

                UserDestination.FeedbackSettings -> UserFeedbackSettingsRoute(onBack = ::goBack)
                UserDestination.ContactSettings -> UserContactSettingsRoute(onBack = ::goBack)

                UserDestination.Help ->
                    UserHelpRoute(
                        onBack = ::goBack,
                        onAction = { action ->
                            val target =
                                when (action) {
                                    HelpAction.Apps -> UserDestination.MyApps
                                    HelpAction.Web -> UserDestination.Web
                                    HelpAction.Security -> UserDestination.Home
                                    HelpAction.Recovery -> UserDestination.ProtectionSettings
                                    HelpAction.Settings -> UserDestination.Settings
                                }
                            if (target == UserDestination.ProtectionSettings) {
                                openSettingsSection(target)
                            } else {
                                selectTopLevel(target)
                            }
                        },
                    )
            }
        }
    }

    when {
        updateState.status == UpdatesStatus.NeedsInstallPermission ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Actualización lista") },
                text = { Text("Android necesita permiso para completar la instalación de Glosh.") },
                confirmButton = {
                    Button(onClick = updatesViewModel::openInstallPermissionSettings) { Text("Dar permiso") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { openSettingsSection(UserDestination.Updates) }) { Text("Ver") }
                },
            )

        updateState.status == UpdatesStatus.ReadyToInstall ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Actualización descargada") },
                text = { Text("Confirmá la instalación en Android para terminar de actualizar Glosh.") },
                confirmButton = {
                    Button(onClick = updatesViewModel::installDownloadedUpdate) { Text("Instalar") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { openSettingsSection(UserDestination.Updates) }) { Text("Ver") }
                },
            )

        showDeviceAdminDialog ->
            ProtectionSetupDialog(
                title = "Protección contra desinstalación",
                text = "Este permiso de Android ayuda a evitar que Glosh se quite sin autorización.",
                actionLabel = "Activar protección",
                onAction = {
                    deviceAdminLauncher.launch(DeviceAdminController.activationIntent(context))
                },
                onLater = { showDeviceAdminDialog = false },
            )

        showAccessibilityDialog ->
            ProtectionSetupDialog(
                title = "Protección de apps",
                text = "Glosh necesita este permiso para aplicar los bloqueos y límites de apps. En la pantalla de Android, buscá esta app y activá “Usar servicio”.",
                actionLabel = "Continuar",
                onAction = {
                    accessibilityReminderDeferred = true
                    showAccessibilityDialog = false
                    AccessibilityController.openSettings(context)
                },
                onLater = {
                    accessibilityReminderDeferred = true
                    showAccessibilityDialog = false
                },
            )

        showVpnDialog && !VpnController.isRunning(context) ->
            ProtectionSetupDialog(
                title = "Protección de Internet",
                text = "Android va a pedir confirmación para crear la conexión protegida que Glosh usa al navegar.",
                actionLabel = "Activar protección",
                onAction = {
                    showVpnDialog = false
                    val permissionIntent = VpnController.prepareIntent(context)
                    if (permissionIntent == null) {
                        VpnController.start(context)
                    } else {
                        vpnPermissionLauncher.launch(permissionIntent)
                    }
                },
                onLater = { showVpnDialog = false },
            )

        showBatteryOptimizationDialog && !batteryOptimizationExempt ->
            ProtectionSetupDialog(
                title = "Funcionamiento continuo",
                text = "Permití que Glosh siga funcionando en segundo plano para que Android no pause la protección.",
                actionLabel = "Permitir",
                onAction = {
                    batteryOptimizationLauncher.launch(BatteryOptimizationController.requestIntent(context))
                },
                onLater = { showBatteryOptimizationDialog = false },
            )
    }
}

@Composable
private fun ProtectionSetupDialog(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(title) },
        text = { Text(text, color = GloshColors.Muted) },
        confirmButton = {
            Button(onClick = onAction) { Text(actionLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onLater) { Text("Más tarde") }
        },
    )
}

private enum class UserDestination(
    val label: String,
    val icon: ProductIcon,
    val showInNav: Boolean = true,
) {
    Home("Inicio", ProductIcon.Home),
    MyApps("Mis apps", ProductIcon.Apps),
    Web("Internet", ProductIcon.Web),
    Requests("Solicitudes", ProductIcon.Requests, showInNav = false),
    Announcements("Avisos", ProductIcon.Bell, showInNav = false),
    Settings("Ajustes", ProductIcon.Settings),
    ProtectionSettings("Protección", ProductIcon.ShieldCheck, showInNav = false),
    Updates("Actualizaciones", ProductIcon.Update, showInNav = false),
    ContactSettings("Contacto", ProductIcon.People, showInNav = false),
    FeedbackSettings("Tu opinión", ProductIcon.Star, showInNav = false),
    Help("Ayuda", ProductIcon.Search, showInNav = false),
}

private fun UserDestination.topLevel(): UserDestination =
    when (this) {
        UserDestination.Requests,
        UserDestination.Announcements,
        -> UserDestination.Home

        UserDestination.ProtectionSettings,
        UserDestination.Updates,
        UserDestination.ContactSettings,
        UserDestination.FeedbackSettings,
        UserDestination.Help,
        -> UserDestination.Settings

        else -> this
    }
