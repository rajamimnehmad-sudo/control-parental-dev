package com.contentfilter.feature.accessibility.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object DeviceAdminController {
    fun component(context: Context): ComponentName = ComponentName(context, ProtectionDeviceAdminReceiver::class.java)

    fun isEnabled(context: Context): Boolean =
        context
            .getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(component(context))

    fun activationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Esta barrera agrega una confirmación de Android antes de desinstalar Content Filter.",
            )
        }
}
