package com.contentfilter.user

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.contentfilter.core.ui.ProductMint
import com.contentfilter.core.ui.ProductNavGlyph
import com.contentfilter.core.ui.ProductSky
import com.contentfilter.core.ui.ProductStatCard
import com.contentfilter.core.ui.ProductSun
import com.contentfilter.core.ui.ProductTeal
import com.contentfilter.core.ui.ProductViolet
import com.contentfilter.core.ui.ProductVisualPage
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.requests.RequestsViewModel
import com.contentfilter.feature.status.SystemStatusViewModel
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.BuildConfig
import com.contentfilter.user.apps.MyAppsRoute
import com.contentfilter.user.internet.UserWebViewModel
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var activationRepository: DeviceActivationRepository

    @javax.inject.Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContentFilterTheme {
                UserAppRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            val activation = activationRepository.currentActivation() ?: return@launch
            targetedPolicySyncCoordinator.refresh(
                deviceId = activation.deviceId,
                reason = "foreground",
            )
        }
    }
}

@Composable
private fun UserAppRoot(
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(UserDestination.Home) }
    var backStack by rememberSaveable { mutableStateOf<List<UserDestination>>(emptyList()) }
    var showAccessibilityDialog by rememberSaveable { mutableStateOf(false) }
    var showVpnDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (VpnController.prepareIntent(context) == null) {
                VpnController.start(context)
            }
        }
    val rootViewModel: UserRootViewModel = hiltViewModel()
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val updatesViewModel: UpdatesViewModel = hiltViewModel()
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    val requestsViewModel: RequestsViewModel = hiltViewModel()
    val requestsState by requestsViewModel.uiState.collectAsStateWithLifecycle()
    val statusViewModel: SystemStatusViewModel = hiltViewModel()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updatesViewModel.autoCheckAndDownload()
    }
    LaunchedEffect(destination) {
        showAccessibilityDialog = !AccessibilityController.isEnabled(context)
    }
    LaunchedEffect(rootState.needsActivation, statusState.isVpnActive) {
        if (!rootState.needsActivation && !statusState.isVpnActive) {
            val permissionIntent = VpnController.prepareIntent(context)
            if (permissionIntent == null) {
                VpnController.start(context)
            } else {
                showVpnDialog = true
            }
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
                    UserHomeTab(
                        pendingRequests = requestsState.pendingCount,
                        latestRequestLabel = requestsState.requests.firstOrNull()?.requestType?.name.orEmpty(),
                        onApps = { navigateTo(UserDestination.MyApps) },
                        onRequests = { navigateTo(UserDestination.Requests) },
                        onWeb = { navigateTo(UserDestination.Web) },
                        onUpdates = { navigateTo(UserDestination.Updates) },
                    )
                UserDestination.MyApps -> MyAppsRoute(onBack = ::goBack)
                UserDestination.Requests -> RequestsRoute(onBack = ::goBack)
                UserDestination.Web ->
                    UserWebTab(
                        onBack = ::goBack,
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
                UserDestination.Updates ->
                    UpdatesRoute(
                        onBack = ::goBack,
                        protectionSummary = statusState.summary,
                        communityName = statusState.communityName,
                        guideName = statusState.guideName,
                        vpnState = statusState.vpnState,
                        accessibilityState = statusState.accessibilityState,
                        syncState = statusState.syncState,
                        activationState = statusState.activationState,
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
    } else if (showVpnDialog && !statusState.isVpnActive) {
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
    }
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
    ProductVisualPage(
        title = "Hola",
        subtitle = "Tu dispositivo protegido está listo",
    ) {
        ProductHeroPanel(
            title = "Content Filter",
            subtitle = "Revisá tus apps, permisos y solicitudes desde una experiencia más clara.",
            mascot = {
                UserHomeFish(modifier = Modifier.size(170.dp))
            },
        )
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
        ProductFeatureTile(
            icon = ProductIcon.Search,
            title = "Mis apps",
            subtitle = "Estado, límites y pedidos de acceso",
            accent = ProductTeal,
            onClick = onApps,
        )
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
        ProductFeatureTile(
            icon = ProductIcon.Web,
            title = "Web",
            subtitle = "Sección preparada para control web",
            accent = ProductSky,
            onClick = onWeb,
        )
        ProductFeatureTile(
            icon = ProductIcon.Update,
            title = "Ajustes",
            subtitle = "Protección, sincronización y actualizaciones",
            accent = ProductViolet,
            onClick = onUpdates,
        )
    }
}

@Composable
private fun UserWebTab(
    onBack: () -> Unit,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    onActivateWebProtection: () -> Unit,
    viewModel: UserWebViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blocked = state.webNavigationBlocked
    val protectionActive = vpnActive && accessibilityActive
    ProductVisualPage(
        title = "Web",
        subtitle = "Bloquear navegación web",
        onBack = onBack,
    ) {
        ProductLargeFeatureCard(
            title =
                if (blocked) {
                    "Navegación web bloqueada por el administrador"
                } else {
                    "Navegación web permitida"
                },
            subtitle =
                if (blocked) {
                    "El usuario no puede modificar este estado."
                } else {
                    "Sin bloqueo web activo desde el administrador."
                },
            accent = ProductSky,
        )
        UserWebStatusCard(
            title = "Abrir páginas desde buscadores",
            value =
                if (blocked && state.externalSearchResultsAllowed) {
                    "Permitido al habilitar Web"
                } else if (blocked) {
                    "Restringido al habilitar Web"
                } else if (state.externalSearchResultsAllowed) {
                    "Permitido"
                } else {
                    "Restringido"
                },
        )
        UserWebStatusCard(
            title = "Fotos e imágenes",
            value =
                if (state.imagesBlocked) {
                    "Filtrado activo"
                } else {
                    "Permitidas"
                },
        )
        UserWebStatusCard(
            title = "SafeSearch",
            value =
                if (blocked && state.safeSearchEnabled) {
                    "Activo al habilitar Web"
                } else if (state.safeSearchEnabled) {
                    "Activo"
                } else {
                    "Sin forzar"
                },
        )
        UserWebStatusCard(title = "Protección con IA", value = "Próximamente")
        if (!accessibilityActive) {
            ProductCard {
                Text("Accessibility apagado.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!vpnActive) {
            ProductCard {
                Text("VPN apagada.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onActivateWebProtection) {
                    Text("Activar protección web")
                }
            }
        }
        if (!protectionActive) {
            ProductCard {
                Text("Protección web no activa.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
    val loop = rememberInfiniteTransition(label = "user-home-fish")
    val swimX by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 7600
                        0f at 0 using FastOutSlowInEasing
                        -8f at 900 using FastOutSlowInEasing
                        9f at 1850 using FastOutSlowInEasing
                        -15f at 2800 using FastOutSlowInEasing
                        18f at 4300 using FastOutSlowInEasing
                        3f at 6100 using FastOutSlowInEasing
                        0f at 7600 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "user-home-fish-x",
    )
    val swimY by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 3600
                        0f at 0 using FastOutSlowInEasing
                        -9f at 900 using FastOutSlowInEasing
                        5f at 1900 using FastOutSlowInEasing
                        -3f at 2850 using FastOutSlowInEasing
                        0f at 3600 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "user-home-fish-y",
    )
    val roll by loop.animateFloat(
        initialValue = -4f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "user-home-fish-roll",
    )
    val bubbleRise by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "user-home-bubbles",
    )
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.55f, size.height * 0.80f),
            )
            listOf(0.12f, 0.36f, 0.62f, 0.84f).forEachIndexed { index, phase ->
                val t = (bubbleRise + phase) % 1f
                val x = size.width * (0.10f + index * 0.16f)
                val y = size.height * (0.86f - t * 0.70f)
                val r = size.minDimension * (0.025f + index * 0.006f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f * (1f - t * 0.35f)),
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
                    .size(138.dp)
                    .graphicsLayer {
                        translationX = swimX.dp.toPx()
                        translationY = swimY.dp.toPx()
                        rotationZ = roll
                        scaleX = 1.04f
                        scaleY = 1.04f
                    },
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
    Requests("Solicitudes", ProductIcon.Bell, showInNav = false),
    Updates("Ajustes", ProductIcon.Settings),
}
