package com.contentfilter.user.chromedataplane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl

class ChromeVisualShieldLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        val result =
            when (intent.action) {
                ActionStart -> ChromeVisualShieldLabControl.start()
                ActionStop -> ChromeVisualShieldLabControl.stop()
                ActionRelease -> ChromeVisualShieldLabControl.release()
                ActionStatus -> ChromeVisualShieldLabControl.status()
                ActionInjectStale -> ChromeVisualShieldLabControl.injectStale()
                ActionCancelStress -> ChromeVisualShieldLabControl.cancelStress()
                else -> "result=unknown_action"
            }
        setResultData(result)
        Log.i(LogTag, "action=${intent.action?.substringAfterLast('.')} $result")
    }

    companion object {
        const val ActionStart = "com.contentfilter.user.chromevisualshield.command.START"
        const val ActionStop = "com.contentfilter.user.chromevisualshield.command.STOP"
        const val ActionRelease = "com.contentfilter.user.chromevisualshield.command.RELEASE"
        const val ActionStatus = "com.contentfilter.user.chromevisualshield.command.STATUS"
        const val ActionInjectStale =
            "com.contentfilter.user.chromevisualshield.command.INJECT_STALE"
        const val ActionCancelStress =
            "com.contentfilter.user.chromevisualshield.command.CANCEL_STRESS"
        private const val LogTag = "GloshVisualShieldLab"
    }
}
