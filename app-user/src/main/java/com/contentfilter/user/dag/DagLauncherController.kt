package com.contentfilter.user.dag

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagLauncherController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        activationRepository: DeviceActivationRepository,
        policyRepository: PolicyRepository,
        systemStatusRepository: SystemStatusRepository,
    ) {
        private val availability =
            combine(
                activationRepository.observeActivation(),
                policyRepository.observeActivePolicy(),
                systemStatusRepository.observeHealth(),
            ) { activation, policy, health ->
                dagLauncherShouldBeEnabled(
                    hasActivation = activation != null,
                    dagEnabled = policy.rules.dagEnabled(),
                    dagEntitled = health.dagEntitled,
                )
            }.distinctUntilChanged()

        suspend fun monitorAvailability() {
            availability.collect(::setLauncherEnabled)
        }

        private fun setLauncherEnabled(enabled: Boolean) {
            val component = ComponentName(context, DagActivity::class.java)
            val expectedState =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            if (context.packageManager.getComponentEnabledSetting(component) == expectedState) return
            context.packageManager.setComponentEnabledSetting(
                component,
                expectedState,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

internal fun dagLauncherShouldBeEnabled(
    hasActivation: Boolean,
    dagEnabled: Boolean,
    dagEntitled: Boolean = true,
): Boolean = hasActivation && dagEntitled && dagEnabled
