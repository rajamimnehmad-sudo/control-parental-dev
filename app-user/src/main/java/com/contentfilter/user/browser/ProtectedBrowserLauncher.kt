package com.contentfilter.user.browser

import android.content.Context
import android.content.Intent

internal object ProtectedBrowserLauncher {
    fun open(context: Context): Boolean =
        runCatching {
            context.startActivity(
                Intent().setClassName(
                    BrowserPackageName,
                    BrowserActivityClassName,
                ),
            )
            true
        }.getOrDefault(false)

    const val BrowserPackageName = "com.contentfilter.dagbrowser.dev"
    const val BrowserActivityClassName = "com.contentfilter.dagbrowser.DagBrowserActivity"
}
