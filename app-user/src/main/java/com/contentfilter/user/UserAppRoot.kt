package com.contentfilter.user

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import com.contentfilter.user.dag.DagActivity
import com.contentfilter.user.dag.DagBrowserRoute
import com.contentfilter.user.protection.BatteryOptimizationController
import com.contentfilter.user.protection.ProtectionViewModel
import com.contentfilter.user.push.UserPushViewModel
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesViewModel
import kotlinx.coroutines.launch

@Composable
internal fun UserAppRoot(
    modifier: Modifier = Modifier,
    dagLaunchRequest: Int = 0,
    announcementOpenRequest: Int = 0,
    onDagLaunchConsumed: () -> Unit = {},
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
            if (VpnController.prepareIntent(context) == null) {
                VpnController.start(context)
            }
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatesViewModel.resumePendingInstallAfterPermission()
    }
    LaunchedEffect(Unit) {
        updatesViewModel.autoCheckAndDownload()
    }
    LaunchedEffect(dagLaunchRequest) {
        if (dagLaunchRequest > 0) {
            backStack = listOf(UserDestination.Web)
            destination = UserDestination.Dag
            onDagLaunchConsumed()
        }
    }
    LaunchedEffect(announcementOpenRequest) {
        if (announcementOpenRequest > 0) {
            backStack = listOf(UserDestination.Home)
            destination = UserDestination.Announcements
            onAnnouncementOpenConsumed()
        }
    }
    LaunchedEffect(rootState.needsActivation) {
        if (!rootState.needsActivation) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
            Text("Revisando enlace...", style = MaterialTheme.typography.bodyLarge)
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

    fun goBack() {
        val previous = backStack.lastOrNull() ?: UserDestination.Home
        backStack = backStack.dropLast(1)
        destination = previous
    }
    BackHandler(enabled = destination != UserDestination.Home) {
        goBack()
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                UserDestination.entries.filter { it.showInNav }.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { selectTopLevel(item) },
                        icon = { ProductNavGlyph(icon = item.icon, selected = destination == item) },
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
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onActivateDeviceAdmin = {
                            deviceAdminLauncher.launch(DeviceAdminController.activationIntent(context))
                        },
                        onOpenSettings = { selectTopLevel(UserDestination.Updates) },
                    )
                UserDestination.MyApps -> MyAppsRoute()
                UserDestination.Requests -> RequestsRoute(onBack = ::goBack)
                UserDestination.Announcements -> UserAnnouncementsRoute(onBack = ::goBack)
                UserDestination.Web -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    UserWebTab(
                        onBack = null,
                        onOpenDag = { DagActivity.open(context) },
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
                UserDestination.Dag -> DagBrowserRoute(onBack = ::goBack)
                UserDestination.Updates -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    val protectionViewModel: ProtectionViewModel = hiltViewModel()
                    val protectionState by protectionViewModel.uiState.collectAsStateWithLifecycle()
                    UpdatesRoute(
                        onBack = null,
                        onHelp = { navigateTo(UserDestination.Help) },
                        activationState = statusState.activationState,
                        recoveryCode = protectionState.recoveryCode,
                        protectionMessage = protectionState.message,
                        onRecoveryCodeChanged = protectionViewModel::onRecoveryCodeChanged,
                        onSubmitRecoveryCode = protectionViewModel::submitRecoveryCode,
                    )
                }
                UserDestination.Help ->
                    UserHelpRoute(
                        onBack = ::goBack,
                        onAction = { action ->
                            selectTopLevel(
                                when (action) {
                                    HelpAction.Apps -> UserDestination.MyApps
                                    HelpAction.Web -> UserDestination.Web
                                    HelpAction.Security -> UserDestination.Home
                                    HelpAction.Recovery,
                                    HelpAction.Settings,
                                    -> UserDestination.Updates
                                },
                            )
                        },
                    )
            }
        }
    }
    if (updateState.status == UpdatesStatus.NeedsInstallPermission) {
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
                OutlinedButton(onClick = { navigateTo(UserDestination.Updates) }) {
                    Text("Ver")
                }
            },
        )
    } else if (updateState.status == UpdatesStatus.ReadyToInstall) {
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
                OutlinedButton(onClick = { navigateTo(UserDestination.Updates) }) {
                    Text("Ver")
                }
            },
        )
    } else if (showDeviceAdminDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceAdminDialog = false },
            title = { Text("Protección contra desinstalación") },
            text = {
                Text(
                    "Activá la protección de Android para que Content Filter no pueda quitarse sin una confirmación adicional.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deviceAdminLauncher.launch(DeviceAdminController.activationIntent(context))
                    },
                ) {
                    Text("Activar protección")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeviceAdminDialog = false }) {
                    Text("Luego")
                }
            },
        )
    } else if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = {
                accessibilityReminderDeferred = true
                showAccessibilityDialog = false
            },
            title = { Text("Accesibilidad apagada") },
            text = {
                Text(
                    "El bloqueo de apps necesita el servicio de accesibilidad activo. Android puede desactivarlo después de una actualización.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        accessibilityReminderDeferred = true
                        showAccessibilityDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        accessibilityReminderDeferred = true
                        showAccessibilityDialog = false
                    },
                ) {
                    Text("Luego")
                }
            },
        )
    } else if (showVpnDialog && !VpnController.isRunning(context)) {
        AlertDialog(
            onDismissRequest = { showVpnDialog = false },
            title = { Text("Protección web apagada") },
            text = { Text("La protección web necesita la VPN activa.") },
            confirmButton = {
                Button(
                    onClick = {
                        showVpnDialog = false
                        val permissionIntent = VpnController.prepareIntent(context)
                        if (permissionIntent == null) {
                            VpnController.start(context)
                        } else {
                            vpnPermissionLauncher.launch(permissionIntent)
                        }
                    },
                ) {
                    Text("Activar protección web")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showVpnDialog = false }) {
                    Text("Luego")
                }
            },
        )
    } else if (showBatteryOptimizationDialog && !batteryOptimizationExempt) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            title = { Text("Funcionamiento continuo") },
            text = {
                Text(
                    "Permití que Content Filter funcione sin optimización de batería para que Android no pause la protección.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        batteryOptimizationLauncher.launch(BatteryOptimizationController.requestIntent(context))
                    },
                ) {
                    Text("Permitir")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatteryOptimizationDialog = false }) {
                    Text("Luego")
                }
            },
        )
    }
}

private enum class UserDestination(
    val label: String,
    val icon: ProductIcon,
    val showInNav: Boolean = true,
) {
    Home("Inicio", ProductIcon.Home),
    MyApps("Mis apps", ProductIcon.Apps),
    Web("Internet", ProductIcon.Web),
    Dag("DAG", ProductIcon.Search, showInNav = false),
    Requests("Solicitudes", ProductIcon.Requests, showInNav = false),
    Announcements("Avisos", ProductIcon.Bell, showInNav = false),
    Updates("Ajustes", ProductIcon.Settings),
    Help("Ayuda", ProductIcon.Search, showInNav = false),
}
