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

        override fun isDeviceRelinkPending(): Boolean = preferences.getBoolean(DeviceRelinkPendingKey, false)

        override fun markDeviceRelinkPending() {
            preferences.edit().putBoolean(DeviceRelinkPendingKey, true).apply()
        }

        override fun clearDeviceRelinkPending() {
            preferences.edit().remove(DeviceRelinkPendingKey).apply()
        }

        override fun clearDeviceToken() {
            preferences.edit()
                .remove(DeviceTokenKey)
                .remove(DeviceRelinkPendingKey)
                .apply()
        }

        private companion object {
            const val PreferencesName = "device-token"
            const val DeviceTokenKey = "device-token"
            const val DeviceRelinkPendingKey = "device-relink-pending"
        }
    }
