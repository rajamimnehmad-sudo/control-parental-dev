package com.contentfilter.dagbrowser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

internal object DagGeckoRuntime {
    @Volatile
    private var instance: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        instance ?: synchronized(this) {
            instance ?: GeckoRuntime.create(context.applicationContext).also { instance = it }
        }
}
