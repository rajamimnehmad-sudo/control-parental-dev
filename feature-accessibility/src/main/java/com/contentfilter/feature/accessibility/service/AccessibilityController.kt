package com.contentfilter.feature.accessibility.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityController {
    private const val AccessibilityDetailsAction = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    fun isEnabled(context: Context): Boolean {
        val accessibilityEnabled =
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
        if (!accessibilityEnabled) return false

        val expectedComponent =
            ComponentName(
                context.packageName,
                ProtectorAccessibilityService::class.java.name,
            ).flattenToString()
        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()

        return enabledServices
            .split(':')
            .any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun openSettings(context: Context) {
        val serviceComponent =
            ComponentName(
                context.packageName,
                ProtectorAccessibilityService::class.java.name,
            )
        val detailsIntent =
            Intent(AccessibilityDetailsAction)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent)

        if (detailsIntent.resolveActivity(context.packageManager) == null) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            context.startActivity(detailsIntent)
        } catch (_: SecurityException) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
