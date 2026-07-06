package com.contentfilter.user

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.feature.accessibility.service.AccessibilityController
import com.contentfilter.feature.activation.ActivationRoute
import com.contentfilter.feature.requests.RequestsRoute
import com.contentfilter.feature.status.SystemStatusRoute
import com.contentfilter.user.apps.MyAppsRoute
import com.contentfilter.user.updates.UpdatesRoute
import com.contentfilter.user.updates.UpdatesStatus
import com.contentfilter.user.updates.UpdatesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val blockedDomainIntent = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedDomainIntent.value = intent.blockedDomainExtra()
        setContent {
            ContentFilterTheme {
                val blockedDomain by blockedDomainIntent.collectAsStateWithLifecycle()
                UserAppRoot(
                    modifier = Modifier.fillMaxSize(),
                    blockedDomain = blockedDomain,
                    onBlockedDomainConsumed = { blockedDomainIntent.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedDomainIntent.value = intent.blockedDomainExtra()
    }
}

@Composable
private fun UserAppRoot(
    modifier: Modifier = Modifier,
    blockedDomain: String? = null,
    onBlockedDomainConsumed: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(UserDestination.MyApps) }
    var showAccessibilityDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val rootViewModel: UserRootViewModel = hiltViewModel()
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val updatesViewModel: UpdatesViewModel = hiltViewModel()
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updatesViewModel.autoCheckAndDownload()
    }
    LaunchedEffect(destination) {
        showAccessibilityDialog = !AccessibilityController.isEnabled(context)
    }
    LaunchedEffect(blockedDomain) {
        if (!blockedDomain.isNullOrBlank()) onBlockedDomainConsumed()
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
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                UserDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {},
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                UserDestination.Status -> SystemStatusRoute()
                UserDestination.MyApps -> MyAppsRoute()
                UserDestination.Requests -> RequestsRoute()
                UserDestination.Updates -> UpdatesRoute()
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
                OutlinedButton(onClick = { destination = UserDestination.Updates }) {
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
                OutlinedButton(onClick = { destination = UserDestination.Updates }) {
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
    }
}

private enum class UserDestination(val label: String) {
    Status("Estado"),
    MyApps("Mis apps"),
    Requests("Mis solicitudes"),
    Updates("Actualizaciones"),
}

private fun Intent.blockedDomainExtra(): String? =
    takeIf { action == OpenInternetAction }
        ?.getStringExtra(ExtraBlockedDomain)

const val OpenInternetAction = "com.contentfilter.user.OPEN_INTERNET"
const val ExtraBlockedDomain = "blocked_domain"
