package com.contentfilter.core.security

import android.content.Context
import com.contentfilter.core.network.config.DeviceTokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SharedPreferencesDeviceTokenProvider
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DeviceTokenProvider {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        override fun currentDeviceToken(): String? =
            preferences.getString(DeviceTokenKey, null)?.takeIf { it.isNotBlank() }

        override fun saveDeviceToken(token: String) {
            preferences.edit()
                .putString(DeviceTokenKey, token)
                .apply()
        }

        private companion object {
            const val PreferencesName = "device-token"
            const val DeviceTokenKey = "device-token"
        }
    }
