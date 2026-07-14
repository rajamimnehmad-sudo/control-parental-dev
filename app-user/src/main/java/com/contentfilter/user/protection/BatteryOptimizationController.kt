package com.contentfilter.user.protection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationController {
    fun isExempt(context: Context): Boolean =
        context
            .getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun shouldPrompt(context: Context): Boolean =
        BatteryOptimizationPromptPolicy.shouldPrompt(
            exempt = isExempt(context),
            promptAlreadyShown = preferences(context).getBoolean(KeyPromptShown, false),
        )

    fun markPromptShown(context: Context) {
        preferences(context).edit().putBoolean(KeyPromptShown, true).apply()
    }

    fun requestIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        return directRequest.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private const val PreferencesName = "battery_optimization_protection"
    private const val KeyPromptShown = "prompt_shown"
}

internal object BatteryOptimizationPromptPolicy {
    fun shouldPrompt(
        exempt: Boolean,
        promptAlreadyShown: Boolean,
    ): Boolean = !exempt && !promptAlreadyShown
}
