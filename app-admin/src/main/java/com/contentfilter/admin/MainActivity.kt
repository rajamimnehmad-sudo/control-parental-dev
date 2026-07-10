package com.contentfilter.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.BuildConfig
import com.contentfilter.admin.auth.AdminAuthRoute
import com.contentfilter.admin.dashboard.DashboardRoute
import com.contentfilter.admin.push.AdminPushViewModel
import com.contentfilter.admin.requests.AdminRequestsRoute
import com.contentfilter.admin.rules.RulesEntryMode
import com.contentfilter.admin.rules.RulesRoute
import com.contentfilter.admin.updates.AdminUpdatesRoute
import com.contentfilter.admin.updates.AdminUpdatesStatus
import com.contentfilter.admin.updates.AdminUpdatesViewModel
import com.contentfilter.core.ui.ContentFilterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContentFilterTheme {
                AdminAppRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun AdminAppRoot(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.Home) }
    var section by rememberSaveable { mutableStateOf<AdminSection?>(null) }
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
        bottomBar = {
            NavigationBar {
                AdminTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item && section == null,
                        onClick = {
                            tab = item
                            section = null
                        },
                        icon = { NavGlyph(icon = item.icon, selected = tab == item && section == null) },
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
                AdminSection.Apps -> RulesRoute(entryMode = RulesEntryMode.Apps, onBack = { section = null })
                AdminSection.Web -> RulesRoute(entryMode = RulesEntryMode.Web, onBack = { section = null })
                AdminSection.ManageUsers -> RulesRoute(entryMode = RulesEntryMode.ManageUsers, onBack = { section = null })
                AdminSection.Requests ->
                    SectionContainer(
                        title = "Solicitudes",
                        subtitle = "Permisos y tiempo extra pendientes",
                        onBack = { section = null },
                    ) {
                        AdminRequestsRoute(refreshKey = requestsRefreshKey)
                    }
                AdminSection.Updates ->
                    SectionContainer(
                        title = "Actualizaciones",
                        subtitle = "Versiones y administrador local",
                        onBack = { section = null },
                    ) {
                        AdminUpdatesRoute()
                    }
                null ->
                    when (tab) {
                        AdminTab.Home ->
                            HomeTab(
                                onCommunity = { tab = AdminTab.Community },
                                onManageUsers = { section = AdminSection.ManageUsers },
                                onSettings = { tab = AdminTab.Settings },
                            )
                        AdminTab.Community ->
                            CommunityTab(
                                onApps = { section = AdminSection.Apps },
                                onWeb = { section = AdminSection.Web },
                                onRequests = {
                                    requestsRefreshKey += 1
                                    section = AdminSection.Requests
                                },
                            )
                        AdminTab.Settings ->
                            SettingsTab(
                                onPanel = { section = AdminSection.Panel },
                                onUpdates = { section = AdminSection.Updates },
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
                    tab = AdminTab.Settings
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
                    tab = AdminTab.Settings
                    section = AdminSection.Updates
                }) {
                    Text("Ver")
                }
            },
        )
    }
}

@Composable
private fun HomeTab(
    onCommunity: () -> Unit,
    onManageUsers: () -> Unit,
    onSettings: () -> Unit,
) {
    VisualPage(
        title = "Hola",
        subtitle = "Tu panel de administración está listo",
    ) {
        AdminHeroCard()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "3",
                label = "secciones",
                accent = Sky,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "DEV",
                label = "versión ${BuildConfig.VERSION_CODE}",
                accent = Mint,
            )
        }
        FeatureTile(
            icon = Icons.Filled.Person,
            title = "Comunidad",
            subtitle = "Apps, Web y solicitudes",
            accent = Teal,
            onClick = onCommunity,
        )
        FeatureTile(
            icon = Icons.Filled.Person,
            title = "Administrar usuarios",
            subtitle = "Ver, agregar y borrar usuarios",
            accent = Sky,
            onClick = onManageUsers,
        )
        FeatureTile(
            icon = Icons.Filled.Settings,
            title = "Ajustes",
            subtitle = "Panel, actualizaciones y versión",
            accent = Violet,
            onClick = onSettings,
        )
    }
}

@Composable
private fun AdminHeroCard() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF18C7B7), Color(0xFF5B6CFF), Color(0xFF1E2A4A)),
                        ),
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(22.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = 126.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Content Filter",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                text = "Administrá permisos, usuarios y comunidad desde una experiencia más clara.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
            )
        }
        FishHomeImage(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 10.dp),
        )
    }
}

@Composable
private fun FishHomeImage(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "fish-loop")
    val swimX by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 9800
                        0f at 0 using FastOutSlowInEasing
                        -8f at 900 using FastOutSlowInEasing
                        10f at 1900 using FastOutSlowInEasing
                        -18f at 2750 using FastOutSlowInEasing
                        154f at 3900 using FastOutSlowInEasing
                        226f at 4400 using LinearEasing
                        226f at 4680 using LinearEasing
                        70f at 5450 using FastOutSlowInEasing
                        0f at 6550 using FastOutSlowInEasing
                        7f at 7900 using FastOutSlowInEasing
                        0f at 9800 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "fish-swim-x",
    )
    val swimY by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 4300
                        0f at 0 using FastOutSlowInEasing
                        -7f at 1050 using FastOutSlowInEasing
                        4f at 2200 using FastOutSlowInEasing
                        -2f at 3300 using FastOutSlowInEasing
                        0f at 4300 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "fish-swim-y",
    )
    val roll by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 4300
                        0f at 0 using FastOutSlowInEasing
                        -3f at 900 using FastOutSlowInEasing
                        4f at 2200 using FastOutSlowInEasing
                        -1f at 3400 using FastOutSlowInEasing
                        0f at 4300 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "fish-roll",
    )
    val breathe by loop.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "fish-breathe",
    )
    val finSway by loop.animateFloat(
        initialValue = -16f,
        targetValue = 18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "fish-fin",
    )
    val tailSway by loop.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "fish-tail",
    )
    val blink by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 4200
                        0f at 0
                        0f at 1350
                        1f at 1420 using FastOutSlowInEasing
                        1f at 1560
                        0f at 1650 using FastOutSlowInEasing
                        0f at 3100
                        1f at 3170 using FastOutSlowInEasing
                        1f at 3260
                        0f at 3350 using FastOutSlowInEasing
                        0f at 4200
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "fish-blink",
    )
    val sparkle by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "fish-sparkle",
    )
    val tapMotion = remember { Animatable(0f) }
    val splash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun playTap() {
        scope.launch {
            tapMotion.snapTo(0f)
            tapMotion.animateTo(1f, tween(durationMillis = 95, easing = FastOutSlowInEasing))
            tapMotion.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
        scope.launch {
            splash.snapTo(0f)
            splash.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
            splash.animateTo(0f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        playTap()
    }
    val tap = tapMotion.value
    val bodyScale = breathe + tap * 0.11f
    val bodyX = swimX + tap * 18f
    val bodyY = swimY - tap * 10f
    val bodyRotation = roll + tap * 12f
    Box(
        modifier =
            modifier
                .size(174.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { playTap() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shadowAlpha = (0.17f - tap * 0.06f).coerceAtLeast(0.05f)
            drawOval(
                color = Color(0xFF071329).copy(alpha = shadowAlpha),
                topLeft = Offset(size.width * 0.2f, size.height * 0.73f),
                size = Size(size.width * 0.58f, size.height * 0.11f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.28f * (1f - sparkle)),
                radius = 7f + sparkle * 16f,
                center = Offset(size.width * 0.15f, size.height * (0.22f - sparkle * 0.08f)),
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = Mint.copy(alpha = 0.22f),
                radius = 42f + tap * 18f,
                center = Offset(size.width * 0.72f, size.height * 0.68f),
            )
            if (splash.value > 0f) {
                val splashAlpha = (1f - splash.value) * 0.62f
                drawCircle(
                    color = Color.White.copy(alpha = splashAlpha),
                    radius = 38f + splash.value * 36f,
                    center = Offset(size.width * 0.6f, size.height * 0.47f),
                    style = Stroke(width = 4f),
                )
                drawLine(
                    color = Color.White.copy(alpha = splashAlpha),
                    start = Offset(size.width * 0.76f, size.height * 0.36f),
                    end = Offset(size.width * (0.82f + splash.value * 0.08f), size.height * 0.28f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(126.dp)
                    .graphicsLayer {
                        translationX = bodyX * 0.45f
                        translationY = bodyY * 0.38f
                        scaleX = 1f + tap * 0.04f
                        scaleY = 1f + tap * 0.04f
                    }
                    .background(Color.White.copy(alpha = 0.13f), CircleShape),
        )
        Canvas(
            modifier =
                Modifier
                    .size(158.dp)
                    .offset(x = 4.dp, y = 1.dp)
                    .graphicsLayer {
                        translationX = bodyX
                        translationY = bodyY
                        rotationZ = bodyRotation
                        scaleX = bodyScale
                        scaleY = bodyScale
                    },
        ) {
            val w = size.width
            val h = size.height
            fun sx(value: Float) = w * value / 1024f
            fun sy(value: Float) = h * value / 1024f
            fun point(x: Float, y: Float) = Offset(sx(x), sy(y))
            fun ovalSize(rx: Float, ry: Float) = Size(sx(rx * 2f), sy(ry * 2f))

            val finBrush =
                Brush.linearGradient(
                    colors = listOf(Color(0xFF47DADB), Color(0xFF169AAE)),
                    start = Offset.Zero,
                    end = Offset(w, h),
                )
            val bodyBrush =
                Brush.linearGradient(
                    colors = listOf(Color(0xFFE2FFF7), Color(0xFFB8F7E8), Color(0xFF7DE7D8), Color(0xFF46C8C8)),
                    start = point(250f, 230f),
                    end = point(730f, 760f),
                )
            val eyeBrush =
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1C3657), Color(0xFF0C1E35)),
                    start = point(0f, 420f),
                    end = point(0f, 560f),
                )
            val finShadow = Color(0xFF0C8194)
            val bodyColor = Color(0xFF8FEBDD)
            val ink = Color(0xFF071329)

            rotate(tailSway + tap * 6f, pivot = point(778f, 516f)) {
                val tail = Path().apply {
                    moveTo(sx(760f), sy(514f))
                    cubicTo(sx(826f), sy(384f), sx(972f), sy(386f), sx(970f), sy(488f))
                    cubicTo(sx(969f), sy(533f), sx(928f), sy(551f), sx(880f), sy(558f))
                    cubicTo(sx(925f), sy(577f), sx(970f), sy(640f), sx(927f), sy(700f))
                    cubicTo(sx(873f), sy(776f), sx(792f), sy(668f), sx(760f), sy(514f))
                    close()
                }
                drawPath(path = tail, brush = finBrush)
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(790f), sy(512f))
                            cubicTo(sx(838f), sy(503f), sx(890f), sy(470f), sx(930f), sy(425f))
                        },
                    alpha = 0.34f,
                    style = Stroke(width = sx(14f), cap = StrokeCap.Round),
                )
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(794f), sy(540f))
                            cubicTo(sx(850f), sy(566f), sx(892f), sy(604f), sx(915f), sy(656f))
                        },
                    alpha = 0.24f,
                    style = Stroke(width = sx(14f), cap = StrokeCap.Round),
                )
            }

            rotate(-finSway * 0.34f, pivot = point(548f, 286f)) {
                val topFin = Path().apply {
                    moveTo(sx(426f), sy(318f))
                    cubicTo(sx(468f), sy(170f), sx(650f), sy(127f), sx(744f), sy(210f))
                    cubicTo(sx(798f), sy(258f), sx(811f), sy(347f), sx(768f), sy(392f))
                    cubicTo(sx(672f), sy(339f), sx(544f), sy(307f), sx(426f), sy(318f))
                    close()
                }
                drawPath(path = topFin, brush = finBrush)
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(516f), sy(276f))
                            cubicTo(sx(548f), sy(220f), sx(598f), sy(186f), sx(654f), sy(170f))
                        },
                    alpha = 0.32f,
                    style = Stroke(width = sx(14f), cap = StrokeCap.Round),
                )
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(620f), sy(296f))
                            cubicTo(sx(651f), sy(240f), sx(700f), sy(211f), sx(737f), sy(210f))
                        },
                    alpha = 0.22f,
                    style = Stroke(width = sx(14f), cap = StrokeCap.Round),
                )
            }

            rotate(-10f - finSway * 0.82f, pivot = point(543f, 760f)) {
                val bottomFin = Path().apply {
                    moveTo(sx(486f), sy(748f))
                    cubicTo(sx(552f), sy(766f), sx(607f), sy(817f), sx(590f), sy(862f))
                    cubicTo(sx(570f), sy(915f), sx(467f), sy(871f), sx(450f), sy(791f))
                    cubicTo(sx(445f), sy(761f), sx(459f), sy(748f), sx(486f), sy(748f))
                    close()
                }
                drawPath(path = bottomFin, brush = finBrush)
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(488f), sy(782f))
                            cubicTo(sx(529f), sy(798f), sx(560f), sy(825f), sx(580f), sy(854f))
                        },
                    alpha = 0.25f,
                    style = Stroke(width = sx(11f), cap = StrokeCap.Round),
                )
            }

            rotate(-finSway * 1.05f - tap * 16f, pivot = point(170f, 575f)) {
                val leftFin = Path().apply {
                    moveTo(sx(157f), sy(563f))
                    cubicTo(sx(77f), sy(557f), sx(55f), sy(621f), sx(104f), sy(661f))
                    cubicTo(sx(155f), sy(701f), sx(224f), sy(651f), sx(235f), sy(594f))
                    cubicTo(sx(241f), sy(561f), sx(201f), sy(552f), sx(157f), sy(563f))
                    close()
                }
                drawPath(path = leftFin, brush = finBrush)
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(120f), sy(608f))
                            cubicTo(sx(157f), sy(612f), sx(196f), sy(600f), sx(226f), sy(579f))
                        },
                    alpha = 0.27f,
                    style = Stroke(width = sx(11f), cap = StrokeCap.Round),
                )
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(128f), sy(639f))
                            cubicTo(sx(163f), sy(640f), sx(193f), sy(627f), sx(220f), sy(607f))
                        },
                    alpha = 0.18f,
                    style = Stroke(width = sx(10f), cap = StrokeCap.Round),
                )
            }

            val body = Path().apply {
                moveTo(sx(150f), sy(536f))
                cubicTo(sx(150f), sy(344f), sx(301f), sy(248f), sx(516f), sy(260f))
                cubicTo(sx(701f), sy(271f), sx(839f), sy(374f), sx(853f), sy(538f))
                cubicTo(sx(870f), sy(724f), sx(718f), sy(817f), sx(487f), sy(804f))
                cubicTo(sx(275f), sy(792f), sx(150f), sy(710f), sx(150f), sy(536f))
                close()
            }
            drawPath(
                color = Color(0xFF178C95).copy(alpha = 0.17f),
                path = body,
                alpha = 0.45f,
            )
            drawPath(path = body, brush = bodyBrush)
            drawPath(
                color = Color(0xFF38BEB8),
                alpha = 0.18f,
                path =
                    Path().apply {
                        moveTo(sx(196f), sy(631f))
                        cubicTo(sx(268f), sy(759f), sx(495f), sy(813f), sx(683f), sy(758f))
                        cubicTo(sx(557f), sy(844f), sx(253f), sy(813f), sx(174f), sy(647f))
                        close()
                    },
            )
            drawPath(
                color = Color.White.copy(alpha = 0.28f),
                path =
                    Path().apply {
                        moveTo(sx(235f), sy(407f))
                        cubicTo(sx(288f), sy(351f), sx(370f), sy(316f), sx(452f), sy(307f))
                    },
                style = Stroke(width = sx(42f), cap = StrokeCap.Round),
            )
            drawOval(color = Color.White.copy(alpha = 0.10f), topLeft = point(759f, 447f), size = ovalSize(20f, 14f))

            listOf(
                Triple(point(632f, 361f), ovalSize(28f, 18f), 0.55f),
                Triple(point(698f, 382f), ovalSize(31f, 21f), 0.45f),
                Triple(point(679f, 448f), ovalSize(25f, 19f), 0.42f),
                Triple(point(748f, 471f), ovalSize(17f, 29f), 0.35f),
            ).forEach { (center, oval, alpha) ->
                drawOval(
                    color = Color(0xFF4ACBBF).copy(alpha = alpha),
                    topLeft = Offset(center.x - oval.width / 2f, center.y - oval.height / 2f),
                    size = oval,
                )
            }
            drawCircle(color = Color(0xFF4ACBBF).copy(alpha = 0.28f), radius = sx(15f), center = point(773f, 532f))
            drawOval(color = Color(0xFF6EDDD0).copy(alpha = 0.46f), topLeft = point(235f, 576f), size = ovalSize(41f, 24f))
            drawOval(color = Color(0xFF6EDDD0).copy(alpha = 0.42f), topLeft = point(538f, 578f), size = ovalSize(42f, 25f))

            rotate(finSway * 1.15f + tap * 20f, pivot = point(651f, 592f)) {
                val rightFin = Path().apply {
                    moveTo(sx(630f), sy(578f))
                    cubicTo(sx(717f), sy(537f), sx(798f), sy(573f), sx(793f), sy(635f))
                    cubicTo(sx(788f), sy(700f), sx(685f), sy(713f), sx(626f), sy(655f))
                    cubicTo(sx(593f), sy(621f), sx(596f), sy(594f), sx(630f), sy(578f))
                    close()
                }
                drawPath(path = rightFin, brush = finBrush)
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(644f), sy(606f))
                            cubicTo(sx(696f), sy(603f), sx(744f), sy(621f), sx(779f), sy(648f))
                        },
                    alpha = 0.27f,
                    style = Stroke(width = sx(12f), cap = StrokeCap.Round),
                )
                drawPath(
                    color = finShadow,
                    path =
                        Path().apply {
                            moveTo(sx(656f), sy(637f))
                            cubicTo(sx(698f), sy(650f), sx(734f), sy(668f), sx(759f), sy(689f))
                        },
                    alpha = 0.17f,
                    style = Stroke(width = sx(10f), cap = StrokeCap.Round),
                )
            }

            val leftEye = point(310f, 486f)
            val rightEye = point(500f, 486f)
            drawOval(color = Color(0xFFF9FFFF), topLeft = point(240f, 402f), size = ovalSize(70f, 84f))
            drawOval(color = Color(0xFFF9FFFF), topLeft = point(430f, 402f), size = ovalSize(70f, 84f))
            drawOval(brush = eyeBrush, topLeft = point(261f, 430f), size = ovalSize(51f, 62f))
            drawOval(brush = eyeBrush, topLeft = point(451f, 430f), size = ovalSize(51f, 62f))
            drawOval(color = Color(0xFF091528), topLeft = point(291f, 480f), size = ovalSize(22f, 27f))
            drawOval(color = Color(0xFF091528), topLeft = point(481f, 480f), size = ovalSize(22f, 27f))
            drawCircle(color = Color.White, radius = sx(16f), center = point(291f, 462f))
            drawCircle(color = Color.White.copy(alpha = 0.65f), radius = sx(7f), center = point(323f, 483f))
            drawCircle(color = Color.White, radius = sx(16f), center = point(481f, 462f))
            drawCircle(color = Color.White.copy(alpha = 0.65f), radius = sx(7f), center = point(513f, 483f))

            if (blink > 0.02f) {
                drawPath(
                    color = bodyColor.copy(alpha = blink),
                    path =
                        Path().apply {
                            moveTo(sx(240f), sy(486f))
                            cubicTo(sx(250f), sy(422f), sx(281f), sy(390f), sx(310f), sy(390f))
                            cubicTo(sx(345f), sy(390f), sx(373f), sy(419f), sx(380f), sy(486f))
                            cubicTo(sx(350f), sy(450f), sx(272f), sy(450f), sx(240f), sy(486f))
                            close()
                        },
                )
                drawPath(
                    color = bodyColor.copy(alpha = blink),
                    path =
                        Path().apply {
                            moveTo(sx(430f), sy(486f))
                            cubicTo(sx(440f), sy(422f), sx(471f), sy(390f), sx(500f), sy(390f))
                            cubicTo(sx(535f), sy(390f), sx(563f), sy(419f), sx(570f), sy(486f))
                            cubicTo(sx(540f), sy(450f), sx(462f), sy(450f), sx(430f), sy(486f))
                            close()
                        },
                )
                drawLine(
                    color = ink.copy(alpha = blink),
                    start = Offset(leftEye.x - sx(42f), leftEye.y),
                    end = Offset(leftEye.x + sx(42f), leftEye.y),
                    strokeWidth = sx(14f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = ink.copy(alpha = blink),
                    start = Offset(rightEye.x - sx(42f), rightEye.y),
                    end = Offset(rightEye.x + sx(42f), rightEye.y),
                    strokeWidth = sx(14f),
                    cap = StrokeCap.Round,
                )
            }

            drawPath(
                color = Color(0xFF129097),
                path =
                    Path().apply {
                        moveTo(sx(255f), sy(392f))
                        cubicTo(sx(278f), sy(373f), sx(311f), sy(371f), sx(335f), sy(390f))
                    },
                style = Stroke(width = sx(17f), cap = StrokeCap.Round),
            )
            drawPath(
                color = Color(0xFF129097),
                path =
                    Path().apply {
                        moveTo(sx(445f), sy(389f))
                        cubicTo(sx(468f), sy(371f), sx(501f), sy(373f), sx(524f), sy(392f))
                    },
                style = Stroke(width = sx(17f), cap = StrokeCap.Round),
            )
            drawArc(
                color = ink,
                startAngle = 12f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = point(352f, 560f + tap * 14f),
                size = Size(sx(108f), sy(82f + tap * 34f)),
                style = Stroke(width = sx(16f), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun CommunityTab(
    onApps: () -> Unit,
    onWeb: () -> Unit,
    onRequests: () -> Unit,
) {
    VisualPage(
        title = "Comunidad",
        subtitle = "Gestioná usuarios, solicitudes y permisos",
    ) {
        LargeFeatureCard(
            title = "Comunidad protegida",
            subtitle = "Entrá a los flujos principales con menos ruido visual.",
            accent = Teal,
        )
        FeatureTile(
            icon = Icons.Filled.Search,
            title = "Apps",
            subtitle = "Elegir usuario y configurar apps",
            accent = Sky,
            onClick = onApps,
        )
        FeatureTile(
            icon = Icons.Filled.Person,
            title = "Web",
            subtitle = "Elegir usuario y bloquear navegación web",
            accent = Teal,
            onClick = onWeb,
        )
        FeatureTile(
            icon = Icons.Filled.Notifications,
            title = "Solicitudes",
            subtitle = "Aprobar accesos y tiempo extra",
            accent = Sun,
            onClick = onRequests,
        )
    }
}

@Composable
private fun SettingsTab(
    onPanel: () -> Unit,
    onUpdates: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(title = "Ajustes", subtitle = "Herramientas de administración")
            LargeFeatureCard(
                title = "Control general",
                subtitle = "Configuración, estado y versiones desde un solo lugar.",
                accent = Violet,
            )
            FeatureTile(
                icon = Icons.Filled.Settings,
                title = "Panel administrador",
                subtitle = "Estado general, comunidad y sincronización",
                accent = Teal,
                onClick = onPanel,
            )
            FeatureTile(
                icon = Icons.Filled.Refresh,
                title = "Actualizaciones",
                subtitle = "Buscar versión y cambiar administrador local",
                accent = Sun,
                onClick = onUpdates,
            )
        }
        Text(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            text = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisualPage(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(title = title, subtitle = subtitle)
        content()
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Ink)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
            )
        }
    }
}

@Composable
private fun LargeFeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MiniIllustration(accent = accent)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
}

@Composable
private fun MiniIllustration(accent: Color) {
    Box(modifier = Modifier.size(46.dp)) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(accent, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .width(28.dp)
                    .height(10.dp)
                    .background(accent.copy(alpha = 0.65f), CircleShape),
        )
    }
}

@Composable
private fun NavGlyph(
    icon: ImageVector,
    selected: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(if (selected) 34.dp else 30.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MutedInk,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SectionContainer(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 18.dp, end = 18.dp),
            title = title,
            subtitle = subtitle,
            onBack = onBack,
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private enum class AdminTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home),
    Community("Comunidad", Icons.Filled.Person),
    Settings("Ajustes", Icons.Filled.Settings),
}

private enum class AdminSection {
    Panel,
    Apps,
    Web,
    ManageUsers,
    Requests,
    Updates,
}

private val AppBackground = Color(0xFFF2F8F7)
private val Ink = Color(0xFF162235)
private val MutedInk = Color(0xFF68758A)
private val Teal = Color(0xFF13BFAE)
private val Sky = Color(0xFF2C9AF4)
private val Sun = Color(0xFFFFC849)
private val Mint = Color(0xFF55E1B8)
private val Violet = Color(0xFF6C63FF)
