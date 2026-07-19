package com.contentfilter.user

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.contentfilter.core.domain.model.ProtectionLevel
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.core.ui.ProductAppBackground
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductFeatureTile
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductLazyVisualPage
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductNavGlyph
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductSun
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.requests.RequestsViewModel
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.announcements.UserAnnouncementsRoute
import com.contentfilter.user.announcements.UserAnnouncementsViewModel
import com.contentfilter.user.apps.AppIcon
import com.contentfilter.user.apps.MyAppsRoute
import com.contentfilter.user.apps.MyAppsViewModel
import com.contentfilter.user.dag.DagActivity
import com.contentfilter.user.dag.DagBrowserRoute
import com.contentfilter.user.dag.DagShortcutController
import com.contentfilter.user.internet.UserWebViewModel
import com.contentfilter.user.protection.BatteryOptimizationController
import com.contentfilter.user.protection.ProtectionControlCoordinator
import com.contentfilter.user.protection.ProtectionViewModel
import com.contentfilter.user.push.OpenAnnouncementsAction
import com.contentfilter.user.push.UserPushViewModel
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesUiState
import com.contentfilter.user.updates.UpdatesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dagLaunchRequests = MutableStateFlow(0)
    private val announcementOpenRequests = MutableStateFlow(0)

    @javax.inject.Inject
    lateinit var activationRepository: DeviceActivationRepository

    @javax.inject.Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    @javax.inject.Inject
    lateinit var protectionControlCoordinator: ProtectionControlCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(UserHomeHeaderTop.toArgb()))
        recordDagLaunch(intent)
        recordAnnouncementLaunch(intent)
        setContent {
            ContentFilterTheme {
                val dagLaunchRequest by dagLaunchRequests.collectAsStateWithLifecycle()
                val announcementOpenRequest by announcementOpenRequests.collectAsStateWithLifecycle()
                UserAppRoot(
                    modifier = Modifier.fillMaxSize(),
                    dagLaunchRequest = dagLaunchRequest,
                    announcementOpenRequest = announcementOpenRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordDagLaunch(intent)
        recordAnnouncementLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            val activation = activationRepository.currentActivation() ?: return@launch
            targetedPolicySyncCoordinator.refresh(
                deviceId = activation.deviceId,
                reason = "foreground",
            )
            protectionControlCoordinator.refresh()
        }
    }

    private fun recordDagLaunch(intent: Intent?) {
        if (intent?.action == DagShortcutController.OpenDagAction) {
            dagLaunchRequests.value += 1
        }
    }

    private fun recordAnnouncementLaunch(intent: Intent?) {
        if (intent?.action == OpenAnnouncementsAction) announcementOpenRequests.value += 1
    }
}

@Composable
private fun UserAppRoot(
    modifier: Modifier = Modifier,
    dagLaunchRequest: Int = 0,
    announcementOpenRequest: Int = 0,
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
        }
    }
    LaunchedEffect(announcementOpenRequest) {
        if (announcementOpenRequest > 0) {
            backStack = listOf(UserDestination.Home)
            destination = UserDestination.Announcements
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
                        accessibilityActive = statusState.accessibilityState == "Activa",
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
                        protectionSummary = statusState.summary,
                        communityName = statusState.communityName,
                        guideName = statusState.guideName,
                        vpnState = statusState.vpnState,
                        accessibilityState = statusState.accessibilityState,
                        deviceAdminState = statusState.deviceAdminState,
                        syncState = statusState.syncState,
                        activationState = statusState.activationState,
                        batteryOptimizationExempt = batteryOptimizationExempt,
                        protectionArmed = protectionState.armed,
                        settingsAuthorized = protectionState.remoteSettingsAuthorized,
                        removalAuthorized = protectionState.removalAuthorized,
                        recoveryAvailable = protectionState.recoveryAvailable,
                        recoveryCode = protectionState.recoveryCode,
                        protectionMessage = protectionState.message,
                        protectionRefreshing = protectionState.refreshing,
                        onActivateDeviceAdmin = {
                            deviceAdminLauncher.launch(DeviceAdminController.activationIntent(context))
                        },
                        onActivateAccessibility = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onActivateVpn = {
                            val permissionIntent = VpnController.prepareIntent(context)
                            if (permissionIntent == null) {
                                VpnController.start(context)
                            } else {
                                vpnPermissionLauncher.launch(permissionIntent)
                            }
                        },
                        onRequestBatteryOptimizationExemption = {
                            batteryOptimizationLauncher.launch(BatteryOptimizationController.requestIntent(context))
                        },
                        onProtectionRefresh = protectionViewModel::refresh,
                        onRequestMaintenance = protectionViewModel::requestMaintenance,
                        onRecoveryCodeChanged = protectionViewModel::onRecoveryCodeChanged,
                        onSubmitRecoveryCode = protectionViewModel::submitRecoveryCode,
                        onCancelRemovalAuthorization = protectionViewModel::cancelRemovalAuthorization,
                        onAuthorizedRemoval = {
                            if (protectionState.removalAuthorized) {
                                context
                                    .getSystemService(DevicePolicyManager::class.java)
                                    .removeActiveAdmin(DeviceAdminController.component(context))
                                context.startActivity(
                                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        },
                    )
                }
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

@Composable
private fun UserHomeRoute(
    onRequests: () -> Unit,
    onAnnouncements: () -> Unit,
    onMyApps: () -> Unit,
    updateState: UpdatesUiState,
    onUpdateNow: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
    requestsViewModel: RequestsViewModel = hiltViewModel(),
    homeViewModel: UserHomeViewModel = hiltViewModel(),
    statusViewModel: SystemStatusViewModel = hiltViewModel(),
    appsViewModel: MyAppsViewModel = hiltViewModel(),
    announcementsViewModel: UserAnnouncementsViewModel = hiltViewModel(),
) {
    val requestsState by requestsViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val appsState by appsViewModel.uiState.collectAsStateWithLifecycle()
    val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
    val limitItems = remember(appsState) { nearLimitItems(appsState) }
    UserHomeTab(
        greeting = homeState.greeting,
        protectionLevel = statusState.protectionLevel,
        vpnState = statusState.vpnState,
        accessibilityState = statusState.accessibilityState,
        deviceAdminState = statusState.deviceAdminState,
        syncState = statusState.syncState,
        activationState = statusState.activationState,
        announcementCount = announcementsState.items.size,
        updateState = updateState,
        limitItems = limitItems,
        pendingRequests = requestsState.pendingCount,
        onRequests = onRequests,
        onAnnouncements = onAnnouncements,
        onMyApps = onMyApps,
        onUpdateNow = onUpdateNow,
        onActivateVpn = onActivateVpn,
        onActivateAccessibility = onActivateAccessibility,
        onActivateDeviceAdmin = onActivateDeviceAdmin,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun UserHomeTab(
    greeting: String,
    protectionLevel: ProtectionLevel,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    syncState: String,
    activationState: String,
    announcementCount: Int,
    updateState: UpdatesUiState,
    limitItems: List<UserHomeLimitItem>,
    pendingRequests: Int,
    onRequests: () -> Unit,
    onAnnouncements: () -> Unit,
    onMyApps: () -> Unit,
    onUpdateNow: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expandedSection by rememberSaveable { mutableStateOf<UserHomeSection?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(ProductAppBackground)) {
        UserHomeHeader(
            greeting = greeting,
            protectionLevel = protectionLevel,
            vpnState = vpnState,
            accessibilityState = accessibilityState,
            deviceAdminState = deviceAdminState,
            syncState = syncState,
            activationState = activationState,
            announcementCount = announcementCount,
            expanded = expandedSection == UserHomeSection.Protection,
            onToggleProtection = {
                expandedSection =
                    if (expandedSection == UserHomeSection.Protection) null else UserHomeSection.Protection
            },
            onAnnouncements = onAnnouncements,
            onActivateVpn = onActivateVpn,
            onActivateAccessibility = onActivateAccessibility,
            onActivateDeviceAdmin = onActivateDeviceAdmin,
            onOpenSettings = onOpenSettings,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (updateState.shouldShowOnHome) {
                item {
                    UserHomeUpdateCard(
                        state = updateState,
                        onUpdateNow = onUpdateNow,
                    )
                }
            }
            if (limitItems.isNotEmpty()) {
                item {
                    UserHomeLimitsCard(
                        items = limitItems,
                        expanded = expandedSection == UserHomeSection.Limits,
                        onToggle = {
                            expandedSection =
                                if (expandedSection == UserHomeSection.Limits) null else UserHomeSection.Limits
                        },
                        onMyApps = onMyApps,
                    )
                }
            }
            item {
                ProductFeatureTile(
                    icon = ProductIcon.Requests,
                    title = "Solicitudes pendientes",
                    subtitle =
                        if (pendingRequests == 0) {
                            "No hay pedidos pendientes"
                        } else {
                            "$pendingRequests pendientes · tocá para ver"
                        },
                    accent = ProductSun,
                    onClick = onRequests,
                )
            }
        }
    }
}

@Composable
private fun UserHomeHeader(
    greeting: String,
    protectionLevel: ProtectionLevel,
    vpnState: String,
    accessibilityState: String,
    deviceAdminState: String,
    syncState: String,
    activationState: String,
    announcementCount: Int,
    expanded: Boolean,
    onToggleProtection: () -> Unit,
    onAnnouncements: () -> Unit,
    onActivateVpn: () -> Unit,
    onActivateAccessibility: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val statusLabel =
        when (protectionLevel) {
            ProtectionLevel.Protected -> "Protección activa"
            ProtectionLevel.Warning -> "Protección por revisar"
            ProtectionLevel.Unprotected -> "Protección incompleta"
        }
    val statusColor =
        when (protectionLevel) {
            ProtectionLevel.Protected -> Color(0xFF72E6AA)
            ProtectionLevel.Warning -> Color(0xFFFFD166)
            ProtectionLevel.Unprotected -> Color(0xFFFF8A80)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .background(
                    brush = Brush.linearGradient(listOf(UserHomeHeaderTop, UserHomeHeaderBottom)),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                )
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp, end = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                modifier = Modifier.weight(1f).padding(top = 6.dp),
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            UserHomeAnnouncementButton(
                count = announcementCount,
                onClick = onAnnouncements,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleProtection)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
            Text(
                text = if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UserProtectionDetailRow(
                    label = "VPN",
                    state = vpnState,
                    active = vpnState == ActiveStateLabel,
                    help = "Aceptá la conexión VPN para proteger Internet.",
                    actionLabel = "Activar VPN",
                    onAction = onActivateVpn,
                )
                UserProtectionDetailRow(
                    label = "Accesibilidad",
                    state = accessibilityState,
                    active = accessibilityState == ActiveStateLabel,
                    help = "Abrí Accesibilidad, elegí Content Filter y activalo.",
                    actionLabel = "Abrir Accesibilidad",
                    onAction = onActivateAccessibility,
                )
                UserProtectionDetailRow(
                    label = "Desinstalación",
                    state = deviceAdminState,
                    active = deviceAdminState == ActiveStateLabel,
                    help = "Confirmá la protección contra desinstalación de Android.",
                    actionLabel = "Activar protección",
                    onAction = onActivateDeviceAdmin,
                )
                UserProtectionDetailRow(
                    label = "Sincronización",
                    state = syncState,
                    active = syncState == ActiveStateLabel,
                    help = "Revisá la conexión y actualizá el estado desde Ajustes.",
                    actionLabel = "Abrir Ajustes",
                    onAction = onOpenSettings,
                )
                UserProtectionDetailRow(
                    label = "Licencia",
                    state = activationState,
                    active = activationState.isHealthyLicenseState(),
                    help = "El administrador debe revisar el enlace o la licencia.",
                    actionLabel = "Ver estado",
                    onAction = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun UserHomeAnnouncementButton(
    count: Int,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Abrir avisos",
                tint = Color.White,
            )
        }
        if (count > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(Color(0xFFFF5C65), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun UserProtectionDetailRow(
    label: String,
    state: String,
    active: Boolean,
    help: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                text = state,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) ActiveProtectionColor else ReviewProtectionColor,
            )
        }
        if (!active) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = help,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
            ) {
                Text(actionLabel, color = ReviewProtectionColor)
            }
        }
    }
}

@Composable
private fun UserHomeUpdateCard(
    state: UpdatesUiState,
    onUpdateNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Nueva actualización disponible", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.homeUpdateMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4A6075),
            )
            if (state.status == UpdatesStatus.Downloading) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(onClick = onUpdateNow) {
                    Text(if (state.status == UpdatesStatus.Available) "Actualizar ahora" else "Reintentar")
                }
            }
        }
    }
}

@Composable
private fun UserHomeLimitsCard(
    items: List<UserHomeLimitItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMyApps: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cerca del límite", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${items.size} ${if (items.size == 1) "límite requiere" else "límites requieren"} atención",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667085),
                    )
                }
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF44546A),
                )
            }
            UserHomeLimitQueue(items)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.take(MaxExpandedHomeLimits).forEach { item ->
                        UserHomeLimitRow(item)
                    }
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onMyApps,
                    ) {
                        Text("Ver todas en Mis apps")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHomeLimitQueue(items: List<UserHomeLimitItem>) {
    val icons =
        remember(items) {
            items
                .flatMap { item -> item.icons.ifEmpty { listOf(UserHomeAppIcon(item.title, null)) } }
                .distinctBy { "${it.name}:${it.iconBase64}" }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(44.dp)) {
            icons.take(MaxQueueIcons).forEachIndexed { index, icon ->
                Box(
                    modifier =
                        Modifier
                            .offset(x = (index * QueueIconOffset).dp)
                            .zIndex(index.toFloat())
                            .size(40.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .padding(2.dp),
                ) {
                    AppIcon(icon.name, icon.iconBase64, size = 32)
                }
            }
        }
        if (icons.size > MaxQueueIcons) {
            Text(
                text = "+${icons.size - MaxQueueIcons}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF44546A),
            )
        }
    }
}

@Composable
private fun UserHomeLimitRow(item: UserHomeLimitItem) {
    val progressColor =
        when {
            item.progress >= 0.90f -> Color(0xFFC62828)
            item.progress >= 0.80f -> Color(0xFFDA7B00)
            else -> Color(0xFF2C7BE5)
        }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserHomeItemIconStack(item)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (item.kind == UserHomeLimitKind.Group) "Grupo de apps" else "Aplicación",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667085),
                )
            }
            Text(
                text =
                    if (item.remainingMinutes == 0) {
                        "Límite alcanzado"
                    } else {
                        "Quedan ${item.remainingMinutes} min"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = progressColor,
            )
        }
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor,
            trackColor = progressColor.copy(alpha = 0.14f),
        )
        Text(
            text = "${item.usedMinutes} de ${item.limitMinutes} min usados hoy",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF667085),
        )
    }
}

@Composable
private fun UserHomeItemIconStack(item: UserHomeLimitItem) {
    val icons = item.icons.ifEmpty { listOf(UserHomeAppIcon(item.title, null)) }
    Box(modifier = Modifier.size(width = 94.dp, height = 38.dp)) {
        icons.take(MaxItemIcons).forEachIndexed { index, icon ->
            Box(
                modifier =
                    Modifier
                        .offset(x = (index * ItemIconOffset).dp)
                        .zIndex(index.toFloat())
                        .size(36.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(2.dp),
            ) {
                AppIcon(icon.name, icon.iconBase64, size = 28)
            }
        }
    }
}

private val UpdatesUiState.shouldShowOnHome: Boolean
    get() =
        status == UpdatesStatus.Available ||
            status == UpdatesStatus.Downloading ||
            status == UpdatesStatus.DownloadFailed ||
            status == UpdatesStatus.ChecksumFailed

private val UpdatesUiState.homeUpdateMessage: String
    get() =
        when (status) {
            UpdatesStatus.Available ->
                manifest?.let { "Versión ${it.versionName} (${it.versionCode}) lista para instalar." }
                    ?: "Hay una versión nueva lista para instalar."
            UpdatesStatus.Downloading -> "Descargando y verificando… ${downloadProgressPercent ?: 0}%"
            UpdatesStatus.ChecksumFailed -> "La descarga no pasó la verificación de seguridad."
            UpdatesStatus.DownloadFailed -> "No se pudo descargar la actualización."
            else -> ""
        }

private fun String.isHealthyLicenseState(): Boolean =
    this == "Activada" || this == "Por vencer" || this == "Periodo de gracia"

private enum class UserHomeSection {
    Protection,
    Limits,
}

private val UserHomeHeaderTop = Color(0xFF172033)
private val UserHomeHeaderBottom = Color(0xFF263A5A)
private val ActiveProtectionColor = Color(0xFF72E6AA)
private val ReviewProtectionColor = Color(0xFFFFD166)
private const val ActiveStateLabel = "Activa"
private const val MaxQueueIcons = 7
private const val QueueIconOffset = 27
private const val MaxItemIcons = 4
private const val ItemIconOffset = 18
private const val MaxExpandedHomeLimits = 8

@Composable
private fun UserWebTab(
    onBack: (() -> Unit)?,
    onOpenDag: () -> Unit,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    onActivateWebProtection: () -> Unit,
    viewModel: UserWebViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blocked = state.webNavigationBlocked
    val protectionActive = vpnActive && accessibilityActive
    ProductLazyVisualPage(
        title = "Internet",
        subtitle = if (blocked) "Navegación bloqueada" else "Navegación protegida",
        onBack = onBack,
    ) {
        item {
            ProductLargeFeatureCard(
                title =
                    if (blocked) {
                        "Internet bloqueado"
                    } else {
                        if (state.activeLayers.isEmpty()) {
                            "Internet totalmente abierto"
                        } else {
                            "Internet abierto con protecciones"
                        }
                    },
                subtitle =
                    if (blocked) {
                        "Las capas elegidas quedan guardadas hasta que el administrador abra Internet."
                    } else {
                        state.activeLayers.joinToString(" · ").ifBlank { "Sin capas adicionales" }
                    },
                accent = ProductSky,
            )
        }
        item { UserWebStatusCard(title = "SafeSearch", value = if (blocked) "Automatico" else "Activo") }
        if (state.onlyResultsEnabled) {
            item { UserWebStatusCard(title = "Solo resultados", value = if (blocked) "Guardado" else "Activo") }
        }
        item {
            ProductLargeFeatureCard(
                title = "DAG",
                subtitle =
                    if (state.dagEnabled) {
                        "Buscador protegido habilitado por el administrador."
                    } else if (!state.dagEntitled) {
                        "DAG no está incluido en la licencia de esta comunidad."
                    } else {
                        "El administrador mantiene DAG cerrado."
                    },
                accent = ProductMint,
            )
        }
        if (state.dagEnabled) {
            item {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenDag) {
                    Text("Abrir DAG")
                }
            }
            item {
                ProductCard {
                    Text("DAG también aparece como una app independiente en el inicio del teléfono.")
                }
            }
        }
        if (state.showDomainListDiagnostics) {
            item {
                ProductCard {
                    Text("Base de proteccion Web", style = MaterialTheme.typography.titleMedium)
                    Text("Estado: ${state.domainList.status}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Version instalada: ${state.domainList.version.takeIf { it > 0L } ?: "Sin base"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Fecha de instalacion: ${state.domainList.installedAtEpochMillis.devDateLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Ultima comprobacion: ${state.domainList.lastCheckResult}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Canario incluido: ${if (state.domainList.canaryIncluded) "Si" else "No"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.domainList.lastError?.let { error ->
                        Text("Ultimo error: $error", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        enabled = !state.domainList.isChecking,
                        onClick = viewModel::checkDomainListNow,
                    ) {
                        Text(if (state.domainList.isChecking) "Comprobando..." else "Comprobar actualizacion ahora")
                    }
                }
            }
        }
        if (!accessibilityActive) {
            item {
                ProductCard {
                    Text("Accessibility apagado.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!vpnActive) {
            item {
                ProductCard {
                    Text("VPN apagada.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onActivateWebProtection) {
                        Text("Activar protección web")
                    }
                }
            }
        }
        if (!protectionActive) {
            item {
                ProductCard {
                    Text("Protección web no activa.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun Long.devDateLabel(): String =
    if (this <= 0L) {
        "Sin datos"
    } else {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            .format(java.util.Date(this))
    }

@Composable
private fun UserWebStatusCard(
    title: String,
    value: String,
) {
    ProductCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
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
}
