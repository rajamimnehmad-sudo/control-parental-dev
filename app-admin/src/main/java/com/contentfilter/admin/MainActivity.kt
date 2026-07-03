package com.contentfilter.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.contentfilter.admin.auth.AdminAuthRoute
import com.contentfilter.admin.dashboard.DashboardRoute
import com.contentfilter.admin.devices.DevicesRoute
import com.contentfilter.admin.requests.AdminRequestsRoute
import com.contentfilter.admin.rules.RulesRoute
import com.contentfilter.admin.updates.AdminUpdatesRoute
import com.contentfilter.admin.updates.AdminUpdatesStatus
import com.contentfilter.admin.updates.AdminUpdatesViewModel
import com.contentfilter.core.ui.ContentFilterTheme
import dagger.hilt.android.AndroidEntryPoint

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
    var destination by rememberSaveable { mutableStateOf(AdminDestination.Dashboard) }
    var requestsRefreshKey by rememberSaveable { mutableStateOf(0) }
    val updatesViewModel: AdminUpdatesViewModel = hiltViewModel()
    val updateState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        updatesViewModel.autoCheckAndDownload()
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AdminDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            destination = item
                            if (item == AdminDestination.Requests) {
                                requestsRefreshKey += 1
                            }
                        },
                        icon = {},
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                AdminDestination.Login -> AdminAuthRoute()
                AdminDestination.Dashboard -> DashboardRoute()
                AdminDestination.Devices -> DevicesRoute()
                AdminDestination.Requests -> AdminRequestsRoute(refreshKey = requestsRefreshKey)
                AdminDestination.Rules -> RulesRoute()
                AdminDestination.Updates -> AdminUpdatesRoute()
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
                OutlinedButton(onClick = { destination = AdminDestination.Updates }) {
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
                OutlinedButton(onClick = { destination = AdminDestination.Updates }) {
                    Text("Ver")
                }
            },
        )
    }
}

private enum class AdminDestination(val label: String) {
    Login("Login"),
    Dashboard("Panel"),
    Devices("Dispositivos"),
    Requests("Solicitudes"),
    Rules("Reglas"),
    Updates("Actualizaciones"),
}
