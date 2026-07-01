package com.contentfilter.feature.vpn.service

import android.content.Context

object DevProtectionMode {
    private const val PreferencesName = "dev_protection_mode"
    private const val ProtectionDisabledKey = "protection_disabled"

    fun isAvailable(context: Context): Boolean = context.packageName.endsWith(".dev")

    fun isProtectionDisabled(context: Context): Boolean =
        isAvailable(context) &&
            context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .getBoolean(ProtectionDisabledKey, false)

    fun setProtectionDisabled(
        context: Context,
        disabled: Boolean,
    ) {
        if (!isAvailable(context)) return
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ProtectionDisabledKey, disabled)
            .apply()
    }
}
