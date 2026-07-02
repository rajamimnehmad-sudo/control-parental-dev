package com.contentfilter.feature.accessibility.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityController {
    fun isEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityEnabled) return false

        val expectedComponent = ComponentName(
            context.packageName,
            ProtectorAccessibilityService::class.java.name,
        ).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
