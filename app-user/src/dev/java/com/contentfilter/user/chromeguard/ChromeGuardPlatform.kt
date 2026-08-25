package com.contentfilter.user.chromeguard

import android.app.Application
import android.content.Context
import android.os.Process
import android.provider.Settings

internal object ChromeGuardProcess {
    fun isGuardProcess(): Boolean = Application.getProcessName().endsWith(ChromeGuardContract.GuardProcessSuffix)
}

internal object ChromeGuardBootMarker {
    fun current(context: Context): Long =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).toLong()
}

internal object ChromeGuardCallerVerifier {
    fun isSameApplicationUid(
        context: Context,
        callerUid: Int,
    ): Boolean {
        if (callerUid != Process.myUid()) return false
        return context.packageManager.getPackagesForUid(callerUid)?.contains(context.packageName) == true
    }
}
