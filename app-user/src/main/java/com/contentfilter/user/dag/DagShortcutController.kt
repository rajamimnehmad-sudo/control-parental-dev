package com.contentfilter.user.dag

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.contentfilter.user.MainActivity
import com.contentfilter.user.R

object DagShortcutController {
    const val OpenDagAction = "com.contentfilter.user.action.OPEN_DAG"

    fun requestPin(context: Context): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        if (!shortcutManager.isRequestPinShortcutSupported) return false
        val shortcut =
            ShortcutInfo.Builder(context, ShortcutId)
                .setShortLabel("DAG")
                .setLongLabel("Buscador protegido DAG")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_user_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(OpenDagAction)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ).build()
        return shortcutManager.requestPinShortcut(shortcut, null)
    }

    private const val ShortcutId = "dag-search"
}
