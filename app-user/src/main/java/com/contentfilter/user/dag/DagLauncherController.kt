package com.contentfilter.user.dag

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val preferences =
            context.getSharedPreferences(LauncherPreferencesName, Context.MODE_PRIVATE)
        private val mutableKeepSeparateLauncher =
            MutableStateFlow(preferences.getBoolean(KeepSeparateLauncherKey, true))

        val keepSeparateLauncher = mutableKeepSeparateLauncher.asStateFlow()

        private val availability =
            combine(
                activationRepository.observeActivation(),
                policyRepository.observeActivePolicy(),
                systemStatusRepository.observeHealth(),
                keepSeparateLauncher,
            ) { activation, policy, health, keepSeparateLauncher ->
                dagLauncherShouldBeEnabled(
                    hasActivation = activation != null,
                    dagEnabled = policy.rules.dagEnabled(),
                    dagEntitled = health.dagEntitled,
                    keepSeparateLauncher = keepSeparateLauncher,
                )
            }.distinctUntilChanged()

        suspend fun monitorAvailability() {
            availability.collect(::setLauncherEnabled)
        }

        fun setKeepSeparateLauncher(enabled: Boolean) {
            if (mutableKeepSeparateLauncher.value == enabled) return
            preferences.edit().putBoolean(KeepSeparateLauncherKey, enabled).apply()
            mutableKeepSeparateLauncher.value = enabled
        }

        private fun setLauncherEnabled(enabled: Boolean) {
            ensureInternalDagEnabled()
            val component = ComponentName(context.packageName, DagLauncherAliasClassName)
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

        private fun ensureInternalDagEnabled() {
            val component = ComponentName(context, DagActivity::class.java)
            if (
                context.packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                return
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        private companion object {
            const val LauncherPreferencesName = "dag_launcher_preferences"
            const val KeepSeparateLauncherKey = "keep_separate_launcher"
            const val DagLauncherAliasClassName = "com.contentfilter.user.dag.DagLauncherAlias"
        }
    }

internal fun dagLauncherShouldBeEnabled(
    hasActivation: Boolean,
    dagEnabled: Boolean,
    dagEntitled: Boolean = true,
    keepSeparateLauncher: Boolean = true,
): Boolean = hasActivation && dagEntitled && dagEnabled && keepSeparateLauncher
