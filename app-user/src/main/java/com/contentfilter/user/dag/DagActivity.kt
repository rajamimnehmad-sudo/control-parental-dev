package com.contentfilter.user.dag

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.sync.engine.TargetedPolicySyncCoordinator
import com.contentfilter.core.ui.ContentFilterTheme
import com.contentfilter.user.protection.ProtectionControlCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DagActivity : ComponentActivity() {
    @Inject
    lateinit var activationRepository: DeviceActivationRepository

    @Inject
    lateinit var targetedPolicySyncCoordinator: TargetedPolicySyncCoordinator

    @Inject
    lateinit var protectionControlCoordinator: ProtectionControlCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContentFilterTheme {
                val viewModel: DagBrowserViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.dagAvailabilityKnown, state.dagEnabled) {
                    if (state.dagAvailabilityKnown && !state.dagEnabled) finishAndRemoveTask()
                }
                DagBrowserRoute(
                    modifier = Modifier.fillMaxSize(),
                    onBack = ::finishAndRemoveTask,
                    standalone = true,
                    viewModel = viewModel,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            val activation = activationRepository.currentActivation() ?: return@launch
            targetedPolicySyncCoordinator.refresh(
                deviceId = activation.deviceId,
                reason = "dag-foreground",
            )
            protectionControlCoordinator.refresh()
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(
                Intent(context, DagActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
    }
}
