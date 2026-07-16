package com.contentfilter.user.protection

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLauncherController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun monitorVisibility() {
            setLauncherEnabled(userLauncherShouldBeEnabled())
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

        private companion object {
            const val UserLauncherAliasClassName = "com.contentfilter.user.UserLauncherAlias"
        }
    }

internal fun userLauncherShouldBeEnabled(): Boolean = true
