package com.contentfilter.user

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.core.ui.PremiumFishMascot
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductFeatureTile
import com.contentfilter.core.ui.ProductHeroPanel
import com.contentfilter.core.ui.ProductIcon
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductLazyVisualPage
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductNavGlyph
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductStatCard
import com.contentfilter.core.ui.ProductSun
import com.contentfilter.core.ui.ProductTeal
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.accessibility.service.DeviceAdminController
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.requests.RequestsViewModel
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.apps.MyAppsRoute
import com.contentfilter.user.dag.DagBrowserRoute
import com.contentfilter.user.dag.DagShortcutController
import com.contentfilter.user.internet.UserWebViewModel
import com.contentfilter.user.protection.BatteryOptimizationController
import com.contentfilter.user.protection.ProtectionControlCoordinator
import com.contentfilter.user.protection.ProtectionViewModel
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dagLaunchRequests = MutableStateFlow(0)

    @javax.inject.Inject
    lateinit var activationRepository: DeviceActivationRepository

    @javax.inject.Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    @javax.inject.Inject
    lateinit var protectionControlCoordinator: ProtectionControlCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordDagLaunch(intent)
        setContent {
            ContentFilterTheme {
                val dagLaunchRequest by dagLaunchRequests.collectAsStateWithLifecycle()
                UserAppRoot(
                    modifier = Modifier.fillMaxSize(),
                    dagLaunchRequest = dagLaunchRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordDagLaunch(intent)
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
}

@Composable
private fun UserAppRoot(
    modifier: Modifier = Modifier,
    dagLaunchRequest: Int = 0,
) {
    var destination by rememberSaveable { mutableStateOf(UserDestination.Home) }
    var backStack by rememberSaveable { mutableStateOf<List<UserDestination>>(emptyList()) }
    var showAccessibilityDialog by rememberSaveable { mutableStateOf(false) }
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
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updatesViewModel.autoCheckAndDownload()
    }
    LaunchedEffect(dagLaunchRequest) {
        if (dagLaunchRequest > 0) {
            backStack = listOf(UserDestination.Web)
            destination = UserDestination.Dag
        }
    }
    LaunchedEffect(destination) {
        showAccessibilityDialog = !AccessibilityController.isEnabled(context)
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
                        onClick = { navigateTo(item) },
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
                        onApps = { navigateTo(UserDestination.MyApps) },
                        onRequests = { navigateTo(UserDestination.Requests) },
                        onWeb = { navigateTo(UserDestination.Web) },
                        onUpdates = { navigateTo(UserDestination.Updates) },
                    )
                UserDestination.MyApps -> MyAppsRoute(onBack = ::goBack)
                UserDestination.Requests -> RequestsRoute(onBack = ::goBack)
                UserDestination.Web -> {
                    val statusViewModel: SystemStatusViewModel = hiltViewModel()
                    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
                    UserWebTab(
                        onBack = ::goBack,
                        onOpenDag = { navigateTo(UserDestination.Dag) },
                        onCreateDagShortcut = {
                            val requested = DagShortcutController.requestPin(context)
                            Toast.makeText(
                                context,
                                if (requested) "Confirmá el atajo DAG." else "Este inicio no admite atajos.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
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
                        onBack = ::goBack,
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
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("Accesibilidad apagada") },
            text = {
                Text(
                    "El bloqueo de apps necesita el servicio de accesibilidad activo. Android puede desactivarlo después de una actualización.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAccessibilityDialog = false }) {
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
    onApps: () -> Unit,
    onRequests: () -> Unit,
    onWeb: () -> Unit,
    onUpdates: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserHomeTab(
        pendingRequests = state.pendingCount,
        latestRequestLabel = state.requests.firstOrNull()?.requestType?.name.orEmpty(),
        onApps = onApps,
        onRequests = onRequests,
        onWeb = onWeb,
        onUpdates = onUpdates,
    )
}

@Composable
private fun UserHomeTab(
    pendingRequests: Int,
    latestRequestLabel: String,
    onApps: () -> Unit,
    onRequests: () -> Unit,
    onWeb: () -> Unit,
    onUpdates: () -> Unit,
) {
    ProductLazyVisualPage(
        title = "Hola",
        subtitle = "Tu dispositivo protegido está listo",
    ) {
        item {
            ProductHeroPanel(
                title = "Content Filter",
                subtitle = "Revisá tus apps, permisos y solicitudes desde una experiencia más clara.",
                mascot = {
                    UserHomeFish(modifier = Modifier.size(170.dp))
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProductStatCard(
                    modifier = Modifier.weight(1f),
                    value = "5",
                    label = "secciones",
                    accent = ProductSky,
                )
                ProductStatCard(
                    modifier = Modifier.weight(1f),
                    value = "DEV",
                    label = "versión ${BuildConfig.VERSION_CODE}",
                    accent = ProductMint,
                )
            }
        }
        item {
            ProductFeatureTile(
                icon = ProductIcon.Search,
                title = "Mis apps",
                subtitle = "Estado, límites y pedidos de acceso",
                accent = ProductTeal,
                onClick = onApps,
            )
        }
        item {
            ProductFeatureTile(
                icon = ProductIcon.Bell,
                title = "Solicitudes pendientes",
                subtitle =
                    if (pendingRequests == 0) {
                        "No hay pedidos pendientes"
                    } else {
                        "$pendingRequests pendientes${latestRequestLabel.ifBlank { "" }.let { if (it.isBlank()) "" else " · tocá para ver" }}"
                    },
                accent = ProductSun,
                onClick = onRequests,
            )
        }
        item {
            ProductFeatureTile(
                icon = ProductIcon.Web,
                title = "Web",
                subtitle = "Sección preparada para control web",
                accent = ProductSky,
                onClick = onWeb,
            )
        }
        item {
            ProductFeatureTile(
                icon = ProductIcon.Update,
                title = "Ajustes",
                subtitle = "Protección, sincronización y actualizaciones",
                accent = ProductViolet,
                onClick = onUpdates,
            )
        }
    }
}

@Composable
private fun UserWebTab(
    onBack: () -> Unit,
    onOpenDag: () -> Unit,
    onCreateDagShortcut: () -> Unit,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    onActivateWebProtection: () -> Unit,
    viewModel: UserWebViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blocked = state.webNavigationBlocked
    val protectionActive = vpnActive && accessibilityActive
    ProductLazyVisualPage(
        title = "Web",
        subtitle = if (blocked) "Internet bloqueado" else "Internet abierto",
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
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCreateDagShortcut) {
                    Text("Crear atajo DAG")
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

@Composable
private fun UserHomeFish(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.55f, size.height * 0.80f),
            )
            listOf(0.12f, 0.36f, 0.62f, 0.84f).forEachIndexed { index, position ->
                val x = size.width * (0.10f + index * 0.16f)
                val y = size.height * (0.86f - position * 0.70f)
                val r = size.minDimension * (0.025f + index * 0.006f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = r,
                    center = Offset(x, y),
                    style = Stroke(width = 2.2f),
                )
            }
        }
        PremiumFishMascot(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(138.dp),
        )
    }
}

private enum class UserDestination(
    val label: String,
    val icon: ProductIcon,
    val showInNav: Boolean = true,
) {
    Home("Home", ProductIcon.Home),
    MyApps("Mis apps", ProductIcon.Search),
    Web("Web", ProductIcon.Web),
    Dag("DAG", ProductIcon.Search, showInNav = false),
    Requests("Solicitudes", ProductIcon.Bell, showInNav = false),
    Updates("Ajustes", ProductIcon.Settings),
}
