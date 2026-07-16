package com.contentfilter.user.protection

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.model.allowsProtection
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.ProtectionStateStore
import com.contentfilter.core.domain.repository.SystemStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLauncherController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        activationRepository: DeviceActivationRepository,
        private val protectionStateStore: ProtectionStateStore,
        private val systemStatusRepository: SystemStatusRepository,
    ) {
        private val visibilityInputs =
            combine(
                activationRepository.observeActivation(),
                protectionStateStore.observeControl(),
                systemStatusRepository.observeHealth(),
            ) { activation, control, health ->
                LauncherVisibilityInputs(
                    hasActivation = activation != null,
                    control = control,
                    health = health,
                )
            }

        suspend fun monitorVisibility() {
            visibilityInputs.collectLatest { inputs ->
                updateVisibility(inputs)
                protectionStateStore
                    .authorizationExpiresAtEpochMillis()
                    ?.let { expiresAt ->
                        delay(expiresAt - System.currentTimeMillis() + 100L)
                        updateVisibility(inputs.copy(control = protectionStateStore.currentControl()))
                    }
            }
        }

        private fun updateVisibility(inputs: LauncherVisibilityInputs) {
            setLauncherEnabled(
                userLauncherShouldBeEnabled(
                    hasActivation = inputs.hasActivation,
                    armed = inputs.control?.armed == true,
                    protectionAvailable =
                        inputs.health.licenseState.allowsProtection() &&
                            inputs.health.vpnState == ComponentState.Enabled,
                    maintenanceAuthorized =
                        protectionStateStore.isAuthorized(ProtectionAuthorizationScope.Settings) ||
                            protectionStateStore.isAuthorized(ProtectionAuthorizationScope.Removal),
                ),
            )
        }

        private fun setLauncherEnabled(enabled: Boolean) {
            val component = ComponentName(context, UserLauncherAliasClassName)
            val expected =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            if (context.packageManager.getComponentEnabledSetting(component) == expected) return
            context.packageManager.setComponentEnabledSetting(component, expected, PackageManager.DONT_KILL_APP)
        }

        private data class LauncherVisibilityInputs(
            val hasActivation: Boolean,
            val control: DeviceProtectionControl?,
            val health: SystemHealthSnapshot,
        )

        private companion object {
            const val UserLauncherAliasClassName = "com.contentfilter.user.UserLauncherAlias"
        }
    }

internal fun userLauncherShouldBeEnabled(
    hasActivation: Boolean,
    armed: Boolean,
    protectionAvailable: Boolean,
    maintenanceAuthorized: Boolean,
): Boolean = !hasActivation || !armed || !protectionAvailable || maintenanceAuthorized
